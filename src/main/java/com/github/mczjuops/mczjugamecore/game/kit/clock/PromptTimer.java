package com.github.mczjuops.mczjugamecore.game.kit.clock;

import com.github.mczjuops.mczjugamecore.game.kit.prompt.Pending;
import com.github.mczjuops.mczjugamecore.game.kit.seat.Seat;
import com.github.mczjuops.mczjugamecore.game.kit.thread.GameThread;
import com.github.mczjuops.mczjugamecore.game.timer.TurnTimer;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * 提示倒计时：给「被询问的其他玩家」在屏幕上走一个 BossBar。
 *
 * <p>当前回合的玩家自己的主时钟已经在走，不再叠加；
 * 挂上的倒计时会随 {@link Pending} 结束（作答 / 超时 / 取消）自动取消，
 * 对局结束时用 {@link #cancelAll()} 统一清掉，避免倒计时残留在屏幕上。
 */
public final class PromptTimer {

    private final Plugin plugin;
    private final GameThread thread;
    private final List<TurnTimer> running = new CopyOnWriteArrayList<>();
    private Supplier<? extends Seat> currentTurn = () -> null;

    public PromptTimer(Plugin plugin, GameThread thread) {
        this.plugin = plugin;
        this.thread = thread;
    }

    /** 绑定「当前正在行动的人」，用于跳过他自己。 */
    public void bindCurrentTurn(Supplier<? extends Seat> supplier) {
        if (supplier != null) {
            this.currentTurn = supplier;
        }
    }

    /** 给 {@code asker} 挂一个倒计时，随 {@code pending} 结束自动取消。 */
    public void attach(Seat asker, int seconds, Pending<?> pending) {
        if (asker == null || pending == null || seconds <= 0 || thread == null) {
            return;
        }
        if (plugin == null || !plugin.isEnabled() || asker.isBot() || !asker.isOnline()) {
            return;
        }
        Seat current = currentTurn.get();
        if (current != null && current.equals(asker)) {
            return;
        }
        TurnTimer timer;
        try {
            timer = thread.call(() -> TurnTimer.builder(seconds)
                    .displayTo(asker.player(), TurnTimer.DisplayMode.BOSS_BAR)
                    .build());
            thread.run(timer::start);
        } catch (RuntimeException e) {
            // 对局已中止 / 玩家掉线：倒计时不是必需品，静默跳过
            return;
        }
        running.add(timer);
        pending.onComplete(() -> {
            running.remove(timer);
            GameThread.runOnMainSafely(plugin, timer::cancel);
        });
    }

    /** 取消所有仍在运行的提示倒计时。 */
    public void cancelAll() {
        List<TurnTimer> left = new ArrayList<>(running);
        running.clear();
        if (left.isEmpty()) {
            return;
        }
        GameThread.runOnMainSafely(plugin, () -> left.forEach(TurnTimer::cancel));
    }
}
