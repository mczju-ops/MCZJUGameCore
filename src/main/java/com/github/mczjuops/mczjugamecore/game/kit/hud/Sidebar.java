package com.github.mczjuops.mczjugamecore.game.kit.hud;

import com.github.mczjuops.mczjugamecore.utils.TextParser;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 右侧计分板。
 *
 * <p>整局共用一个 {@link Scoreboard}，所有方法都必须在**主线程**调用。
 * 行文本以 {@code §} 颜色码作为 score entry（客户端只显示内容、不显示这些码），
 * 并用 {@link NumberFormat#blank()} 去掉右侧的红色分数。
 */
public final class Sidebar {

    /** 最多显示的行数（计分板侧边栏上限 15）。 */
    public static final int MAX_LINES = 15;

    private final Scoreboard board;
    private final Objective objective;
    /** 上一次渲染出来的 entry，用于清理已经不存在 / 改变的行。 */
    private final List<String> shown = new ArrayList<>();

    /**
     * @param title 侧边栏标题（MiniMessage）
     * @param key   计分板内部标识，同一局内唯一即可
     */
    public Sidebar(String key, String title) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            throw new IllegalStateException("ScoreboardManager 不可用（服务器正在关闭）");
        }
        this.board = manager.getNewScoreboard();
        this.objective = board.registerNewObjective(key, Criteria.DUMMY, TextParser.parse(title));
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    /** 让某位玩家看到侧边栏。 */
    public void attach(Player player) {
        if (player != null && player.isOnline()) {
            player.setScoreboard(board);
        }
    }

    /** 恢复玩家的主计分板。 */
    public void detach(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null || player == null || !player.isOnline()) {
            return;
        }
        player.setScoreboard(manager.getMainScoreboard());
    }

    /** 自上而下刷新内容。 */
    public void update(List<Component> lines) {
        List<String> wanted = new ArrayList<>();
        int count = Math.min(lines.size(), MAX_LINES);
        for (int i = 0; i < count; i++) {
            wanted.add(LegacyComponentSerializer.legacySection().serialize(lines.get(i)));
        }

        for (String old : shown) {
            if (!wanted.contains(old)) {
                board.resetScores(old);
            }
        }
        for (int i = 0; i < wanted.size(); i++) {
            var score = objective.getScore(wanted.get(i));
            // 分数越大越靠上
            score.setScore(wanted.size() - i);
            score.numberFormat(NumberFormat.blank());
        }

        shown.clear();
        shown.addAll(wanted);
    }

    /** 一条分隔线（纯 ASCII，避免字体缺字）。 */
    public static Component divider() {
        return TextParser.parse("<dark_gray>" + "-".repeat(16));
    }
}
