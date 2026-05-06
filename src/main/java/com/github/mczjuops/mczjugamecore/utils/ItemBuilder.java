package com.github.mczjuops.mczjugamecore.utils;

import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * ItemBuilder 用于插件中快速构建自定义 {@link ItemStack} 的工具类。
 *
 * <p>说明：
 * <ul>
 *     <li>封装了几种常用的数据组件（见示例）</li>
 *     <li>链式调用</li>
 *     <li>如果需要修改更多数据组件，可以进一步编辑 </li>
 * </ul>
 *
 * <p>示例：
 * <pre>{@code
 *
 * // 创建一把自定义铁锄，但是贴图是钻石锄
 * ItemStack item = ItemBuilder.of(Material.IRON_HOE)
 *     .customName("<dark_purple>玻璃刀")
 *     .lore(List.of(
 *         "非常锋利",
 *         "<yellow>可以从<red><b>限时商店</b></red>购买"
 *     ))
 *     .glint(true) // 覆盖设置附魔光效
 *     // .amount(1) // 此示例不需要设置
 *     // .maxStackSize(1) // 此示例不需要设置
 *     .itemModel(Material.DIAMOND_HOE) // 贴图换成钻石锄，物品本身还是铁锄（例如不能升级为下界合金锄）
 *     .build();
 *
 * // 构建后的 ItemStack 可进一步修改其他数据（如果需要）
 * item.editPersistentDataContainer(pdc -> {
 *     pdc.set(itemId, PersistentDataType.STRING, "glass_knife");
 *     pdc.set(ownerUuid, PersistentDataType.STRING, player.getUniqueId().toString());
 * });
 * }</pre>
 */
public class ItemBuilder {

    private final ConsoleSender logger = new ConsoleSender("ItemBuilder");

    private final Material material;

    private Component customName; // 物品自定义名称
    private final List<Component> lore = new ArrayList<>(); // 物品描述
    private Boolean glint; // 覆盖设置附魔光效
    private Integer maxStackSize; // 物品最大堆叠数，若为 null，则使用默认值
    private Material itemModel; // 物品的贴图
    private Integer amount = 1;

    /**
     * 创建新的 ItemBuilder。
     *
     * @param material 物品材质
     */
    public ItemBuilder(Material material) {
        this.material = material;
    }

    /** 快速创建一个 ItemBuilder */
    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    /**
     * 设置物品名称（支持 MiniMessage）
     * 不同于原版，字体默认为正体
     */
    public ItemBuilder customName(String customName) {
        if (customName == null) {
            this.customName = null;
        } else {
            this.customName = TextParser.parseNonItalic(customName);
        }
        return this;
    }

    /**
     * 设置物品描述（支持 MiniMessage）
     * 不同于原版，字体默认为正体
     */
    public ItemBuilder lore(List<String> lines) {
        lines.forEach(line -> lore.add(TextParser.parseNonItalic(line)));
        return this;
    }

    /** 覆盖设置附魔光效 */
    public ItemBuilder glint(boolean glint) {
        this.glint = glint;
        return this;
    }

    /** 设置数量（范围 1 ~ 99，不强制在最大堆叠范围内） */
    public ItemBuilder amount(int amount) {
        if (amount < 1 || amount > 99) {
            logger.warn("物品数量必须在 1 和 99 之间");
            amount = Math.clamp(amount, 1, 99);
        }
        this.amount = amount;
        return this;
    }

    /** 设置该物品的最大堆叠数量（范围 1 ~ 99） */
    public ItemBuilder maxStackSize(int maxStackSize) {
        if (maxStackSize < 1 || maxStackSize > 99) {
            logger.warn("物品最大堆叠数必须在 1 和 99 之间");
            maxStackSize = Math.clamp(maxStackSize, 1, 99);
        }
        this.maxStackSize = maxStackSize;
        return this;
    }

    /**
     * 设置使用的物品贴图
     * 指的是物品类型不变，但是把贴图换成该物品。详见 wiki 的 item_model 数据组件
     */
    public ItemBuilder itemModel(Material itemModel) {
        this.itemModel = itemModel;
        return this;
    }

    /** 构建并返回 {@link ItemStack} */
    public @NotNull ItemStack build() {
        ItemStack item = ItemStack.of(material);
        item.editMeta(itemMeta -> {
            if (customName != null) itemMeta.customName(customName);
            itemMeta.lore(lore);
            if (glint != null) itemMeta.setEnchantmentGlintOverride(glint);
            if (maxStackSize != null) itemMeta.setMaxStackSize(maxStackSize);
            if (itemModel != null) itemMeta.setItemModel(itemModel.getKey());
        });

        if (amount != null) item.setAmount(amount);
        return item;
    }
}
