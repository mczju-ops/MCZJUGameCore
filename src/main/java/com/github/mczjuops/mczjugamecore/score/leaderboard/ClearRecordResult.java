package com.github.mczjuops.mczjugamecore.score.leaderboard;

/** 清除玩家排行榜成绩的结果。 */
public enum ClearRecordResult {
    /** 成绩已清除并保存。 */
    SUCCESS,
    /** 排行榜 ID 未注册。 */
    LEADERBOARD_NOT_FOUND,
    /** 玩家没有数据或没有有效成绩。 */
    NO_RECORD,
    /** 数据源尚未实现清除功能。 */
    UNSUPPORTED,
    /** 数据修改或保存失败。 */
    FAILED
}
