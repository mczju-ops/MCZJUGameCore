package com.github.mczjuops.mczjugamecore.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

@FunctionalInterface
public interface SlotAction {
    void execute(Player clicker, InventoryClickEvent event);
}