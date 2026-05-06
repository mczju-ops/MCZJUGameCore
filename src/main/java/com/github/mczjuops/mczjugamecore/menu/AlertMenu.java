package com.github.mczjuops.mczjugamecore.menu;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class AlertMenu extends Menu {

    public AlertMenu() {
        setSize(9);
        setName("确认菜单");
        setDisplayName("确认菜单");
        setPermission("menu.default");
    }

    protected static final Map<Player, Runnable> callbackMap = new HashMap<>();

    @Override
    public void click(@NotNull InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (event.getSlot() == 2){
            player.closeInventory();
            callbackMap.get(player).run();
        } else if (event.getSlot() == 6) {
            player.closeInventory();
        }
    }

    @Override
    public void open(@NotNull PlayerExt player, @NotNull Inventory inventory, Object... args) {
        inventory.setItem(2, ItemBuilder.of(Material.GREEN_WOOL)
                .customName("<green>确认")
                .build());

        inventory.setItem(6, ItemBuilder.of(Material.RED_WOOL)
                .customName("<red>取消")
                .build());
    }
}
