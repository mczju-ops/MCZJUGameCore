package com.github.mczjuops.mczjugamecore.event;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import org.bukkit.event.Event;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

/** 统计提交前的同步事件；取消仅阻止本项统计，不影响游戏死亡流程或另一项统计。 */
public final class GameDeathEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;
    private final AbstractGame game;
    private final UUID victimId;
    /** 构造由统计管理器发出的事件。 */
    public GameDeathEvent(AbstractGame game, UUID victimId) { this.game = game; this.victimId = victimId; }
    /** @return 死亡发生的游戏实例 */
    public AbstractGame getGame() { return game; }
    /** @return 受害者 UUID */
    public UUID getVictimId() { return victimId; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    /** @return Bukkit 事件处理器列表 */
    public static HandlerList getHandlerList() { return HANDLERS; }
}
