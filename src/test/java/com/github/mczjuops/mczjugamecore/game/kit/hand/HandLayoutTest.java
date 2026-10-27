package com.github.mczjuops.mczjugamecore.game.kit.hand;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HandLayout}：哪些槽位算手牌。
 *
 * <p>这是两个游戏差别最大的地方（SoulCard 36 槽 + 翻页，日麻 14 张 + 自动理牌），
 * 也是「牌放进保留槽后消失」这类事故的源头，所以单独锁住。
 */
class HandLayoutTest {

    @Test
    void soulCardStyleExcludesReservedSlots() {
        HandLayout layout = HandLayout.mainInventory(17, 26, 35);
        assertEquals(33, layout.capacity(), "36 个槽减掉 3 个保留槽");
        assertTrue(layout.isHandSlot(0));
        assertTrue(layout.isHandSlot(16));
        assertFalse(layout.isHandSlot(17), "上一页按钮位不能算手牌");
        assertFalse(layout.isHandSlot(26), "出牌按钮位不能算手牌");
        assertFalse(layout.isHandSlot(35), "下一页按钮位不能算手牌");
        assertTrue(layout.isReserved(26));
    }

    @Test
    void mahjongStyleIsSingleRow() {
        HandLayout layout = HandLayout.contiguous(14);
        assertEquals(14, layout.capacity());
        assertTrue(layout.isHandSlot(13));
        assertFalse(layout.isHandSlot(14), "14 张之外不该还有手牌槽");
    }

    @Test
    void reserveRemovesFromHandSlots() {
        HandLayout layout = HandLayout.builder()
                .autoSlots(14, 0)
                .reserve(9)
                .build();
        assertEquals(13, layout.capacity());
        assertFalse(layout.isHandSlot(9));
        assertTrue(layout.isReserved(9));
    }

    @Test
    void duplicateSlotIsIgnored() {
        HandLayout layout = HandLayout.builder().slot(0).slot(0).slot(1).build();
        assertEquals(2, layout.capacity());
    }

    @Test
    void armorAndOffhandAreRecognised() {
        assertTrue(HandLayout.isOffhand(40));
        assertTrue(HandLayout.isArmor(36));
        assertTrue(HandLayout.isArmor(39));
        assertFalse(HandLayout.isArmor(35));
        assertFalse(HandLayout.isOffhand(39));
    }

    @Test
    void offhandIsLockedByDefault() {
        assertTrue(HandLayout.contiguous(14).lockOffhand(),
                "默认焊死副手：手牌扫描只覆盖主背包，牌放副手会数不到");
        assertFalse(HandLayout.builder().lockOffhand(false).build().lockOffhand());
    }

    @Test
    void reservedItemSlotIsNotHandSlot() {
        HandLayout layout = HandLayout.builder()
                .autoSlots(14, 0)
                .reserve(9)
                .build();
        assertTrue(layout.isReserved(9));
        assertTrue(layout.reservedItems().isEmpty(), "不挂固定物品时不该凭空塞东西");
        assertEquals(13, layout.capacity());
    }
}
