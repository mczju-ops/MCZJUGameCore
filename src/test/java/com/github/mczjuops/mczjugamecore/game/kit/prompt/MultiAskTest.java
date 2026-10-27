package com.github.mczjuops.mczjugamecore.game.kit.prompt;

import com.github.mczjuops.mczjugamecore.game.kit.support.FakeSeat;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MultiAsk} 的契约：同时发问、统一截止、只返回真的回答了的座位。
 */
class MultiAskTest {

    private static Pending<String> answered(String value) {
        Pending<String> pending = new Pending<>(null, null);
        pending.complete(value);
        return pending;
    }

    private static Pending<String> silent() {
        // 永远不 complete：模拟「没来得及作答」
        return new Pending<>(null, null);
    }

    @Test
    void collectsAnswersAndOmitsSilentSeats() {
        FakeSeat a = new FakeSeat("甲", true);
        FakeSeat b = new FakeSeat("乙", true);
        FakeSeat c = new FakeSeat("丙", true);

        long start = System.currentTimeMillis();
        Map<FakeSeat, String> answers = MultiAsk.askAll(List.of(a, b, c), seat -> switch (seat.name()) {
            case "甲" -> answered("荣");
            case "乙" -> answered("过");
            default -> silent();
        }, 1);
        long used = System.currentTimeMillis() - start;

        assertNotNull(answers);
        assertEquals("荣", answers.get(a));
        assertEquals("过", answers.get(b));
        assertFalse(answers.containsKey(c), "没作答的座位不能出现在结果里，由调用方当成「过」");
        assertEquals(2, answers.size());
        // 统一截止：丙没答，整体最多等 1 秒左右
        assertTrue(used < 2500, "整体等待不应超过截止时间太多，实际 " + used + "ms");
    }

    @Test
    void allAnsweredReturnsImmediately() {
        FakeSeat a = new FakeSeat("甲", true);
        FakeSeat b = new FakeSeat("乙", true);
        long start = System.currentTimeMillis();
        Map<FakeSeat, String> answers = MultiAsk.askAll(List.of(a, b),
                seat -> answered("过"), 5);
        long used = System.currentTimeMillis() - start;
        assertEquals(2, answers.size());
        assertTrue(used < 1000, "所有人都答完就不该再等剩余时间，实际 " + used + "ms");
    }

    @Test
    void emptySeatsYieldsEmptyResult() {
        assertTrue(MultiAsk.askAll(List.<FakeSeat>of(), seat -> answered("过"), 1).isEmpty());
    }

    @Test
    void nullPendingIsIgnored() {
        FakeSeat a = new FakeSeat("甲", true);
        Map<FakeSeat, String> answers = MultiAsk.askAll(List.of(a), seat -> null, 1);
        assertTrue(answers.isEmpty());
    }

    @Test
    void nullAnswerCountsAsNoAnswer() {
        FakeSeat a = new FakeSeat("甲", true);
        Pending<String> nullAnswer = new Pending<>(null, null);
        nullAnswer.complete(null);
        Map<FakeSeat, String> answers = MultiAsk.askAll(List.of(a), seat -> nullAnswer, 1);
        assertNull(answers.get(a), "交付 null 等于没给答案");
        assertTrue(answers.isEmpty());
    }
}
