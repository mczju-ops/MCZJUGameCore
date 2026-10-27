package com.github.mczjuops.mczjugamecore.game.kit.prompt;

import com.github.mczjuops.mczjugamecore.game.kit.menu.ChoiceOption;
import com.github.mczjuops.mczjugamecore.game.kit.seat.Seat;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 「等待 + 超时保底 + 取消处理」的共用实现，{@link Interaction} 的默认方法全部走这里。
 *
 * <p>超时替玩家做决定时会给真人发一句提示（人机没有 {@code Player}，自动跳过）。
 */
public final class Prompts {

    private Prompts() {
    }

    public static <P extends Seat> P awaitTarget(Pending<P> pending, List<P> candidates,
                                                 AskOptions options, Seat asker) {
        P value = pending.await(options.seconds(), null);
        if (pending.isCancelled()) {
            runCancel(options);
            return null;
        }
        if (value != null) {
            return value;
        }
        if (candidates.isEmpty()) {
            return null;
        }
        notice(asker);
        return switch (options.onTimeout()) {
            case RANDOM -> candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            case FIRST -> candidates.get(0);
            default -> null;
        };
    }

    public static <T> T awaitItem(Pending<T> pending, List<T> pool, AskOptions options, Seat asker) {
        T value = pending.await(options.seconds(), null);
        if (pending.isCancelled()) {
            runCancel(options);
            return null;
        }
        if (value != null) {
            return value;
        }
        if (pool.isEmpty()) {
            return null;
        }
        notice(asker);
        return switch (options.onTimeout()) {
            case RANDOM -> pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            case FIRST -> pool.get(0);
            default -> null;
        };
    }

    public static String awaitChoice(Pending<String> pending, List<ChoiceOption> options,
                                     AskOptions askOptions, Seat asker) {
        String value = pending.await(askOptions.seconds(), null);
        if (pending.isCancelled()) {
            runCancel(askOptions);
            return null;
        }
        if (value != null) {
            return value;
        }
        List<ChoiceOption> usable = options.stream().filter(ChoiceOption::enabled).toList();
        if (usable.isEmpty()) {
            return null;
        }
        notice(asker);
        return switch (askOptions.onTimeout()) {
            case FIRST -> usable.get(0).value();
            case RANDOM -> usable.get(ThreadLocalRandom.current().nextInt(usable.size())).value();
            default -> null;
        };
    }

    public static boolean awaitConfirm(Pending<Boolean> pending, AskOptions options, Seat asker) {
        Boolean value = pending.await(options.seconds(), null);
        if (pending.isCancelled()) {
            runCancel(options);
            return false;
        }
        if (value != null) {
            return value;
        }
        // 是 / 否问题超时一律按「否」：随机选「是」等于替玩家做了不可撤销的决定
        return false;
    }

    public static int awaitNumber(Pending<Integer> pending, int min, int max, AskOptions options,
                                  Seat asker) {
        Integer value = pending.await(options.seconds(), null);
        if (pending.isCancelled()) {
            runCancel(options);
            return min;
        }
        if (value != null) {
            return value;
        }
        notice(asker);
        return options.onTimeout() == AskOptions.TimeoutFallback.MAX ? max : min;
    }

    private static void runCancel(AskOptions options) {
        Runnable action = options.onCancel();
        if (action != null) {
            action.run();
        }
    }

    private static void notice(Seat asker) {
        if (asker == null || asker.isBot()) {
            return;
        }
        Player player = asker.player();
        if (player != null && player.isOnline()) {
            player.sendMessage(TextParser.parse("<yellow>超时，已替你做出选择"));
        }
    }
}
