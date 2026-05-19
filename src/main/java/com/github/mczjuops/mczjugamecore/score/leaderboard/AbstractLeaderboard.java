package com.github.mczjuops.mczjugamecore.score.leaderboard;

import com.github.mczjuops.mczjugamecore.utils.TextParser;
import net.kyori.adventure.text.Component;

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

    /** 排行榜是否自动定期刷新，默认否 */
    public boolean autoRefresh() {
        return false;
    }

    /**
     * 渲染每一个条目行
     * 默认格式：1. Steve - 100
     */
    public Component renderLine(int rank, String playerName, String displayValue) {
        return TextParser.parse("<yellow>%d.</yellow> <green>%s</green> <gray>-</gray> <yellow>%s<yellow>"
                .formatted(rank, playerName, displayValue));
    }

    /** 当排行榜没有数据时显示的内容 */
    public Component renderEmpty() {
        return TextParser.parse("<gray>暂无数据");
    }
}
