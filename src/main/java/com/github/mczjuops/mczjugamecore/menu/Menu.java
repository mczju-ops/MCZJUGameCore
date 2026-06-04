package com.github.mczjuops.mczjugamecore.menu;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.HashMap;
import java.util.Map;

public abstract class Menu implements InventoryHolder {

    protected final @NotNull PlayerExt player;
    protected @NotNull Inventory inventory;

    private final Map<Integer, SlotAction> slotActions = new HashMap<>(); // 槽位 -> 回调
    protected final Object[] args;

    public Menu(Player player, Object... args) {
        this.args = args;
        this.player = new PlayerExt(player);
        inventory = Bukkit.createInventory(
                this,
                getRows() * 9,
                TextParser.parse(getTitle()));
    }

    /** 子类填充菜单 */
    protected abstract void setup();

    public void open() {
        if (!player.player().hasPermission(getPermission())) return;

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
        if (action != null) action.execute(player, event);
    }

    public void handleClose() {}

    /** 刷新此菜单 */
    protected void refresh() {
        clearMenu();
        setup();
        player.player().updateInventory();
    }

    protected void clearMenu() {
        inventory.clear();
        slotActions.clear();
    }

    protected abstract String getTitle();
    public Object[] getArgs(){
        return this.args;
    }

    protected abstract @Range(from = 1, to = 6) int getRows();

    protected abstract String getPermission();

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
}
