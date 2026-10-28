package com.github.mczjuops.mczjugamecore.achievement;

/** 全服统计，分母为登录过本小游戏服务器的去重 UUID 数，包含离线玩家。 */
public record AchievementStats(long unlockedPlayers, long loggedInPlayers) {
    /** @return 0 到 1 的完成比例；无人登录时返回 0 */
    public double completionRatio() { return loggedInPlayers == 0 ? 0 : (double) unlockedPlayers / loggedInPlayers; }
}
