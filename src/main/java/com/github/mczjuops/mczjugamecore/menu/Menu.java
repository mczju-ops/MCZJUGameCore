package com.github.mczjuops.mczjugamecore.menu;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public abstract class Menu implements InventoryHolder {

    protected final @NotNull PlayerExt player;
    protected final String permission;

    protected @NotNull Inventory inventory;

    private final Map<Integer, SlotAction> slotActions = new HashMap<>(); // 槽位 -> 回调

    public Menu(Class<? extends Menu> menuClass, Player player) {
        if (!MenuFacade.registered(menuClass)) {
            throw new IllegalStateException("Menu %s is not registered".formatted(menuClass.getName()));
        }

        this.permission = MenuFacade.getPermission(menuClass);
        this.player = new PlayerExt(player);
        inventory = Bukkit.createInventory(
                this,
                MenuFacade.getRows(menuClass) * 9,
                Objects.requireNonNull(MenuFacade.getTitle(menuClass))); // 注册了就不会是 null
    }

    /** 子类填充菜单 */
    protected abstract void setup();

    public void open() {
        if (!player.player().hasPermission(permission)) return;

        setup();
        player.player().openInventory(inventory);
    }

    /** 提供给子类的注册方法，无回调（此槽位纯展示） */
    protected void setSlot(int slot, ItemStack display) {
        inventory.setItem(slot, display);
    }

    /** 提供给子类的注册方法，带回调 */
    protected void setSlot(int slot, ItemStack display, SlotAction action) {
        inventory.setItem(slot, display);
        if (action != null) slotActions.put(slot, action);
    }

    public void handleClick(InventoryClickEvent event) {
        SlotAction action = slotActions.get(event.getSlot());
        if (action != null) action.execute(player.player(), event);
    }

    public void handleClose() {}

    /** 刷新此菜单 */
    public void refresh() {
        setup();
        player.player().updateInventory();
    }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
}
