package com.github.mczjuops.mczjugamecore.achievement;

/** 只读进度快照；unlockedAt 为首次解锁的 Unix 毫秒时间，0 表示未解锁。 */
public record AchievementProgress(long progress, long unlockedAt) {
    /** @return 是否已经永久解锁 */
    public boolean unlocked() { return unlockedAt > 0; }
}
