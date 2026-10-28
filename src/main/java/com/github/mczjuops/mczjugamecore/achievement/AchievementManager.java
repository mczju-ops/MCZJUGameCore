package com.github.mczjuops.mczjugamecore.achievement;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.event.PlayerAchievementUnlockedEvent;
import com.github.mczjuops.mczjugamecore.statistics.GameStatistic;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import java.util.*;

/** 成就注册及更新入口。所有方法仅限主线程；定义按注册顺序展示，数据按稳定 ID 保留。 */
public final class AchievementManager {
    private final AchievementStorage storage;
    private final Map<AchievementKey, AchievementDefinition> definitions = new LinkedHashMap<>();
    private final Map<AchievementKey, Long> unlockedCounts = new HashMap<>();
    private final Map<AchievementKey, Plugin> owners = new HashMap<>();
    private final NativeAchievementDisplay nativeDisplay;

    /** 核心创建成就服务及原生展示适配器。 */
    public AchievementManager(AchievementStorage storage) {
        this.storage = storage;
        nativeDisplay = new NativeAchievementDisplay(this);
    }
    /**
     * 原子校验并注册定义，补发已有统计达标的成就（不发送聊天提示）。
     * 父节点必须已注册或在本批次内，必须同游戏同 owner，且不得成环；重复 ID 拒绝。
     * @param owner 定义所属子插件，停用时注销运行时定义，历史数据保留
     * @param additions 新成就定义，可任意父子顺序
     */
    public void register(Plugin owner, Collection<AchievementDefinition> additions) {
        AchievementStorage.requireMainThread();
        Objects.requireNonNull(owner);
        if (!owner.isEnabled()) throw new IllegalArgumentException("Owner is disabled");
        List<AchievementDefinition> batch = List.copyOf(additions);
        var combined = new LinkedHashMap<>(definitions);
        for (var definition : batch) {
            if (!MCZJUGameCore.getGameManager().getRegisteredGameIds().contains(definition.key().gameId()))
                throw new IllegalArgumentException("Register game before achievements: " + definition.key().gameId());
            if (combined.putIfAbsent(definition.key(), definition) != null)
                throw new IllegalArgumentException("Duplicate achievement: " + definition.key());
        }
        for (var definition : batch) {
            Set<AchievementKey> visited = new HashSet<>();
            for (var cursor = definition; cursor != null;) {
                if (!visited.add(cursor.key())) throw new IllegalArgumentException("Cyclic achievement parents");
                var parent = cursor.parent();
                if (parent == null) break;
                if (!combined.containsKey(parent)) throw new IllegalArgumentException("Missing parent: " + parent);
                if (owners.containsKey(parent) && owners.get(parent) != owner)
                    throw new IllegalArgumentException("Parent belongs to another plugin");
                cursor = combined.get(parent);
            }
        }
        for (var definition : batch) {
            definitions.put(definition.key(), definition);
            owners.put(definition.key(), owner);
            unlockedCounts.put(definition.key(), storage.players(definition.key().gameId()).stream()
                    .filter(storage::hasLoggedIn).filter(id -> storage.progress(id, definition.key()).unlocked()).count());
        }
        for (var definition : batch) {
            if (definition.statistic() == null) continue;
            for (UUID id : storage.players(definition.key().gameId())) {
                if (storage.hasLoggedIn(id)) check(id, definition, true);
            }
        }
        nativeDisplay.scheduleRebuild();
    }
    /** 注销插件拥有的定义；不删除任何玩家统计或历史成就。 */
    public void unregister(Plugin owner) {
        AchievementStorage.requireMainThread();
        var keys = owners.entrySet().stream().filter(e -> e.getValue() == owner).map(Map.Entry::getKey).toList();
        keys.forEach(key -> { owners.remove(key); definitions.remove(key); unlockedCounts.remove(key); });
        nativeDisplay.scheduleRebuild();
    }
    /** @return 当前全部定义的不可变快照 */
    public List<AchievementDefinition> getDefinitions() {
        AchievementStorage.requireMainThread();
        return List.copyOf(definitions.values());
    }
    /** @return 当前游戏定义的不可变快照；未注册成就时为空 */
    public List<AchievementDefinition> getDefinitions(String gameId) {
        return getDefinitions().stream().filter(d -> d.key().gameId().equals(gameId)).toList();
    }
    private AchievementDefinition definition(AchievementKey key) {
        AchievementStorage.requireMainThread();
        var result = definitions.get(key);
        if (result == null) throw new IllegalArgumentException("Unregistered achievement: " + key);
        return result;
    }
    private void requirePlayer(UUID id) {
        if (!storage.hasLoggedIn(Objects.requireNonNull(id)))
            throw new IllegalArgumentException("Player has never logged into this server: " + id);
    }
    /** @return 只读进度；统计成就实时读取统计，已解锁记录不会因统计降低撤销 */
    public AchievementProgress getProgress(UUID playerId, AchievementKey key) {
        var definition = definition(key);
        var saved = storage.progress(playerId, key);
        long progress = definition.statistic() == null ? saved.progress()
                : storage.statistic(playerId, key.gameId(), definition.statistic());
        return new AchievementProgress(progress, saved.unlockedAt());
    }
    /** @return 本服已登录玩家中的解锁人数和全服登录人数，包含离线玩家 */
    public AchievementStats getStats(AchievementKey key) {
        definition(key);
        return new AchievementStats(unlockedCounts.getOrDefault(key, 0L), storage.loggedInCount());
    }
    /** 直接解锁手动成就；重复调用无副作用，绑定统计的成就拒绝此操作。 */
    public AchievementUpdateResult unlock(UUID playerId, AchievementKey key) {
        var definition = manual(key);
        return updateProgress(playerId, key, definition.target());
    }
    /** 增加手动成就进度，amount 必须非负；每次调用都会累加，不做业务事件去重。 */
    public AchievementUpdateResult increment(UUID playerId, AchievementKey key, long amount) {
        manual(key);
        if (amount < 0) throw new IllegalArgumentException("Negative increment");
        long old = getProgress(playerId, key).progress();
        long next = amount > Long.MAX_VALUE - old ? Long.MAX_VALUE : old + amount;
        return updateProgress(playerId, key, next);
    }
    /** 同步单调累计的手动进度，取 max(old,value)，达到目标自动解锁；value 不得为负。 */
    public AchievementUpdateResult updateProgress(UUID playerId, AchievementKey key, long value) {
        var definition = manual(key);
        requirePlayer(playerId);
        if (value < 0) throw new IllegalArgumentException("Negative progress");
        var old = getProgress(playerId, key);
        if (old.unlocked()) return new AchievementUpdateResult(false, false, old);
        return update(playerId, definition, Math.min(definition.target(), Math.max(old.progress(), value)), false);
    }
    private AchievementDefinition manual(AchievementKey key) {
        var definition = definition(key);
        if (definition.statistic() != null) throw new IllegalArgumentException("Modify the bound statistic instead");
        return definition;
    }
    private AchievementUpdateResult update(UUID id, AchievementDefinition definition, long progress, boolean historical) {
        var key = definition.key();
        var saved = storage.progress(id, key);
        boolean newlyUnlocked = !saved.unlocked() && progress >= definition.target();
        long unlockedAt = newlyUnlocked ? System.currentTimeMillis() : saved.unlockedAt();
        var next = new AchievementProgress(progress, unlockedAt);
        boolean changed = !saved.equals(next);
        if (changed) storage.progress(id, key, next);
        if (newlyUnlocked) {
            unlockedCounts.merge(key, 1L, Long::sum);
            var player = Bukkit.getPlayer(id);
            if (player != null) {
                if (!historical) {
                    Component name = TextParser.parseNonItalic(definition.name()).colorIfAbsent(NamedTextColor.GOLD);
                    Component description = TextParser.parseNonItalic(definition.description()).colorIfAbsent(NamedTextColor.GRAY);
                    player.sendMessage(Component.text("✦ ", NamedTextColor.GOLD)
                            .append(Component.text("成就达成 ", NamedTextColor.GREEN))
                            .append(Component.text("[", NamedTextColor.DARK_GRAY))
                            .append(name)
                            .append(Component.text("]", NamedTextColor.DARK_GRAY))
                            .hoverEvent(description));
                }
                nativeDisplay.sync(player);
            }
            Bukkit.getPluginManager().callEvent(new PlayerAchievementUnlockedEvent(id, key, historical));
        }
        return new AchievementUpdateResult(changed, newlyUnlocked, next);
    }
    private void check(UUID id, AchievementDefinition definition, boolean historical) {
        if (storage.progress(id, definition.key()).unlocked()) return;
        long value = storage.statistic(id, definition.key().gameId(), definition.statistic());
        // 绑定统计无需复制未解锁进度，唯一数据源是统计存储。
        if (value >= definition.target()) update(id, definition, value, historical);
    }
    /** 核心统计更新后调用，只检查绑定该项的成就。 */
    public void checkStatistic(UUID id, String gameId, GameStatistic statistic) {
        AchievementStorage.requireMainThread();
        if (!storage.hasLoggedIn(id)) return;
        for (var definition : getDefinitions(gameId)) {
            if (definition.statistic() == statistic) check(id, definition, false);
        }
    }
    /** @return 核心原生成就展示适配器（不作为业务数据源） */
    public NativeAchievementDisplay getNativeDisplay() { return nativeDisplay; }
}
