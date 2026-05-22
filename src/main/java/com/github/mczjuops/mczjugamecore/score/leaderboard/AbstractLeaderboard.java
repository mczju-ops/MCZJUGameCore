package com.github.mczjuops.mczjugamecore.score.leaderboard;

import java.util.List;

public abstract class AbstractLeaderboard {

    public abstract String getTitle();
    public abstract String getSubtitle();

    /**
     * 返回本排行榜的所有原始条目（不必排序，MCZJUGameCore 会负责排序）
     * 子插件从自己的内存里拿数据
     */
    public abstract List<LeaderboardEntry> fetchEntries();

    /** 排序方向，默认降序，即分数越高越好 */
    public SortOrder getSortOrder() {
        return SortOrder.DESCENDING;
    }

    /** 排行榜最多展示几名，默认 12 */
    public int getDisplayCount() {
        return 12;
    }

    /**
     * 渲染每一个条目行
     * 默认格式：1. Steve - 100
     */
    public String renderLine(int rank, String playerName, double value) {
        return "<yellow>%d.</yellow> <green>%s</green> <gray>-</gray> <yellow>%.0f</yellow>"
                .formatted(rank, playerName, value);
    }

    /** 当排行榜没有数据时显示的内容 */
    public String renderEmpty() {
        return "<gray>暂无数据</gray>";
    }
}
