package com.github.mczjuops.mczjugamecore.achievement;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.google.gson.Gson;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Paper 原生成就树投影。统一入口后按游戏分支展示，父子关系仅控制布局，权威记录仍在核心。
 * 使用兼容 Paper 26.2-62 的注册入口并移除自动生成的持久定义，重启/资源重载后重建；原生 UI 不提供实时数值和全服完成比例。
 */
@SuppressWarnings("deprecation")
public final class NativeAchievementDisplay {
    private static final Gson GSON = new Gson();
    // Multiverse 的 allow-advancement-grant=false 明确保留此条件，供程序授予自定义进度。
    private static final String CRITERION = "unlock_right_away";
    private static final NamespacedKey ENTRY = new NamespacedKey("mgc_achievements", "root");
    private final AchievementManager manager;
    private boolean scheduled;
    private final Set<NamespacedKey> roots = new HashSet<>();
    private final Set<NamespacedKey> branches = new HashSet<>();
    private final Map<NamespacedKey, AchievementKey> keys = new HashMap<>();
    private final Set<NamespacedKey> retired = new HashSet<>();
    private final Map<Key, String> loaded = new HashMap<>();
    /** 核心创建展示投影。 */
    public NativeAchievementDisplay(AchievementManager manager) { this.manager = manager; }
    private String gamePath(String game) {
        return HexFormat.of().formatHex(game.getBytes(StandardCharsets.UTF_8));
    }
    private NamespacedKey root(String game) { return new NamespacedKey("mgc_achievements", gamePath(game) + "/root"); }
    /** @return 成就对应的稳定原生 key；无损编码游戏 ID，避免不同插件碰撞 */
    public NamespacedKey key(AchievementKey key) {
        return new NamespacedKey("mgc_achievements", gamePath(key.gameId()) + "/achievements/" + key.achievementId());
    }
    /** 合并同 tick 的注册，下一 tick 统一加载原生成就。 */
    public void scheduleRebuild() {
        AchievementStorage.requireMainThread();
        if (scheduled || !MCZJUGameCore.getInstance().isEnabled()) return;
        scheduled = true;
        Bukkit.getScheduler().runTask(MCZJUGameCore.getInstance(), () -> {
            scheduled = false;
            rebuild();
        });
    }
    private String json(String title, String description, String icon, NamespacedKey parent, boolean showToast) {
        Map<String, Object> display = new LinkedHashMap<>();
        display.put("icon", Map.of("id", icon));
        display.put("title", JsonParser.parseString(GsonComponentSerializer.gson().serialize(
                TextParser.parseNonItalic(title).colorIfAbsent(NamedTextColor.GOLD))));
        display.put("description", JsonParser.parseString(GsonComponentSerializer.gson().serialize(
                TextParser.parseNonItalic(description).colorIfAbsent(NamedTextColor.GRAY))));
        display.put("frame", "task");
        display.put("show_toast", showToast); // 入口与具体成就弹窗，游戏分组不弹窗。
        display.put("announce_to_chat", false);
        display.put("hidden", false);
        if (parent == null) display.put("background", "minecraft:gui/advancements/backgrounds/stone");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("display", display);
        result.put("criteria", Map.of(CRITERION, Map.of("trigger", "minecraft:impossible")));
        result.put("requirements", List.of(List.of(CRITERION)));
        if (parent != null) result.put("parent", parent.toString());
        return GSON.toJson(result);
    }
    /** 资源重载后调用；按当前全部定义重新注册并同步在线玩家。 */
    public void rebuild() {
        AchievementStorage.requireMainThread();
        Map<Key, String> requested = new LinkedHashMap<>();
        retired.addAll(roots);
        retired.addAll(branches);
        retired.addAll(keys.keySet());
        roots.clear();
        branches.clear();
        keys.clear();
        roots.add(ENTRY);
        requested.put(ENTRY, json("进入小游戏世界", "啪啪啪啪啪", "minecraft:firework_rocket", null, true));
        for (var definition : manager.getDefinitions()) {
            String game = definition.key().gameId();
            NamespacedKey root = root(game);
            branches.add(root);
            var meta = MCZJUGameCore.getGameManager().getGameMetas().get(game);
            requested.putIfAbsent(root, json(meta.displayName(), String.join("\n", meta.description()),
                    meta.icon().getKey().toString(), ENTRY, false));
            NamespacedKey key = key(definition.key());
            keys.put(key, definition.key());
            requested.put(key, json(definition.name(), definition.description(), definition.icon().getKey().toString(),
                    definition.parent() == null ? root : key(definition.parent()), true));
        }
        retired.removeAll(roots);
        retired.removeAll(branches);
        retired.removeAll(keys.keySet());
        Map<Key, String> missing = new LinkedHashMap<>();
        requested.forEach((key, value) -> {
            NamespacedKey bukkitKey = NamespacedKey.fromString(key.asString());
            if (Bukkit.getAdvancement(bukkitKey) == null) missing.put(key, value);
            else if (loaded.containsKey(key) && !loaded.get(key).equals(value)) {
                MCZJUGameCore.getInstance().getLogger().warning("Native achievement definition changed; restart or reload data to refresh: " + key);
            }
        });
        try {
            if (!missing.isEmpty()) loadInParentOrder(missing);
            for (Key key : missing.keySet()) {
                if (Bukkit.getAdvancement(NamespacedKey.fromString(key.asString())) == null)
                    throw new IllegalStateException("Native achievement was not loaded: " + key);
            }
            loaded.putAll(missing);
            MCZJUGameCore.getInstance().getLogger().info("Native achievements ready: roots=" + roots.size() + ", achievements=" + keys.size());
            Bukkit.getOnlinePlayers().forEach(this::sync);
        } catch (Exception e) {
            MCZJUGameCore.getInstance().getLogger().severe("Native achievement registration failed; chest menu remains available: " + e);
        }
    }
    // 26.2-62 尚无 loadAdvancements(Map, boolean)。逐个按父节点顺序注册，
    // 再删除该 API 生成的持久定义（不移除运行时节点），避免重启残留旧定义。
    private void loadInParentOrder(Map<Key, String> missing) {
        var pending = new LinkedHashMap<>(missing);
        while (!pending.isEmpty()) {
            boolean progressed = false;
            var iterator = pending.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                var json = JsonParser.parseString(entry.getValue()).getAsJsonObject();
                if (json.has("parent") && Bukkit.getAdvancement(
                        NamespacedKey.fromString(json.get("parent").getAsString())) == null) continue;
                var key = NamespacedKey.fromString(entry.getKey().asString());
                try {
                    if (Bukkit.getUnsafe().loadAdvancement(key, entry.getValue()) == null)
                        throw new IllegalStateException("Cannot load native achievement: " + key);
                } finally {
                    Bukkit.getUnsafe().removeAdvancement(key);
                }
                iterator.remove();
                progressed = true;
            }
            if (!progressed) throw new IllegalStateException("Missing native achievement parent: " + pending.keySet());
        }
    }
    /** 将持久化的登录入口和核心成就投影到在线玩家；游戏分组只控制布局，不计入游戏成就数量。 */
    public void sync(Player player) {
        AchievementStorage.requireMainThread();
        for (var old : retired) project(player, old, false);
        boolean entered = MCZJUGameCore.getAchievementStorage().getMinigamesEnteredAt(player.getUniqueId()) > 0;
        for (var root : roots) project(player, root, entered);
        for (var branch : branches) project(player, branch, entered);
        keys.forEach((nativeKey, key) -> project(player, nativeKey, manager.getProgress(player.getUniqueId(), key).unlocked()));
    }
    private void project(Player player, NamespacedKey key, boolean unlocked) {
        var advancement = Bukkit.getAdvancement(key);
        if (advancement == null) {
            MCZJUGameCore.getInstance().getLogger().warning("Native achievement missing during sync: " + key);
            return;
        }
        var progress = player.getAdvancementProgress(advancement);
        if (unlocked && !progress.isDone()) {
            boolean awarded = progress.awardCriteria(CRITERION);
            MCZJUGameCore.getInstance().getLogger().info("Native achievement sync: player=" + player.getName()
                    + ", key=" + key + ", criteria=" + advancement.getCriteria()
                    + ", awarded=" + awarded + ", done=" + progress.isDone()
                    + ", remaining=" + progress.getRemainingCriteria() + ", awardedCriteria=" + progress.getAwardedCriteria());
        }
        else if (!unlocked && progress.isDone()) progress.revokeCriteria(CRITERION);
    }
    /**
     * 返回玩家原生投影的诊断摘要，不修改核心记录或原生进度。
     * @param player 当前在线玩家
     * @return 定义数量、已加载根节点及玩家已完成根节点数量
     */
    public String diagnose(Player player) {
        AchievementStorage.requireMainThread();
        long registered = roots.stream().map(Bukkit::getAdvancement).filter(Objects::nonNull).count();
        long completed = roots.stream().map(Bukkit::getAdvancement).filter(Objects::nonNull)
                .filter(a -> player.getAdvancementProgress(a).isDone()).count();
        String details = roots.stream().map(Bukkit::getAdvancement).filter(Objects::nonNull)
                .map(a -> a.getKey() + ":remaining=" + player.getAdvancementProgress(a).getRemainingCriteria()
                        + ",awarded=" + player.getAdvancementProgress(a).getAwardedCriteria())
                .toList().toString();
        return "details=" + details + ", player=" + player.getName() + ", definitions=" + manager.getDefinitions().size()
                + ", roots=" + roots.size() + ", registeredRoots=" + registered + ", completedRoots=" + completed;
    }
    /** 防止原生命令绕过核心记录授予投影成就；非本系统成就不受影响。 */
    public boolean mayGrant(Player player, NamespacedKey nativeKey) {
        if (!nativeKey.getNamespace().equals("mgc_achievements")) return true;
        if (roots.contains(nativeKey) || branches.contains(nativeKey))
            return MCZJUGameCore.getAchievementStorage().getMinigamesEnteredAt(player.getUniqueId()) > 0;
        var key = keys.get(nativeKey);
        return key != null && manager.getProgress(player.getUniqueId(), key).unlocked();
    }
}
