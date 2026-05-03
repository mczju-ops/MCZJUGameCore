package com.github.mczjuops.mczjugamecore.initialize;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import com.github.mczjuops.mczjugamecore.utils.LocationSelector;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

public class ListenerInitializer {
    public static void initialize(){
        register(MCZJUGameCore.getMenuFacade());
        register(LocationSelector.getInstance());
    }

    private static void register(Listener listener){
        Bukkit.getPluginManager().registerEvents(listener, MCZJUGameCore.getInstance());
    }
}
