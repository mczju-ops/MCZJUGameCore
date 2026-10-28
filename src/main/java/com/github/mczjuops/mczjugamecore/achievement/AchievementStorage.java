package com.github.mczjuops.mczjugamecore.achievement;

import com.github.mczjuops.mczjugamecore.player.data.PlayerDataManager;
import com.github.mczjuops.mczjugamecore.statistics.GameStatistic;
import org.bukkit.Bukkit;
import java.util.*;
import java.util.stream.Collectors;

/** PlayerDataManager 的薄访问层；不拥有文件、保存任务或 IO 线程。 */
public final class AchievementStorage {
    private static final String DATA_ID = "mgc_achievements";
    private final PlayerDataManager manager;
    private final Map<UUID, AchievementPlayerData> players = new HashMap<>();
    private final Set<UUID> logins = new HashSet<>();

    /** 在核心保留命名空间注册数据类，避免覆盖子插件自己的游戏数据。 */
    public AchievementStorage(PlayerDataManager manager) {
        this.manager = manager;
        manager.registerPlayerData(DATA_ID, AchievementPlayerData.class);
        for (var data : manager.getAllPlayerData(AchievementPlayerData.class)) {
            UUID id = UUID.fromString(data.getPlayerID());
            players.put(id, data);
            if (data.loggedIn) logins.add(id);
        }
    }
    /** 统一检查管理器 API 只能在服务器线程调用。 */
    public static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Achievement/statistics API requires server thread");
    }
    private AchievementPlayerData data(UUID id) {
        requireMainThread();
        return players.computeIfAbsent(Objects.requireNonNull(id),
                uuid -> manager.getPlayerData(uuid.toString(), AchievementPlayerData.class));
    }
    /** 记录真实登录并标记玩家数据待保存，重复登录不增加分母。 */
    public void recordLogin(UUID id) {
        requireMainThread();
        if (logins.add(id)) {
            var data = data(id);
            data.loggedIn = true;
            data.setModified(true);
        }
    }
    /**
     * 解锁统一小游戏进度树的入口成就；必须在真实登录记录后调用，重复调用保留首次时间。
     * @param id 已登录本服的玩家 UUID
     * @return 本次是否首次解锁
     * @throws IllegalArgumentException 玩家没有真实登录记录时抛出
     */
    public boolean recordMinigamesEntry(UUID id) {
        requireMainThread();
        if (!hasLoggedIn(id)) throw new IllegalArgumentException("Player has not logged in");
        var data = data(id);
        if (data.minigamesEnteredAt > 0) return false;
        data.minigamesEnteredAt = System.currentTimeMillis();
        data.setModified(true);
        return true;
    }
    /**
     * 查询统一入口成就的首次解锁时间；查询不会创建玩家记录。
     * @param id 玩家 UUID
     * @return Unix 毫秒时间；未解锁或无记录返回 0
     */
    public long getMinigamesEnteredAt(UUID id) {
        requireMainThread();
        var data = players.get(Objects.requireNonNull(id));
        return data == null ? 0 : data.minigamesEnteredAt;
    }
    /** @return 本服历史登录去重人数 */
    public long loggedInCount() { requireMainThread(); return logins.size(); }
    /** @return 玩家是否真实登录过本服 */
    public boolean hasLoggedIn(UUID id) { requireMainThread(); return logins.contains(id); }
    /** @return 有指定游戏记录的玩家快照，包含离线玩家 */
    public Set<UUID> players(String gameId) {
        requireMainThread();
        return players.entrySet().stream().filter(e -> e.getValue().games.containsKey(gameId))
                .map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }
    private AchievementPlayerData.GameData find(UUID id, String gameId) {
        requireMainThread();
        var data = players.get(Objects.requireNonNull(id));
        return data == null ? null : data.games.get(gameId);
    }
    /** @return 当前统计，无记录为 0；查询不创建数据 */
    public long statistic(UUID id, String gameId, GameStatistic statistic) {
        var game = find(id, gameId);
        return game == null ? 0 : game.statistics.getOrDefault(statistic, 0L);
    }
    /** 内部修改统计并标记待保存。 */
    public void statistic(UUID id, String gameId, GameStatistic statistic, long value) {
        var data = data(id);
        data.games.computeIfAbsent(gameId, k -> new AchievementPlayerData.GameData()).statistics.put(statistic, value);
        data.setModified(true);
    }
    /** @return 已保存的成就只读快照，无记录返回零值 */
    public AchievementProgress progress(UUID id, AchievementKey key) {
        var game = find(id, key.gameId());
        var value = game == null ? null : game.achievements.get(key.achievementId());
        return value == null ? new AchievementProgress(0, 0) : new AchievementProgress(value.progress, value.unlockedAt);
    }
    /** 内部修改成就并标记待保存。 */
    public void progress(UUID id, AchievementKey key, AchievementProgress value) {
        var data = data(id);
        var progress = new AchievementPlayerData.ProgressData();
        progress.progress = value.progress();
        progress.unlockedAt = value.unlockedAt();
        data.games.computeIfAbsent(key.gameId(), k -> new AchievementPlayerData.GameData())
                .achievements.put(key.achievementId(), progress);
        data.setModified(true);
    }
}
