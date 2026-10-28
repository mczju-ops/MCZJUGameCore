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

    /**
     * 清除玩家的一次成绩，允许之后取得新成绩重新上榜。
     * 默认不支持；自定义数据源应重写此方法，并在保存成功后返回 SUCCESS。
     *
     * @param playerName 玩家的原始名称，不包含显示格式
     * @return 清除结果；找不到已有成绩时不应创建新数据
     */
    public ClearRecordResult clearPlayerRecord(String playerName) {
        return ClearRecordResult.UNSUPPORTED;
    }

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
