package com.github.mczjuops.mczjugamecore.event;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import org.bukkit.event.Event;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

/** 统计提交前的同步事件；取消仅阻止本项统计，不影响游戏死亡流程或另一项统计。 */
public final class GameKillEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;
    private final AbstractGame game;
    private final UUID victimId;
    private UUID killerId;
    /** 构造由统计管理器发出的事件。 */
    public GameKillEvent(AbstractGame game, UUID victimId, UUID killerId) {
        this.game = game; this.victimId = victimId; this.killerId = killerId;
    }
    /** @return 击杀发生的游戏实例 */
    public AbstractGame getGame() { return game; }
    /** @return 受害者 UUID */
    public UUID getVictimId() { return victimId; }
    /** @return 击杀者 UUID，可为 null（环境死亡） */
    public UUID getKillerId() { return killerId; }
    /** 修改归因；null 表示无击杀者，非 null 必须是本局有效参赛者。 */
    public void setKillerId(UUID killerId) { this.killerId = killerId; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    /** @return Bukkit 事件处理器列表 */
    public static HandlerList getHandlerList() { return HANDLERS; }
}
