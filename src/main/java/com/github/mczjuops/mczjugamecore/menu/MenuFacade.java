package com.github.mczjuops.mczjugamecore.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class MenuFacade implements Listener {

    private static final Map<String, Function<Player, ? extends Menu>> registry = new HashMap<>();

    /** 注册一个菜单的构造方法，用于通过 /menu 为玩家打开此菜单 */
    public static void registerMenu(String menuId, @Nullable Function<Player, ? extends Menu> factory) {
        // 检查是否已有相同 menuId
        if (registry.containsKey(menuId)) {
            throw new IllegalArgumentException("Menu class already registered: %s".formatted(menuId));
        }
        registry.put(menuId.toLowerCase(), factory);
    }

    public static Set<String> getMenuIds() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    public static boolean open(String menuId, Player player) {
        if (!registry.containsKey(menuId.toLowerCase())) return false;
        var factory = registry.get(menuId.toLowerCase());
        if (factory == null) return false;
        factory.apply(player);
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event){
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Menu menu)) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot >= 0 && slot < top.getSize()) menu.handleClick(event);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event){
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        menu.handleClose();
    }
}
