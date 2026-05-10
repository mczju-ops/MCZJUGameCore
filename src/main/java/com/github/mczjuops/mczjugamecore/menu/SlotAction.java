package com.github.mczjuops.mczjugamecore.menu;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.bukkit.event.inventory.InventoryClickEvent;

@FunctionalInterface
public interface SlotAction {
    void execute(PlayerExt clicker, InventoryClickEvent event);
}