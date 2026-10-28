package com.github.mczjuops.mczjugamecore.event;

import com.github.mczjuops.mczjugamecore.achievement.AchievementKey;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

/** 首次解锁的同步通知；历史统计补发也会触发，不应据此假设玩家在线。 */
public final class PlayerAchievementUnlockedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final AchievementKey key;
    private final boolean historical;
    /** 构造首次解锁通知；historical 表示注册时根据历史统计补发。 */
    public PlayerAchievementUnlockedEvent(UUID playerId, AchievementKey key, boolean historical) {
        this.playerId = playerId; this.key = key; this.historical = historical;
    }
    /** @return 玩家 UUID */
    public UUID getPlayerId() { return playerId; }
    /** @return 成就唯一标识 */
    public AchievementKey getKey() { return key; }
    /** @return 是否为历史补发 */
    public boolean isHistorical() { return historical; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    /** @return Bukkit 事件处理器列表 */
    public static HandlerList getHandlerList() { return HANDLERS; }
}
