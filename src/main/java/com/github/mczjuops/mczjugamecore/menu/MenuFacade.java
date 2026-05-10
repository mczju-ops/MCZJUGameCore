package com.github.mczjuops.mczjugamecore.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class MenuFacade implements Listener {

    private static final Map<String, Constructor<? extends Menu>> registry = new HashMap<>();

    /** 注册一个菜单的构造方法，用于通过 /menu 为玩家打开此菜单 */
    public static void registerMenu(
            String menuId,
            @NotNull Class<? extends Menu> menuClass
    ) {
        String id = menuId.toLowerCase();

        if (registry.containsKey(id)) {
            throw new IllegalArgumentException(
                    "Menu already registered: " + menuId
            );
        }

        try {
            Constructor<? extends Menu> constructor =
                    menuClass.getConstructor(Player.class, Object[].class);

            constructor.setAccessible(true);

            registry.put(id, constructor);

        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    """
                    Menu class %s must have constructor:
                    (Player player, Object... args)
                    """.formatted(menuClass.getName()),
                    e
            );
        }
    }

    public static Set<String> getMenuIds() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    public static boolean open(String menuId, Player player, Object... args) {
        if (!registry.containsKey(menuId.toLowerCase())) return false;
        var factory = registry.get(menuId.toLowerCase());
        if (factory == null) return false;
        try {
            factory.newInstance(player, args).open();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
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
