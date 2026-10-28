package com.github.mczjuops.mczjugamecore.event;

import com.github.mczjuops.mczjugamecore.statistics.GameStatistic;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

/** 统计更新后的只读通知，成就系统通过监听检查达成条件；监听器不应递归修改同一统计。 */
public final class GameStatisticChangedEvent extends Event {
    /** 修改来源。 */
    public enum Source { AUTOMATIC, REPORTED_DEATH, API }
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final String gameId;
    private final GameStatistic statistic;
    private final long oldValue, newValue;
    private final Source source;
    /** 构造已完成的统计变更通知。 */
    public GameStatisticChangedEvent(UUID playerId, String gameId, GameStatistic statistic,
                                    long oldValue, long newValue, Source source) {
        this.playerId = playerId; this.gameId = gameId; this.statistic = statistic;
        this.oldValue = oldValue; this.newValue = newValue; this.source = source;
    }
    /** @return 玩家 UUID */
    public UUID getPlayerId() { return playerId; }
    /** @return 游戏 ID */
    public String getGameId() { return gameId; }
    /** @return 统计项 */
    public GameStatistic getStatistic() { return statistic; }
    /** @return 更新前数值 */
    public long getOldValue() { return oldValue; }
    /** @return 更新后数值 */
    public long getNewValue() { return newValue; }
    /** @return 自动采集、主动死亡上报或 API 修改 */
    public Source getSource() { return source; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    /** @return Bukkit 事件处理器列表 */
    public static HandlerList getHandlerList() { return HANDLERS; }
}
