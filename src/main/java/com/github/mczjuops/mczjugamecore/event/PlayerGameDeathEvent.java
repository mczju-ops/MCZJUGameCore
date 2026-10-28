package com.github.mczjuops.mczjugamecore.event;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

/** 核心即将处理游戏内死亡的同步通知；不会取消原版死亡或改变死亡策略。 */
public final class PlayerGameDeathEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final AbstractGame game;
    private final PlayerExt player;
    private final UUID killerId;
    /** 核心在相应生命周期确认点创建通知。 */
    public PlayerGameDeathEvent(AbstractGame game, PlayerExt player, UUID killerId) { this.game = game; this.player = player; this.killerId = killerId; }
    /** @return 死亡所在游戏 */
    public AbstractGame getGame() { return game; }
    /** @return 死亡玩家（尚未执行死亡策略） */
    public PlayerExt getPlayer() { return player; }
    /** @return 原版归因的击杀者 UUID，环境死亡可为 null */
    public UUID getKillerId() { return killerId; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    /** @return Bukkit 事件处理器列表 */
    public static HandlerList getHandlerList() { return HANDLERS; }
}
