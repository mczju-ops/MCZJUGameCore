package com.github.mczjuops.mczjugamecore.event;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** 运行中游戏确认接受中途加入后的同步通知；等待玩家统一由 GameStartEvent 通知正式开局。 */
public final class PlayerGameJoinEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final AbstractGame game;
    private final PlayerExt player;
    /** 核心在相应生命周期确认点创建通知。 */
    public PlayerGameJoinEvent(AbstractGame game, PlayerExt player) { this.game = game; this.player = player; }
    /** @return 接受加入的运行中游戏 */
    public AbstractGame getGame() { return game; }
    /** @return 成功加入的玩家 */
    public PlayerExt getPlayer() { return player; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    /** @return Bukkit 事件处理器列表 */
    public static HandlerList getHandlerList() { return HANDLERS; }
}
