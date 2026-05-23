package com.github.mczjuops.mczjugamecore.item;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
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

    /**
     * 重写equal函数，可以传ItemStack或者MGCItem
     * @param obj   the reference object with which to compare.
     * @return  他们的id相同，则为同一个物品
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MGCItem){
            return Objects.equals(((MGCItem) obj).getId(), getId());
        } else if (obj instanceof ItemStack) {
            return isThis((ItemStack) obj);
        }
        return false;
    }
}
