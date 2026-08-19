package com.github.mczjuops.mczjugamecore.initialize;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.item.DebugStick;
import com.github.mczjuops.mczjugamecore.item.LobbyMenuClock;
import org.bukkit.Bukkit;

public class ItemInitializer {
    public static void initialize(){
        MCZJUGameCore.getItemManager().register(new DebugStick());
        LobbyMenuClock lobbyMenuClock = new LobbyMenuClock();
        MCZJUGameCore.getItemManager().register(lobbyMenuClock);
        Bukkit.getPluginManager().registerEvents(lobbyMenuClock, MCZJUGameCore.getInstance());
    }
}
