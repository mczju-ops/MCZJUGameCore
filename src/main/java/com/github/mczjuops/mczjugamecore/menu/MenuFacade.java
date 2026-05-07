package com.github.mczjuops.mczjugamecore.menu;

import com.github.mczjuops.mczjugamecore.utils.TextParser;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Range;

import java.util.HashMap;
import java.util.Map;

public class MenuFacade implements Listener {

    private static final Map<Class<? extends Menu>, MenuMeta> registry = new HashMap<>();

    /**
     * 注册一个菜单
     * 注册的唯一目的，是后续创建时不需要注入这些信息
     * 如果需要修改信息（比如某个菜单，不同情况下标题不同），可以注入额外字段并在类内做具体处理
     */
    public static void registerMenu(Class<? extends Menu> menuClass, String title, @Range(from = 1, to = 6) int rows, String permission) {
        if (permission == null) permission = ""; // 还是 default ?
        MenuMeta meta = new MenuMeta(
                TextParser.parse(title),
                rows,
                permission
        );
        registry.put(menuClass, meta);
    }

    // 下面三个方法，用于每次创建 Menu 子类实例时获取
    public static Component getTitle(Class<? extends Menu> menuClass) {
        MenuMeta meta = registry.get(menuClass);
        return meta != null ? meta.title() : null;
    }

    public static int getRows(Class<? extends Menu> menuClass) {
        MenuMeta meta = registry.get(menuClass);
        return meta != null ? meta.rows() : 0;
    }

    public static String getPermission(Class<? extends Menu> menuClass) {
        MenuMeta meta = registry.get(menuClass);
        return meta != null ? meta.permission() : null;
    }

    public static boolean registered(Class<? extends Menu> menuClass) {
        return registry.containsKey(menuClass);
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

    // 内部数据类，便于把注册信息存储到 Map
    private record MenuMeta(Component title, int rows, String permission) {}
}
