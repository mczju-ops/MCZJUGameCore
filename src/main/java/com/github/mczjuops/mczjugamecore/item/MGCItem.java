package com.github.mczjuops.mczjugamecore.item;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public abstract class MGCItem {

    protected abstract ItemStack createItem();

    public abstract String getId();

    public final ItemStack getItem() {
        ItemStack item = createItem();

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer container = meta.getPersistentDataContainer();

        // 写入ID标记
        container.set(MCZJUGameCore.getItemManager().getKey(), PersistentDataType.STRING, getId());

        item.setItemMeta(meta);
        return item;
    }

    // 判断是否是当前物品
    public final boolean isThis(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return false;

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();

        String id = container.get(MCZJUGameCore.getItemManager().getKey(), PersistentDataType.STRING);

        return getId().equals(id);
    }
}
