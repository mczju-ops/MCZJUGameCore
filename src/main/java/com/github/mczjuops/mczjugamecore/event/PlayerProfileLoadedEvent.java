package com.github.mczjuops.mczjugamecore.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 玩家登录档案成功加载并应用后，在服务器线程触发；此时可以安全切换游戏档案。
 * 加载失败或玩家在加载期间离线时不触发，不代表玩家已加入某个游戏。
 */
public final class PlayerProfileLoadedEvent extends PlayerEvent {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    /** @param player 档案已恢复的在线玩家 */
    public PlayerProfileLoadedEvent(@NotNull Player player) {
        super(player);
    }

    /** @return 本事件的监听器列表 */
    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    /** @return 用于 Bukkit 注册监听器的事件处理列表 */
    public static @NotNull HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
