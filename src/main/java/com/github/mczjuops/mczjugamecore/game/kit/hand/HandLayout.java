package com.github.mczjuops.mczjugamecore.game.kit.hand;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 手牌的槽位布局。
 *
 * <p>两个游戏差别很大，所以这里只描述「哪些槽位算手牌、哪些是保留按钮、副手锁不锁」：
 *
 * <table border="1">
 *   <caption>典型布局</caption>
 *   <tr><th></th><th>SoulCard</th><th>日麻</th></tr>
 *   <tr><td>手牌槽</td><td>0~35 去掉 17/26/35</td><td>0~13</td></tr>
 *   <tr><td>保留槽</td><td>17 上一页 / 26 出牌 / 35 下一页</td><td>26 打出（可选）</td></tr>
 *   <tr><td>翻页</td><td>需要</td><td>不需要</td></tr>
 * </table>
 *
 * <p>副手（槽位 40）一律锁死：手牌扫描只覆盖主背包，牌放进副手会导致
 * 「手牌数少一张、界面里选不到」。
 */
public final class HandLayout {

    /** 副手槽位。 */
    public static final int OFFHAND_SLOT = 40;
    /** 主背包容量：0~35 是快捷栏 + 三行背包。 */
    public static final int MAIN_SIZE = 36;
    /** 盔甲槽范围。 */
    public static final int ARMOR_FROM = 36;
    public static final int ARMOR_TO = 39;

    private final List<Integer> handSlots;
    private final Set<Integer> reserved;
    private final Map<Integer, ItemStack> reservedItems;
    private final boolean lockOffhand;

    private HandLayout(Builder b) {
        this.handSlots = List.copyOf(b.handSlots);
        this.reserved = Collections.unmodifiableSet(new LinkedHashSet<>(b.reserved));
        this.reservedItems = Collections.unmodifiableMap(new LinkedHashMap<>(b.reservedItems));
        this.lockOffhand = b.lockOffhand;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 连续 {@code count} 个槽位（从 0 开始），不接受保留槽。 */
    public static HandLayout contiguous(int count) {
        return builder().autoSlots(count, 0).build();
    }

    /** 0~35 全部可用，去掉给定保留槽（SoulCard 的布局）。 */
    public static HandLayout mainInventory(int... reservedSlots) {
        Builder b = builder();
        for (int i = 0; i < MAIN_SIZE; i++) {
            b.slot(i);
        }
        for (int s : reservedSlots) {
            b.reserve(s);
        }
        return b.build();
    }

    public List<Integer> handSlots() {
        return handSlots;
    }

    /** 可用手牌槽数量（不含保留槽）。 */
    public int capacity() {
        return handSlots.size();
    }

    public boolean isHandSlot(int slot) {
        return handSlots.contains(slot);
    }

    public boolean isReserved(int slot) {
        return reserved.contains(slot);
    }

    /** 保留槽上应该常驻的物品（翻页 / 出牌按钮）。 */
    public Map<Integer, ItemStack> reservedItems() {
        return reservedItems;
    }

    public boolean lockOffhand() {
        return lockOffhand;
    }

    /** 副手是否属于「不归本布局管」的槽位（盔甲槽同理）。 */
    public static boolean isOffhand(int slot) {
        return slot == OFFHAND_SLOT;
    }

    public static boolean isArmor(int slot) {
        return slot >= ARMOR_FROM && slot <= ARMOR_TO;
    }

    public static final class Builder {
        private final List<Integer> handSlots = new ArrayList<>();
        private final Set<Integer> reserved = new LinkedHashSet<>();
        private final Map<Integer, ItemStack> reservedItems = new LinkedHashMap<>();
        private boolean lockOffhand = true;

        /** 添加一个手牌槽（重复添加会被忽略）。 */
        public Builder slot(int index) {
            if (!handSlots.contains(index)) {
                handSlots.add(index);
            }
            return this;
        }

        /** 添加 [from, from+count) 区间。 */
        public Builder autoSlots(int count, int from) {
            for (int i = from; i < from + count; i++) {
                slot(i);
            }
            return this;
        }

        /** 声明保留槽：它不再算手牌槽，可以挂一个常驻物品。 */
        public Builder reserve(int index) {
            return reserve(index, null);
        }

        public Builder reserve(int index, ItemStack fixedItem) {
            handSlots.remove(Integer.valueOf(index));
            reserved.add(index);
            if (fixedItem != null) {
                reservedItems.put(index, fixedItem);
            }
            return this;
        }

        public Builder lockOffhand(boolean value) {
            this.lockOffhand = value;
            return this;
        }

        public HandLayout build() {
            return new HandLayout(this);
        }
    }

    /** 副手占位物（各游戏可以换成自己的图标）。 */
    public static ItemStack defaultOffhandPlaceholder() {
        return com.github.mczjuops.mczjugamecore.utils.ItemBuilder.of(Material.BARRIER)
                .customName("<red>副手已禁用")
                .lore(List.of("<gray>本局不允许使用副手", "<dark_gray>该物品不可移动、不可丢弃"))
                .build();
    }

    /** 把保留槽物品与副手占位物写进玩家背包（主线程）。 */
    public void applyTo(PlayerInventory inventory) {
        if (inventory == null) {
            return;
        }
        if (lockOffhand) {
            inventory.setItemInOffHand(defaultOffhandPlaceholder());
        }
        reservedItems.forEach(inventory::setItem);
    }
}
