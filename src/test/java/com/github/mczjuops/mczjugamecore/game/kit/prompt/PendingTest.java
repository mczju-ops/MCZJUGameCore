package com.github.mczjuops.mczjugamecore.game.kit.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Pending} 的核心语义：交付、取消、超时。
 *
 * <p>这些语义是「玩家作答 / 关界面 / 时间到」三条路的唯一汇合点，
 * 改动会影响每一处询问，所以单独锁住。
 */
class PendingTest {

    private static Pending<String> bare() {
        // plugin / player 都为 null：不涉及 Bukkit 调度与界面
        return new Pending<>(null, null);
    }

    @Test
    void completesWithValue() {
        Pending<String> pending = bare();
        pending.complete("甲");
        assertTrue(pending.isDone());
        assertEquals("甲", pending.await(1, "兜底"));
    }

    @Test
    void cancelledIsFlaggedAndYieldsFallback() {
        Pending<String> pending = bare();
        pending.cancelNow();
        assertTrue(pending.isCancelled(), "关界面必须能被识别成「玩家取消」，而不是普通超时");
        assertEquals("兜底", pending.await(1, "兜底"));
    }

    @Test
    void cancelAfterResultDoesNotMarkCancelled() {
        Pending<String> pending = bare();
        pending.complete("乙");
        // 玩家点完按钮界面关闭，不能算取消
        pending.cancelNow();
        assertFalse(pending.isCancelled());
        assertEquals("乙", pending.await(1, "兜底"));
    }

    @Test
    void awaitReturnsFallbackOnTimeout() {
        Pending<String> pending = bare();
        long start = System.currentTimeMillis();
        assertEquals("兜底", pending.awaitMillis(120, "兜底"));
        long used = System.currentTimeMillis() - start;
        assertTrue(used >= 100, "应当真的等到截止时刻，实际只用了 " + used + "ms");
    }

    @Test
    void awaitReturnsFallbackWhenCompletedWithNull() {
        Pending<String> pending = bare();
        pending.complete(null);
        assertEquals("兜底", pending.await(1, "兜底"));
    }

    @Test
    void completeIsIdempotent() {
        Pending<String> pending = bare();
        pending.complete("第一次");
        pending.complete("第二次");
        assertEquals("第一次", pending.await(1, "兜底"));
    }

    @Test
    void onCompleteRunsOnlyOnce() {
        Pending<String> pending = bare();
        int[] runs = {0};
        pending.onComplete(() -> runs[0]++);
        pending.complete("x");
        pending.complete("y");
        assertEquals(1, runs[0]);
    }

    @Test
    void nullOwnerIsSafe() {
        Pending<String> pending = bare();
        assertNull(pending.owner(), "人机 / 无人的挂起操作没有 owner");
        // 关界面必须是空操作而不是 NPE
        pending.forceClose();
        pending.closeMenu();
    }
}
