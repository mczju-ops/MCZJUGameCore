package com.github.mczjuops.mczjugamecore.event;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import org.bukkit.event.Event;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import java.util.Objects;
import java.util.UUID;

/**
 * 子插件通过 Bukkit.getPluginManager().callEvent(...) 上报结算，核心在 MONITOR 统计。
 * 每次未取消事件累计一次；子插件负责只在结算时上报一次，不保存游戏 UUID 或结算流水。
 * 可为离线玩家结算，但必须有本服登录记录；结果不自动推断其他玩家输赢。
 */
public class GameResultEvent extends Event implements Cancellable {
    /** 互斥的结算结果。 */
    public enum Outcome { WIN, LOSS, DRAW }
    private static final HandlerList HANDLERS = new HandlerList();
    private final String gameId;
    private final UUID playerId;
    private final Outcome outcome;
    private boolean cancelled;
    /** 上报游戏中一位玩家的结果。 */
    public GameResultEvent(AbstractGame game, UUID playerId, Outcome outcome) {
        this(game.getId(), playerId, outcome);
    }
    /** 按游戏 ID 上报，可用于开放会话子回合或离线结算。 */
    public GameResultEvent(String gameId, UUID playerId, Outcome outcome) {
        this.gameId = Objects.requireNonNull(gameId); this.playerId = Objects.requireNonNull(playerId);
        this.outcome = Objects.requireNonNull(outcome);
    }
    /** @return 游戏 ID */
    public String getGameId() { return gameId; }
    /** @return 玩家 UUID */
    public UUID getPlayerId() { return playerId; }
    /** @return 胜负平局结果 */
    public Outcome getOutcome() { return outcome; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    /** @return Bukkit 事件处理器列表；GameWinEvent 共用此列表 */
    public static HandlerList getHandlerList() { return HANDLERS; }
}
