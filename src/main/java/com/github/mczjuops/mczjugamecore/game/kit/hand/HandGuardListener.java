package com.github.mczjuops.mczjugamecore.game.kit.hand;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.function.Predicate;

/**
 * 手牌防护：手牌可以自由交换位置（理牌），但不能丢出、不能放进容器或盔甲槽。
 *
 * <p>保留槽（翻页 / 出牌按钮）不可移动，副手整局禁用。
 *
 * <p><b>⚠️ 本监听器注册在插件级别（全局），必须自己限制作用范围。</b>
 * 每个事件入口都先问 {@code inGame}，不是局内玩家就直接放行 ——
 * 否则大厅里的玩家也会被塞副手占位物、被禁止 F 键交换。
 */
public final class HandGuardListener implements Listener {

    private final HandLayout layout;
    private final Predicate<ItemStack> owned;
    private final Predicate<Player> inGame;

    /**
     * @param layout 槽位布局
     * @param owned  判断某物品是不是本游戏的手牌（通常是 {@code hand::owns}）
     * @param inGame 判断某玩家是否正在本游戏对局中
     */
    public HandGuardListener(HandLayout layout, Predicate<ItemStack> owned, Predicate<Player> inGame) {
        this.layout = layout;
        this.owned = owned;
        this.inGame = inGame;
    }

    private boolean isProtected(ItemStack item) {
        if (item == null) {
            return false;
        }
        if (owned.test(item)) {
            return true;
        }
        // 保留槽上的按钮同样不许丢
        for (ItemStack fixed : layout.reservedItems().values()) {
            if (fixed != null && fixed.getType() == item.getType()) {
                return true;
            }
        }
        return false;
    }

    private boolean isReservedButton(ItemStack item) {
        if (item == null) {
            return false;
        }
        for (ItemStack fixed : layout.reservedItems().values()) {
            if (fixed != null && fixed.getType() == item.getType()) {
                return true;
            }
        }
        return false;
    }

    /** 禁止丢出手牌；副手占位物同样不能丢。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!inGame.test(event.getPlayer())) {
            return;
        }
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (layout.lockOffhand() && dropped.getType() == Material.BARRIER) {
            event.setCancelled(true);
            return;
        }
        if (!isProtected(dropped)) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().updateInventory();
    }

    /** 禁止用手牌 / 占位物按 F 交换主副手。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (!inGame.test(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
    }

    /** 阻止按钮被移动、阻止物品进入按钮槽、阻止手牌进入其他容器。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!inGame.test(player)) {
            return;
        }
        var clicked = event.getClickedInventory();
        boolean ownInventory = clicked != null && clicked.equals(player.getInventory());

        if (ownInventory) {
            int slot = event.getSlot();
            if (layout.lockOffhand() && HandLayout.isOffhand(slot)) {
                // 副手常驻占位物：既不能被拿走，也不能往里放东西
                event.setCancelled(true);
                layout.applyTo(player.getInventory());
                return;
            }
            if (layout.isReserved(slot)) {
                // 按钮的点击由游戏自己的监听器处理，这里只禁止搬动
                if (!isReservedButton(event.getCurrentItem())) {
                    event.setCancelled(true);
                }
                return;
            }
            // 不允许把物品放进盔甲槽
            if (HandLayout.isArmor(slot) && owned.test(event.getCursor())) {
                event.setCancelled(true);
            }
            return;
        }

        // 非本人背包（箱子等）：禁止移动手牌进出
        if (clicked != null) {
            if (isProtected(event.getCurrentItem()) || isProtected(event.getCursor())) {
                event.setCancelled(true);
            }
            return;
        }

        // 快捷移动（shift 点击）把手牌送进其他容器
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            var top = event.getView().getTopInventory();
            boolean topIsOwn = top.equals(player.getInventory());
            if (!topIsOwn && isProtected(event.getCurrentItem())) {
                event.setCancelled(true);
            }
        }
    }

    /** 禁止把手牌拖到保留槽位或副手。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!inGame.test(player)) {
            return;
        }
        if (!event.getInventory().equals(player.getInventory())) {
            return;
        }
        for (int raw : event.getRawSlots()) {
            if ((layout.lockOffhand() && HandLayout.isOffhand(raw)) || layout.isReserved(raw)) {
                event.setCancelled(true);
                layout.applyTo(player.getInventory());
                return;
            }
        }
    }

    /** 禁止把手牌当道具使用（水桶、盾牌等）。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!inGame.test(event.getPlayer())) {
            return;
        }
        if (layout.lockOffhand() && event.getHand() == EquipmentSlot.OFF_HAND) {
            event.setCancelled(true);
            layout.applyTo(event.getPlayer().getInventory());
            return;
        }
        if (isProtected(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /** 禁止把手牌当方块放置。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!inGame.test(event.getPlayer())) {
            return;
        }
        if (isProtected(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }
}
