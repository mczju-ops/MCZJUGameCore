package com.github.mczjuops.mczjugamecore.game.kit.team;

import com.github.mczjuops.mczjugamecore.utils.TextParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

/**
 * 一支队伍的静态定义（不可变），由游戏自己声明。
 *
 * <p>队伍的数量、名字、颜色都是游戏规则的一部分，所以 kit 不做"红蓝两队"这类假设：
 * 你需要几支队、叫什么名字，就在这里声明几支，然后交给 {@link TeamSession} 手动分配。
 *
 * <p>{@code displayName} 使用 MiniMessage 语法，同时被用作 Tab 前缀与语音组名；
 * {@code color} 用于需要纯色文本的场合（侧边栏、聊天播报等）。
 *
 * <pre>{@code
 * public static final GameTeam RED = new GameTeam("red", "<red>红队", NamedTextColor.RED, Material.RED_GLAZED_TERRACOTTA);
 * public static final GameTeam BLUE = new GameTeam("blue", "<blue>蓝队", NamedTextColor.BLUE, Material.BLUE_GLAZED_TERRACOTTA);
 * }</pre>
 *
 * @param id 局内唯一且稳定的队伍标识，不能为空白
 * @param displayName MiniMessage 显示名，不能为空白
 * @param color 纯色文本用的颜色，不能为 null
 * @param icon 菜单/侧边栏展示用的图标，不能为 null
 */
public record GameTeam(String id, String displayName, NamedTextColor color, Material icon) {

    public GameTeam {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("队伍 id 不能为空白");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("队伍显示名不能为空白");
        }
        if (color == null) throw new NullPointerException("color");
        if (icon == null) throw new NullPointerException("icon");
    }

    /**
     * Tab 前缀：显示名 + 一个空格（不自动插入其他格式）。
     * @return 解析后的 MiniMessage 前缀
     */
    public Component tabPrefix() {
        return TextParser.parse(displayName + " ");
    }

    /**
     * 用队伍颜色渲染一段纯文本。
     * @param text 纯文本（不会被当作 MiniMessage 解析）
     * @return 着色后的文本
     */
    public Component colored(String text) {
        return Component.text(text == null ? "" : text, color);
    }

    /** @return 去掉格式后的队伍名，用于语音组名、日志等纯文本场合 */
    public String plainName() {
        String plain = TextParser.plain(TextParser.parse(displayName)).trim();
        return plain.isEmpty() ? id : plain;
    }
}
