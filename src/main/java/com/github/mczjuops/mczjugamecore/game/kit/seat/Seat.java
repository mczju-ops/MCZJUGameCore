package com.github.mczjuops.mczjugamecore.game.kit.seat;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 一个「座位」：真人玩家或人机。
 *
 * <p>kit 的所有交互（询问、计时、菜单、手牌）都只认这个接口，因此**人机必须是合法座位** ——
 * 这是能单机验证一局游戏的前提。人机的 {@link #ext()} 返回 {@code null}，
 * kit 内部所有需要 {@code Player} 的地方都必须先判空。
 *
 * <p>游戏的座位状态类（例如 SoulCard 的 {@code SoulPlayer}）直接实现本接口即可。
 */
public interface Seat {

    /** 显示名（真人用玩家名，人机用配置的名字）。 */
    String name();

    boolean isBot();

    boolean isOnline();

    /** 真人返回对应的 {@link PlayerExt}；人机返回 {@code null}。 */
    @Nullable PlayerExt ext();

    /** 真人返回 {@link Player}；人机或已离线返回 {@code null}。 */
    default @Nullable Player player() {
        PlayerExt ext = ext();
        return ext == null ? null : ext.player();
    }

    /** 是否还能被询问 / 收到提示（人机恒为 true，真人要求在线）。 */
    default boolean isActive() {
        return isBot() || isOnline();
    }
}
