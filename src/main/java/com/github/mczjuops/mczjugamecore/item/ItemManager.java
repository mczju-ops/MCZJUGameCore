package com.github.mczjuops.mczjugamecore.item;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 物品管理器，用于注册、存储和查询自定义 MGCItem。
 * 该类基于 {@link NamespacedKey} 在 ItemStack 的 PersistentDataContainer 中
 * 存储物品 ID，用于实现物品的唯一标识与反查。
 */
public class ItemManager {

    /**
     * 用于在 ItemStack 的 PersistentDataContainer 中存储物品 ID 的 Key。
     */
    private final NamespacedKey key;

    /**
     * 已注册的物品映射表（ID -> MGCItem）。
     */
    private final Map<String, MGCItem> items = new HashMap<>();

    /**
     * 创建物品管理器。
     */
    public ItemManager() {
        this.key = new NamespacedKey(MCZJUGameCore.getInstance(), "mgc_item_id");
    }

    /**
     * 获取用于标识自定义物品的 NamespacedKey。
     *
     * @return NamespacedKey 实例
     */
    public NamespacedKey getKey() {
        return key;
    }

    /**
     * 注册一个自定义物品。
     *
     * @param item 要注册的 MGCItem
     */
    public void register(MGCItem item) {
        items.put(item.getId(), item);
    }

    /**
     * 根据物品 ID 获取对应的 MGCItem。
     *
     * @param id 物品唯一 ID
     * @return 对应的 MGCItem，如果不存在则返回 null
     */
    public @Nullable MGCItem get(String id) {
        return items.get(id);
    }

    /**
     * 根据物品 ID 获取对应的 ItemStack。
     *
     * @param id 物品唯一 ID
     * @return 对应的 ItemStack，如果不存在则返回 null
     */
    public @Nullable ItemStack getItem(String id) {
        MGCItem item = items.get(id);
        return item != null ? item.getItem() : null;
    }

    /**
     * 从 ItemStack 中读取其绑定的物品 ID。
     *
     * @param item 物品实例
     * @return 物品 ID，如果没有则返回 null
     */
    public @Nullable String getItemId(@NotNull ItemStack item) {
        return item.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /**
     * 判断某个 ItemStack 是否属于指定物品 ID。
     *
     * @param item 要检查的物品
     * @param id   目标物品 ID
     * @return 如果匹配返回 true，否则 false
     */
    public boolean is(@Nullable ItemStack item, String id) {
        if (item == null) return false;
        String itemId = getItemId(item);
        return id.equals(itemId);
    }
}
