package com.github.mczjuops.mczjugamecore.game.kit.clock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ActionClock} 的扣时顺序：**额外时间先花，基础时间后花**。
 *
 * <p>顺序反了会把「每回合刷新的救命 5 秒」变成「永久扣的基础时间」，
 * 玩家会发现自己明明每回合都在 5 秒内出牌，基础时间却越来越少。
 */
class ActionClockTest {

    private static ActionClock clock(int main, int extra) {
        // owner 为 null：不涉及 Bukkit 显示，只测时间推进
        return new ActionClock(null, null, main, extra);
    }

    @Test
    void extraTimeIsSpentFirst() {
        ActionClock c = clock(20, 5);
        c.decrementOneSecond();
        assertEquals(4, c.extra(), "第一秒必须从额外时间里扣");
        assertEquals(20, c.main(), "基础时间不能先动");
    }

    @Test
    void mainTimeOnlyAfterExtraRunsOut() {
        ActionClock c = clock(20, 2);
        c.decrementOneSecond();
        c.decrementOneSecond();
        assertEquals(0, c.extra());
        assertEquals(20, c.main());

        c.decrementOneSecond();
        assertEquals(19, c.main(), "额外时间用完才开始扣基础时间");
    }

    @Test
    void totalNeverGoesNegative() {
        ActionClock c = clock(1, 1);
        for (int i = 0; i < 10; i++) {
            c.decrementOneSecond();
        }
        assertEquals(0, c.main());
        assertEquals(0, c.extra());
        assertEquals(0, c.total());
    }

    @Test
    void refreshExtraResetsOnlyExtra() {
        ActionClock c = clock(20, 5);
        for (int i = 0; i < 8; i++) {
            c.decrementOneSecond();
        }
        assertEquals(17, c.main(), "8 秒里有 5 秒花在额外时间上");
        assertEquals(0, c.extra());

        c.refreshExtra(5);
        assertEquals(5, c.extra(), "每回合刷新额外时间");
        assertEquals(17, c.main(), "基础时间一旦消耗不再恢复");
    }

    @Test
    void negativeInputIsClamped() {
        ActionClock c = clock(-5, -3);
        assertEquals(0, c.main());
        assertEquals(0, c.extra());

        c.refreshExtra(-1);
        assertEquals(0, c.extra());
    }
}
