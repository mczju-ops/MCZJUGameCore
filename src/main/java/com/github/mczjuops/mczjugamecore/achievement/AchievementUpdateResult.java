package com.github.mczjuops.mczjugamecore.achievement;

/** 修改结果：是否有变化、是否首次解锁、修改后的快照。 */
public record AchievementUpdateResult(boolean changed, boolean newlyUnlocked, AchievementProgress progress) {}
