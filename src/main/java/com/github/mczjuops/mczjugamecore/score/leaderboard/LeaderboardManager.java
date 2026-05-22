package com.github.mczjuops.mczjugamecore.score.leaderboard;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.score.leaderboard.textdisplay.JsonTextDisplayLoader;
import com.github.mczjuops.mczjugamecore.score.leaderboard.textdisplay.TextDisplayProperties;
import com.github.mczjuops.mczjugamecore.score.leaderboard.textdisplay.TextDisplayRecord;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 负责排行榜的管理（数据整理 + 展示实体管理）
 */
public class LeaderboardManager {

    private final ConsoleSender logger = new ConsoleSender(getClass().getSimpleName());

    private final Map<String, AbstractLeaderboard> leaderboards = new LinkedHashMap<>(); // 所有注册的排行榜
    private final Map<String, List<TextDisplayRecord>> displayMap = new HashMap<>(); // 排行榜 ID 对应的展示实体 ID

    private BukkitTask autoRefreshTask;

    /** 注册排行榜时调用，从文件加载对应的展示实体 */
    private void loadTextDisplays(String leaderboardId) {
        new JsonTextDisplayLoader().loadAllTextDisplay(leaderboardId);
    }

    /**
     * 注册一个排行榜
     *
     * @param leaderboardId 排行榜唯一 ID，不能重复
     * @param leaderboard AbstractLeaderboard 子类实例
     */
    public void registerLeaderboard(String leaderboardId, AbstractLeaderboard leaderboard) {
        if (leaderboards.containsKey(leaderboardId)) {
            throw new IllegalStateException("Duplicate leaderboard ID: " + leaderboardId);
        }
        leaderboards.put(leaderboardId, leaderboard);
        loadTextDisplays(leaderboardId); // 从文件加载该排行榜的所有展示实体（若有）
    }

    /** 新建或加载时，为一个排行榜创建一个展示实体档案（不生成实体） */
    public void createTextDisplay(String leaderboardId, TextDisplayRecord record) {
        if (!displayMap.containsKey(leaderboardId)) displayMap.put(leaderboardId, new LinkedList<>());
        displayMap.get(leaderboardId).add(record);
    }

    /**
     * 立即刷新指定 ID 的排行榜（所有实体）
     *
     * @param leaderboardId 排行榜注册时的唯一 ID
     */
    public void refresh(String leaderboardId) {
        var leaderboard = leaderboards.get(leaderboardId);
        if (leaderboard == null) return;

        Component content = buildContent(leaderboard);
        displayMap.get(leaderboardId).forEach(record -> {
            TextDisplay entity = findOrRespawnEntity(record);
            String entityId = record.getEntityId();
            if (entity == null) {
                logger.warn("未设置位置，无法生成排行榜（排行榜 ID：%s，实体 ID：%s）".formatted(leaderboardId, entityId));
            } else {
                entity.text(content);
                record.getProperties().applyTo(entity);
            }
        });
    }

    /** 自动刷新所有排行榜 */
    public void startAutoRefresh(long intervalTicks) {
        if (autoRefreshTask != null) {
            autoRefreshTask.cancel();
        }

        autoRefreshTask = Bukkit.getScheduler().runTaskTimer(
                MCZJUGameCore.getInstance(),
                () -> leaderboards.forEach((leaderboardId, leaderboard) -> {
                    if (leaderboard.autoRefresh()) refresh(leaderboardId);
                }),
                60 * 20, // 开服 1 分钟时先刷新一次
                intervalTicks
        );
    }

    public void stopAutoRefresh() {
        if (autoRefreshTask != null) {
            autoRefreshTask.cancel();
            autoRefreshTask = null;
        }
    }

    /** 删除一个展示实体（移除对应实体） */
    public boolean removeTextDisplay(String leaderboardId, String entityId) {
        return displayMap.get(leaderboardId).removeIf(record -> {
            if (record.getEntityId().equals(entityId)) {
                record.deleteData();
                Location location = record.getLocation();
                UUID uuid = record.getUniqueId();
                if (location != null && uuid != null) {
                    var entity = findEntity(location, record.getUniqueId());
                    if (entity != null && entity.isValid()) entity.remove();
                }
                return true;
            }
            return false;
        });
    }

    /** 异步保存指定展示实体的数据 */
    public void saveTextDisplayAsync(String leaderboardId, String entityId) {
        Bukkit.getScheduler().runTaskAsynchronously(
                MCZJUGameCore.getInstance(),
                () -> saveTextDisplay(leaderboardId, entityId)
        );
    }

    public void shutdown() {
        stopAutoRefresh();
        saveAllTextDisplaysSync();
    }

