package com.github.mczjuops.mczjugamecore;

import com.github.mczjuops.mczjugamecore.game.manager.AbstractGameManager;
import com.github.mczjuops.mczjugamecore.game.strategy.impl.SinglePlayerGame;
import com.github.mczjuops.mczjugamecore.player.AbstractPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class MCZJUGameCore extends JavaPlugin {

    private static MCZJUGameCore INSTANCE;

    @Override
    public void onEnable() {
        // Plugin startup logic
        INSTANCE = this;

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static MCZJUGameCore getInstance(){
        return INSTANCE;
    }

    /**
     * 获取player manager，用于查看某个游戏有哪些玩家加入、玩家状态之类的
     * @return player manager
     */
    public static @NotNull AbstractPlayerManager getPlayerManager(){
        return null;
    }

    /**
     * 获取game manager，用于注册游戏等
     * @return game manager
     */
    public static @NotNull AbstractGameManager  getGameManager(){
        return null;
    }
}
