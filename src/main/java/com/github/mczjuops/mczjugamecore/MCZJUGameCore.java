package com.github.mczjuops.mczjugamecore;

import com.github.mczjuops.mczjugamecore.game.manager.AbstractGameManager;
import com.github.mczjuops.mczjugamecore.game.manager.DefaultGameManager;
import com.github.mczjuops.mczjugamecore.game.strategy.impl.SinglePlayerGame;
import com.github.mczjuops.mczjugamecore.player.AbstractPlayerManager;
import com.github.mczjuops.mczjugamecore.player.DefaultPlayerManager;
import com.github.mczjuops.mczjugamecore.player.party.PartyManager;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class MCZJUGameCore extends JavaPlugin {

    private static MCZJUGameCore INSTANCE;
    private AbstractGameManager gameManager;

    private AbstractPlayerManager playerManager;

    private PartyManager partyManager;

    @Override
    public void onEnable() {
        // Plugin startup logic
        INSTANCE = this;
        gameManager = new DefaultGameManager();
        partyManager = new PartyManager();
        playerManager = new DefaultPlayerManager();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static @NotNull MCZJUGameCore getInstance(){
        if (INSTANCE == null){
            throw new RuntimeException("请勿在MCZJUGameCore初始化前，调用getInstance()函数！");
        }
        return INSTANCE;
    }

    /**
     * 获取player manager，用于查看某个游戏有哪些玩家加入、玩家状态之类的
     * @return player manager
     */
    public static @NotNull AbstractPlayerManager getPlayerManager(){
        return getInstance().playerManager;
    }

    /**
     * 获取game manager，用于注册游戏等
     * @return game manager
     */
    public static @NotNull AbstractGameManager  getGameManager(){
        return getInstance().gameManager;
    }
    public static @NotNull ConsoleSender getConsoleSender(){
        return new ConsoleSender("MGC");
    }

    public static @NotNull PartyManager getPartymanager(){
        return getInstance().partyManager;
    }
}
