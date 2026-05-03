package com.github.mczjuops.mczjugamecore.menu;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

public abstract class Menu {
    private String name;  // 菜单名，建议用：插件名缩写_菜单名，不能重复
    private String displayName;
    private final List<Inventory> inventoryList = new LinkedList<>();
    private Menu next;
    private int size;

    private String permission;

    public Menu() {
    }

    public Menu(@NotNull String name, @NotNull String disPlayName, @NotNull Integer size) {
        this.name = name;
        this.size = size;
        this.displayName = disPlayName;
    }

    /**
     * check if the current Inventory is a Menu
     * @param inventory inventory to be checked
     * @return  true if the inventory is Menu
     */
    public final boolean contains(@NotNull Inventory inventory){
        if (inventoryList.contains(inventory)){
            return true;
        }else {
            if (next == null) return false;
            else return next.contains(inventory);
        }
    }

    public final boolean isPlayerInteractWithMenu(@NotNull HumanEntity player){
        for (Inventory it: inventoryList){
            if (it.getHolder() == player) return true;
        }
        if (next != null) return next.isPlayerInteractWithMenu(player);
        else return false;
    }


    public abstract void click(@NotNull InventoryClickEvent event);
    public abstract void open(@NotNull PlayerExt player, @NotNull Inventory inventory, Object... args);

    public void solveOpen(String name, Player player, Object... args){
        if (this.name.equalsIgnoreCase(name) && player.hasPermission(permission)){
            Component title = TextParser.parse(displayName);
            Inventory inventory = Bukkit.createInventory(player, size, title);
            inventoryList.add(inventory);
            PlayerExt playerExt = new PlayerExt(player);
            open(playerExt, inventory, args);
            player.openInventory(inventory);
        }
        else if (next != null) next.solveOpen(name, player, args);
        else noMenuTip(name, new PlayerExt(player));
    }

    public void close(InventoryCloseEvent event){
        if (inventoryList.contains(event.getInventory())) inventoryList.remove(event.getInventory());
        else if (next != null) next.close(event);
    }

    public void setNext(Menu next) {
        this.next = next;
    }

    public void solveClick(InventoryClickEvent event){
        if (inventoryList.contains(event.getClickedInventory())){
            event.setCancelled(true);
            click(event);
        }
        else if (next != null) next.solveClick(event);
    }
    private void noMenuTip(String name, PlayerExt player){
        player.sender().error(STR."没有这个菜单: \{name}");
    }

    public Menu getNext() {
        return next;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDisplayName(String displayName){
        this.displayName = displayName;
    }

    public String getDisplayName(){
        return displayName;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }
}
