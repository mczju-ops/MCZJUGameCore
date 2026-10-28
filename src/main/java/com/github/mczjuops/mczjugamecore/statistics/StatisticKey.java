package com.github.mczjuops.mczjugamecore.statistics;

/**
 * 自定义统计的稳定标识，与成就标识采用相同的游戏内命名规则。
 * @param gameId 非空的游戏 ID，保持原值
 * @param statisticId 游戏内唯一的统计 ID，使用小写字母、数字、下划线或连字符
 */
public record StatisticKey(String gameId, String statisticId) {
    /** @throws IllegalArgumentException 标识为空或统计 ID 不合法 */
    public StatisticKey {
        if (gameId == null || gameId.isBlank() || statisticId == null
                || !statisticId.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid statistic key");
        }
    }
}
