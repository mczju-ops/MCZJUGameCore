package com.github.mczjuops.mczjugamecore.game.kit.prompt;

import com.github.mczjuops.mczjugamecore.game.kit.menu.ChoiceOption;
import com.github.mczjuops.mczjugamecore.game.kit.seat.Seat;

import java.util.List;

/**
 * 玩家交互入口。
 *
 * <p>低层方法只**创建挂起**（供 {@link MultiAsk} 并发使用）；
 * 高层 {@code xxxSync} 方法把「等待 → 超时保底 → 取消处理」做完，游戏侧调用点通常只需要一行。
 * 人机在创建挂起时就已经答完（见 {@link BotAnswerer}），所以两条路都能直接用于人机。
 *
 * <p>所有方法都必须在游戏线程调用；内部会把 GUI 操作编组到主线程。
 */
public interface Interaction<T> {

    // ---- 低层：创建挂起 ----

    /** 选择一名玩家目标。 */
    <P extends Seat> Pending<P> askTarget(P asker, List<P> candidates, AskOptions options);

    /** 从一组物品中选一个（手牌 / 弃牌堆 / 牌堆顶 / 别人的牌）。 */
    Pending<T> askItem(Seat asker, List<T> pool, AskOptions options);

    /** 从选项里选一个，返回 {@link ChoiceOption#value()}。 */
    Pending<String> askChoice(Seat asker, List<ChoiceOption> choices, AskOptions options);

    Pending<Boolean> askConfirm(Seat asker, String description, List<String> details,
                                AskOptions options);

    Pending<Integer> askNumber(Seat asker, int min, int max, AskOptions options);

    // ---- 高层：一路到底 ----

    /** @return 选中的目标；取消且未配置 {@code onCancel} 时返回 {@code null} */
    default <P extends Seat> P askTargetSync(P asker, List<P> candidates, AskOptions options) {
        AskOptions o = options == null ? AskOptions.builder().build() : options;
        return Prompts.awaitTarget(askTarget(asker, candidates, o), candidates, o, asker);
    }

    default T askItemSync(Seat asker, List<T> pool, AskOptions options) {
        AskOptions o = options == null ? AskOptions.builder().build() : options;
        return Prompts.awaitItem(askItem(asker, pool, o), pool, o, asker);
    }

    default String askChoiceSync(Seat asker, List<ChoiceOption> choices, AskOptions options) {
        AskOptions o = options == null ? AskOptions.builder().build() : options;
        return Prompts.awaitChoice(askChoice(asker, choices, o), choices, o, asker);
    }

    default boolean askConfirmSync(Seat asker, String description, List<String> details,
                                   AskOptions options) {
        AskOptions o = options == null ? AskOptions.builder().build() : options;
        return Prompts.awaitConfirm(askConfirm(asker, description, details, o), o, asker);
    }

    default int askNumberSync(Seat asker, int min, int max, AskOptions options) {
        AskOptions o = options == null ? AskOptions.builder().build() : options;
        return Prompts.awaitNumber(askNumber(asker, min, max, o), min, max, o, asker);
    }

    // ---- 配置 ----

    /** 注册人机回答策略。默认什么都不做（纯真人局的假实现不必重写）。 */
    default void setBotAnswerer(BotAnswerer answerer) {
    }

    /** 取消所有仍在运行的提示倒计时（对局结束时调用）。 */
    default void cancelPromptTimers() {
    }
}
