package com.github.mczjuops.mczjugamecore.event;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.statistics.GameStatistic;
import org.bukkit.event.Event;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

/**
 * 一次统计采集前的过滤事件，取消仅跳过此次计数；不影响生命周期或直接 set/add。
 * 可排除练习阶段、自定义观众，或取消自动死亡采集而保留手动 reportDeath。
 */
public final class GameStatisticCollectEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final AbstractGame game;
    private final UUID playerId;
    private final GameStatistic statistic;
    private final GameStatisticChangedEvent.Source source;
    private boolean cancelled;
    /** 创建即将增加一次的采集请求。 */
    public GameStatisticCollectEvent(AbstractGame game, UUID playerId, GameStatistic statistic, GameStatisticChangedEvent.Source source) {
        this.game = game; this.playerId = playerId; this.statistic = statistic; this.source = source;
    }
    /** @return 采集所属实例 */
    public AbstractGame getGame() { return game; }
    /** @return 玩家 UUID */
    public UUID getPlayerId() { return playerId; }
    /** @return 本次统计项 */
    public GameStatistic getStatistic() { return statistic; }
    /** @return 自动或主动死亡上报来源 */
    public GameStatisticChangedEvent.Source getSource() { return source; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    /** @return Bukkit 事件处理器列表 */
    public static HandlerList getHandlerList() { return HANDLERS; }
}
