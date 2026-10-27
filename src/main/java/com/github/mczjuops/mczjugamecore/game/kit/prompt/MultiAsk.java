package com.github.mczjuops.mczjugamecore.game.kit.prompt;

import com.github.mczjuops.mczjugamecore.game.kit.seat.Seat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 并发询问：同时向多个座位发问，统一截止，收齐后交给调用方决议。
 *
 * <p>语义（写死，改动前先想清楚）：
 * <ul>
 *   <li><b>同时</b>弹出所有问询 —— 不是问完 A 再问 B。否则慢玩家会拖死全场，
 *       而且先答的人等于先看到了别人的选择；</li>
 *   <li><b>同一个截止时刻</b>（{@code seconds} 秒），不是每人各自计时；</li>
 *   <li>返回的 {@code Map} 里<b>只包含真的回答了的座位</b>，未回答的不出现在结果中
 *       —— 由调用方决定「没回答 = 过」；</li>
 *   <li>优先级（日麻的 荣 &gt; 碰/杠 &gt; 吃）与合法性判断<b>不在这里</b>，是调用方的事。</li>
 * </ul>
 *
 * <p>日麻的声明窗口就靠它；SoulCard 的金/诚链是「串行 + 可反复反制」，不走这里。
 */
public final class MultiAsk {

    private MultiAsk() {
    }

    /**
     * @param seats    要被询问的座位（顺序即弹窗顺序，不影响优先级）
     * @param question 为每个座位创建挂起（内部通常调 {@code interaction.askXxx}）
     * @param seconds  统一截止秒数
     * @return 座位 → 答案；未回答的座位不在其中
     */
    public static <P extends Seat, T> Map<P, T> askAll(List<P> seats,
                                                       Function<P, Pending<T>> question,
                                                       int seconds) {
        Map<P, Pending<T>> opened = new LinkedHashMap<>();
        for (P seat : seats) {
            Pending<T> pending = question.apply(seat);
            if (pending != null) {
                opened.put(seat, pending);
            }
        }
        if (opened.isEmpty()) {
            return Map.of();
        }
        long deadline = System.currentTimeMillis() + Math.max(1, seconds) * 1000L;
        Map<P, T> answers = new LinkedHashMap<>();
        for (Map.Entry<P, Pending<T>> entry : opened.entrySet()) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                break;
            }
            T value = entry.getValue().awaitMillis(remaining, null);
            if (value != null) {
                answers.put(entry.getKey(), value);
            }
        }
        // 收齐后统一关界面，避免超时玩家的窗口一直挂着
        opened.values().forEach(Pending::forceClose);
        return answers;
    }
}
