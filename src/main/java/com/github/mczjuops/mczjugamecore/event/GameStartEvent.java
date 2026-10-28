package com.github.mczjuops.mczjugamecore.event;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import java.util.List;

/** 游戏成功进入 RUNNING 后的同步通知，不可取消；不包含统计业务。 */
public final class GameStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final AbstractGame game;
    private final List<PlayerExt> players;
    /** 核心在相应生命周期确认点创建通知。 */
    public GameStartEvent(AbstractGame game) { this.game = game; this.players = List.copyOf(game.getPlayers()); }
    /** @return 已成功开始的游戏 */
    public AbstractGame getGame() { return game; }
    /** @return 开始时成员的不可变快照 */
    public List<PlayerExt> getPlayers() { return players; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    /** @return Bukkit 事件处理器列表 */
    public static HandlerList getHandlerList() { return HANDLERS; }
}
