package com.github.mczjuops.mczjugamecore.initialize;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.item.DebugStick;

public class ItemInitializer {
    public static void initialize(){
        MCZJUGameCore.getItemManager().register(new DebugStick());
    }
}
