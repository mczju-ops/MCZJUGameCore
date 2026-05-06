package com.github.mczjuops.mczjugamecore.item;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;

public abstract class MGCItem {

    protected abstract ItemStack createRawItem();

    public abstract String getId();

    public final ItemStack getItem() {
        ItemStack item = createRawItem();
        item.editPersistentDataContainer(pdc ->
                pdc.set(MCZJUGameCore.getItemManager().getKey(), PersistentDataType.STRING, getId()));
        return item;
    }

    // 判断是否是当前物品
    public final boolean isThis(ItemStack item) {
        String id = item.getPersistentDataContainer()
                .get(MCZJUGameCore.getItemManager().getKey(), PersistentDataType.STRING);
        return Objects.equals(id, getId());
    }
}
