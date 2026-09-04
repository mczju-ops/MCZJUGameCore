package com.github.mczjuops.mczjugamecore.event.player;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 玩家通过原版旁观模式菜单准备传送时触发。
 * <p>
 * 目标玩家根据原生传送目的地估算，可能为空，也不保证绝对准确。
 * 取消本事件会阻止对应的原生传送。
 */
public final class PlayerSpectatorTeleportEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Location from;
    private final Location to;
    private final Player estimatedTarget;
    private final double estimatedTargetDistanceSquared;
    private boolean cancelled;

    public PlayerSpectatorTeleportEvent(
            @NotNull Player player,
            @NotNull Location from,
            @NotNull Location to,
            @Nullable Player estimatedTarget,
            double estimatedTargetDistanceSquared
    ) {
        super(player);
        this.from = from.clone();
        this.to = to.clone();
        this.estimatedTarget = estimatedTarget;
        this.estimatedTargetDistanceSquared = estimatedTargetDistanceSquared;
    }

    /** 获取传送前的位置。返回值为副本，修改它不会改变实际传送。 */
    public @NotNull Location getFrom() {
        return from.clone();
    }

    /** 获取原生事件给出的传送目的地。返回值为副本，修改它不会改变实际传送。 */
    public @NotNull Location getTo() {
        return to.clone();
    }

    /**
     * 获取根据传送目的地估算出的目标玩家。
     *
     * @return 容差范围内最近的非旁观玩家；无法估算时返回 null
     */
    public @Nullable Player getEstimatedTarget() {
        return estimatedTarget;
    }

    /**
     * 获取估算目标与传送目的地之间的距离平方。
     *
     * @return 找不到估算目标时为 {@link Double#POSITIVE_INFINITY}
     */
    public double getEstimatedTargetDistanceSquared() {
        return estimatedTargetDistanceSquared;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
