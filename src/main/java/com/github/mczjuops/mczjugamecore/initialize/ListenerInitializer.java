package com.github.mczjuops.mczjugamecore.initialize;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.player.listener.PlayerDeathListener;
import com.github.mczjuops.mczjugamecore.player.listener.PlayerQuitListener;
import com.github.mczjuops.mczjugamecore.player.listener.PlayerSpectatorTeleportListener;
import com.github.mczjuops.mczjugamecore.player.listener.SpectatorTeleportRestrictionListener;
import com.github.mczjuops.mczjugamecore.utils.LocationSelector;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

public class ListenerInitializer {
    public static void initialize(){
        register(MCZJUGameCore.getMenuFacade());
        register(LocationSelector.getInstance());
        register(new PlayerDeathListener());
        register(new PlayerQuitListener());
        register(new PlayerSpectatorTeleportListener());
        register(new SpectatorTeleportRestrictionListener());
        register(MCZJUGameCore.getProfileManager());
    }

    private static void register(Listener listener){
        Bukkit.getPluginManager().registerEvents(listener, MCZJUGameCore.getInstance());
    }
}
