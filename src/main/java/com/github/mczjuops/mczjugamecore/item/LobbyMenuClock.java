package com.github.mczjuops.mczjugamecore.item;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 用于打开 MGC 主菜单，并固定在快捷栏第 9 格的钟。 */
public final class LobbyMenuClock extends MGCItem implements Listener {

    private static final int FIXED_SLOT = 8;

    @Override
    protected @NotNull ItemStack createRawItem() {
        return ItemBuilder.of(Material.CLOCK)
                .customName("<#DEB12D>小游戏菜单<gray>（点击）")
                .lore(List.of(
                        "<gray>/mgc",
                        "<yellow>点击打开小游戏菜单"
                ))
                .maxStackSize(1)
                .glint(true)
                .build();
    }

    @Override
    public @NotNull String getId() {
        return MGCMaterial.LOBBY_MENU_CLOCK.toString();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() == Action.PHYSICAL
                || event.getItem() == null
                || !isThis(event.getItem())) {
            return;
        }

        event.setCancelled(true);
        ensureInFixedSlot(event.getPlayer());
        event.getPlayer().performCommand("mgc");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || !isThis(event.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }

        event.setCancelled(true);
        ensureInFixedSlot(event.getPlayer());
        event.getPlayer().performCommand("mgc");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        boolean clickedClock = isThis(event.getCurrentItem()) || isThis(event.getCursor());
        boolean exchangedClock = event.getClick() == ClickType.NUMBER_KEY
                && isThis(player.getInventory().getItem(event.getHotbarButton()));
        boolean swappedClockToOffHand = event.getClick() == ClickType.SWAP_OFFHAND
                && isThis(player.getInventory().getItemInOffHand());

        if (!clickedClock && !exchangedClock && !swappedClockToOffHand) return;

        event.setCancelled(true);
        openMenuNextTick(player);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isThis(event.getOldCursor())) event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isThis(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (isThis(event.getMainHandItem()) || isThis(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCreativeInventoryClick(InventoryCreativeEvent event) {
        if (isThis(event.getCurrentItem())) event.setCancelled(true);
    }

    /** 如果玩家已经持有菜单钟，则将其整理到固定槽位。 */
    public void ensureInFixedSlotIfPresent(@NotNull Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isThis(inventory.getItem(slot))) {
                ensureInFixedSlot(player);
                return;
            }
        }
    }

    /** 将菜单钟整理到玩家快捷栏第 9 格，并妥善转移该格原有物品。 */
    public void ensureInFixedSlot(@NotNull Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack displacedItem = inventory.getItem(FIXED_SLOT);

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isThis(inventory.getItem(slot))) inventory.setItem(slot, null);
        }

        inventory.setItem(FIXED_SLOT, getItem());

        if (displacedItem == null || displacedItem.getType().isAir() || isThis(displacedItem)) return;

        inventory.addItem(displacedItem).values().forEach(item
                -> player.getWorld().dropItem(player.getLocation(), item));
    }

    private void openMenuNextTick(Player player) {
        MCZJUGameCore.getInstance().getServer().getScheduler().runTask(
                MCZJUGameCore.getInstance(),
                () -> {
                    if (player.isOnline()) player.performCommand("mgc");
                }
        );
    }
}
