package com.github.mczjuops.mczjugamecore.item;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 大厅中用于打开 MGC 主菜单的钟。 */
public final class LobbyMenuClock extends MGCItem implements Listener {

    @Override
    protected @NotNull ItemStack createRawItem() {
        return ItemBuilder.of(Material.CLOCK)
                .customName("<gold>MGC 菜单")
                .lore(List.of("<gray>左键或右键打开菜单"))
                .build();
    }

    @Override
    public @NotNull String getId() {
        return MGCMaterial.LOBBY_MENU_CLOCK.toString();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!MCZJUGameCore.getConfigManager().isLobbyProfileFeaturesEnabled()
                || event.getHand() != EquipmentSlot.HAND
                || event.getAction() == Action.PHYSICAL
                || event.getItem() == null
                || !isThis(event.getItem())) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().performCommand("mgc");
    }
}
