package com.github.mczjuops.mczjugamecore.achievement;

import com.github.mczjuops.mczjugamecore.player.data.JsonPlayerData;
import com.github.mczjuops.mczjugamecore.statistics.GameStatistic;
import java.util.concurrent.ConcurrentHashMap;

/** 核心统计/成就玩家数据，复用 PlayerDataManager 加载、脏标记和保存流程。 */
public final class AchievementPlayerData extends JsonPlayerData {
    /** 真实登录标记；仅查询或创建数据不能增加全服登录人数。 */
    public boolean loggedIn;
    /** 首次实际进入小游戏服的 Unix 毫秒时间；旧数据默认 0，历史玩家导入不会解锁入口成就。 */
    public long minigamesEnteredAt;
    /** 按游戏 ID 保存统计和成就；并发容器允许现有后台保存流程遍历。 */
    public ConcurrentHashMap<String, GameData> games = new ConcurrentHashMap<>();

    /** 单个游戏的数据。字段必须公开且非 final，符合现有 JSON 序列化规则。 */
    public static final class GameData {
        /** 通用统计值。 */
        public ConcurrentHashMap<GameStatistic, Long> statistics = new ConcurrentHashMap<>();
        /** 按成就 ID 保存的记录，更新时整体替换值。 */
        public ConcurrentHashMap<String, ProgressData> achievements = new ConcurrentHashMap<>();
    }
    /** 持久化 DTO；不直接序列化 record，因为现有序列化器排除 private/final 字段。 */
    public static final class ProgressData {
        /** 独立累计进度。 */
        public long progress;
        /** 首次解锁 Unix 毫秒时间，0 表示未解锁。 */
        public long unlockedAt;
    }
}
