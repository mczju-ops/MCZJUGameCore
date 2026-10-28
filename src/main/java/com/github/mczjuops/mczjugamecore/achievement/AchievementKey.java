package com.github.mczjuops.mczjugamecore.achievement;

/** 成就稳定标识，游戏 ID 保持原值；成就 ID 使用小写字母、数字、下划线或连字符。 */
public record AchievementKey(String gameId, String achievementId) {
    /** @throws IllegalArgumentException 标识为空或成就 ID 不合法 */
    public AchievementKey {
        if (gameId == null || gameId.isBlank() || achievementId == null
                || !achievementId.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid achievement key");
        }
    }
}