    private void saveAllTextDisplaysSync() {
        displayMap.forEach((leaderboardId, records)
                -> records.forEach(record -> saveTextDisplay(leaderboardId, record.getEntityId())
        ));
    }

    public void saveTextDisplay(String leaderboardId, String entityId) {
        var record = getDisplayRecord(leaderboardId, entityId);
        if (record != null && record.isModified()) record.save();
    }

    /** 获取所有已注册的排行榜的 ID */
    public Set<String> getAllLeaderboardIds() {
        return Collections.unmodifiableSet(leaderboards.keySet());
    }

    public @Nullable TextDisplayRecord getDisplayRecord(String leaderboardId, String entityId) {
        List<TextDisplayRecord> records = displayMap.get(leaderboardId);
        if (records == null) return null;
        for (TextDisplayRecord record : records) {
            if (record.getEntityId().equals(entityId)) {
                return record;
            }
        }
        return null;
    }

    /** 获取指定排行榜的所有展示实体的 ID */
    public Set<String> getAllDisplayIds(String leaderboardId) {
        Set<String> displayIds = new HashSet<>();
        List<TextDisplayRecord> records = displayMap.get(leaderboardId);
        if (records != null) {
            for (TextDisplayRecord record : records) {
                displayIds.add(record.getEntityId());
            }
        }
        return Collections.unmodifiableSet(displayIds);
    }

    /**
     * 查找或生成对应展示实体
     * 先按 UUID 查找；找不到时（被人删了/没存档）则重新生成
     * 如果所在区块未加载，先强制加载区块
     *
     * @return 找到或生成的实体。若为 null，说明未正确设置
     */
    private @Nullable TextDisplay findOrRespawnEntity(TextDisplayRecord record) {
        Location location = record.getLocation();
        if (location == null) return null;

        UUID entityUniqueId = record.getUniqueId();
        var props = record.getProperties();

        // 还未生成实体，生成并记录
        if (entityUniqueId == null) {
            TextDisplay spawned = spawnEntity(location, props);
            record.setUniqueId(spawned.getUniqueId());
            record.save();
            return spawned;
        }

        TextDisplay entity = findEntity(location, entityUniqueId);

        // 区块内未找到，视为被误杀
        if (entity == null) {
            TextDisplay spawned = spawnEntity(location, props);
            record.setUniqueId(spawned.getUniqueId());
            record.save();
            return spawned;
        }

        // 找到了
        return entity;
    }

    /** 尝试按 UUID 查找实体（会强制加载区块），找不到返回 null */
    private @Nullable TextDisplay findEntity(@NotNull Location location, UUID entityUniqueId) {
        // 强制加载区块
        World world = location.getWorld();
        world.getChunkAt(location).load();

        Entity entity = world.getEntity(entityUniqueId);
        if (entity instanceof TextDisplay td && !td.isDead()) return td;
        return null;
    }

    /** 生成一个展示实体 */
    private TextDisplay spawnEntity(@NotNull Location location, @NotNull TextDisplayProperties properties) {
        return location.getWorld().spawn(location, TextDisplay.class, spawned -> {
            properties.applyTo(spawned);
            spawned.setPersistent(true);
            spawned.setBrightness(new Display.Brightness(15, 0));
        });
    }

    /** 根据注册的排行榜子类，生成展示实体的文本 */
    private Component buildContent(AbstractLeaderboard leaderboard) {
        List<LeaderboardEntry> entries = leaderboard.fetchEntries();

        // 排序
        Comparator<LeaderboardEntry> comparator = leaderboard.getSortOrder() == SortOrder.ASCENDING
                ? Comparator.comparingDouble(LeaderboardEntry::value)
                : Comparator.comparingDouble(LeaderboardEntry::value).reversed();
        entries.sort(comparator);

        // 截取
        int limit = leaderboard.getDisplayCount();
        Component result = TextParser.parse(leaderboard.getTitle()).append(Component.newline()).append(TextParser.parse(leaderboard.getSubtitle()));

        if (entries.isEmpty()) {
            result = result.append(Component.newline()).append(TextParser.parse(leaderboard.renderEmpty()));
        } else {
            int size = entries.size();
            for (int i = 0; i < limit; i++) {
                if (i < size) {
                    LeaderboardEntry entry = entries.get(i);
                    Component line = TextParser.parse(leaderboard.renderLine(i + 1, entry.playerName(), entry.value()));
                    result = result.append(Component.newline()).append(line);
                } else {
                    result = result.append(Component.newline()); // 人数不足，仍补满
                }
            }
        }
        return result;
    }
}
