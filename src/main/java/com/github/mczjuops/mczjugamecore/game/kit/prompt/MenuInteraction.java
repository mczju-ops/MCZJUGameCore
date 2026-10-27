package com.github.mczjuops.mczjugamecore.game.kit.prompt;

import com.github.mczjuops.mczjugamecore.game.kit.clock.PromptTimer;
import com.github.mczjuops.mczjugamecore.game.kit.hand.HandItemCodec;
import com.github.mczjuops.mczjugamecore.game.kit.menu.ChoiceMenu;
import com.github.mczjuops.mczjugamecore.game.kit.menu.ChoiceOption;
import com.github.mczjuops.mczjugamecore.game.kit.menu.ConfirmMenu;
import com.github.mczjuops.mczjugamecore.game.kit.menu.ItemPickMenu;
import com.github.mczjuops.mczjugamecore.game.kit.menu.NumberMenu;
import com.github.mczjuops.mczjugamecore.game.kit.menu.SeatPickMenu;
import com.github.mczjuops.mczjugamecore.game.kit.seat.Seat;
import com.github.mczjuops.mczjugamecore.game.kit.thread.GameThread;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link Interaction} 的默认实现：用 kit 的菜单渲染 GUI。
 *
 * <p>人机的询问在**创建挂起时**就由 {@link BotAnswerer} 答完并立即返回已完成的挂起，
 * 因此上层既可以用 {@code askXxxSync}（自己等），也可以用 {@link MultiAsk}（并发收集）。
 */
public final class MenuInteraction<T> implements Interaction<T> {

    private final Plugin plugin;
    private final @Nullable GameThread thread;
    private final HandItemCodec<T> codec;
    private @Nullable PromptTimer promptTimer;
    private @Nullable BotAnswerer botAnswerer;

    public MenuInteraction(Plugin plugin, @Nullable GameThread thread, HandItemCodec<T> codec) {
        this.plugin = plugin;
        this.thread = thread;
        this.codec = codec;
    }

    public MenuInteraction<T> withPromptTimer(PromptTimer timer) {
        this.promptTimer = timer;
        return this;
    }

    @Override
    public void setBotAnswerer(BotAnswerer answerer) {
        this.botAnswerer = answerer;
    }

    @Override
    public void cancelPromptTimers() {
        if (promptTimer != null) {
            promptTimer.cancelAll();
        }
    }

    // ==================================================================
    //  低层：创建挂起
    // ==================================================================

    @Override
    public <P extends Seat> Pending<P> askTarget(P asker, List<P> candidates, AskOptions options) {
        AskOptions o = options == null ? AskOptions.builder().build() : options;
        Pending<P> pending = new Pending<>(plugin, playerOf(asker));
        if (asker.isBot()) {
            pending.complete(askBotAnswer(AskRequest.<P>target(asker, o.title(), o, candidates)));
            return pending;
        }
        if (candidates.isEmpty() || !asker.isActive()) {
            pending.complete(null);
            return pending;
        }
        pending.withTimeout(o.seconds());
        attachTimer(asker, o, pending);
        @SuppressWarnings("unchecked")
        Pending<Seat> asSeat = (Pending<Seat>) (Pending<?>) pending;
        runOnMain(() -> new SeatPickMenu(asker.player(), o.title(),
                new ArrayList<>(candidates), asSeat).show());
        return pending;
    }

    @Override
    public Pending<T> askItem(Seat asker, List<T> pool, AskOptions options) {
        AskOptions o = options == null ? AskOptions.builder().build() : options;
        Pending<T> pending = new Pending<>(plugin, playerOf(asker));
        if (asker.isBot()) {
            pending.complete(askBotAnswer(AskRequest.item(asker, o.title(), o, pool)));
            return pending;
        }
        if (pool.isEmpty() || !asker.isActive()) {
            pending.complete(null);
            return pending;
        }
        pending.withTimeout(o.seconds());
        attachTimer(asker, o, pending);
        runOnMain(() -> new ItemPickMenu<>(asker.player(), o.title(),
                new ArrayList<>(pool), codec, o, pending).show());
        return pending;
    }

    @Override
    public Pending<String> askChoice(Seat asker, List<ChoiceOption> options, AskOptions options2) {
        AskOptions o = options2 == null ? AskOptions.builder().build() : options2;
        Pending<String> pending = new Pending<>(plugin, playerOf(asker));
        if (asker.isBot()) {
            pending.complete(askBotAnswer(AskRequest.<String>choice(asker, o.title(), o, options)));
            return pending;
        }
        if (options.isEmpty() || !asker.isActive()) {
            pending.complete(null);
            return pending;
        }
        pending.withTimeout(o.seconds());
        attachTimer(asker, o, pending);
        runOnMain(() -> new ChoiceMenu(asker.player(), o.title(),
                new ArrayList<>(options), o.hint(), pending).show());
        return pending;
    }

    @Override
    public Pending<Boolean> askConfirm(Seat asker, String description, List<String> details,
                                       AskOptions options) {
        AskOptions o = options == null ? AskOptions.builder().build() : options;
        Pending<Boolean> pending = new Pending<>(plugin, playerOf(asker));
        if (asker.isBot()) {
            pending.complete(askBotAnswer(AskRequest.<Boolean>confirm(asker, o.title(), o)));
            return pending;
        }
        if (!asker.isActive()) {
            pending.complete(Boolean.FALSE);
            return pending;
        }
        pending.withTimeout(o.seconds());
        attachTimer(asker, o, pending);
        runOnMain(() -> new ConfirmMenu(asker.player(), o.title(), description,
                details == null ? List.of() : details,
                o.yesLabel(), o.noLabel(), pending).show());
        return pending;
    }

    @Override
    public Pending<Integer> askNumber(Seat asker, int min, int max, AskOptions options) {
        AskOptions o = options == null ? AskOptions.builder().build() : options;
        Pending<Integer> pending = new Pending<>(plugin, playerOf(asker));
        if (asker.isBot()) {
            pending.complete(askBotAnswer(AskRequest.<Integer>number(asker, o.title(), o, min, max)));
            return pending;
        }
        if (!asker.isActive()) {
            pending.complete(min);
            return pending;
        }
        pending.withTimeout(o.seconds());
        attachTimer(asker, o, pending);
        runOnMain(() -> new NumberMenu(asker.player(), o.title(), min, max, o.hint(), pending).show());
        return pending;
    }

    // ==================================================================
    //  内部
    // ==================================================================

    private void attachTimer(Seat asker, AskOptions options, Pending<?> pending) {
        if (promptTimer != null && options.showPromptTimer()) {
            promptTimer.attach(asker, options.seconds(), pending);
        }
    }

    /** 取消处理：默认交给 {@link AskOptions#onCancel()}（通常直接抛游戏的取消异常）。 */
    private void runCancel(AskOptions options) {
        Runnable action = options.onCancel();
        if (action != null) {
            action.run();
        }
    }

    private void runOnMain(Runnable action) {
        if (thread != null) {
            try {
                thread.run(action);
                return;
            } catch (RuntimeException e) {
                // 对局已中止：不要再弹界面
                return;
            }
        }
        GameThread.runOnMainSafely(plugin, action);
    }

    private static @Nullable Player playerOf(Seat seat) {
        return seat == null ? null : seat.player();
    }

    private <R> R askBotAnswer(AskRequest<R> request) {
        BotAnswerer answerer = botAnswerer;
        if (answerer == null) {
            return null;
        }
        try {
            return answerer.answer(request);
        } catch (RuntimeException e) {
            // 人机决策出错不能带崩整局
            plugin.getLogger().warning("[kit] 人机决策失败：" + e);
            return null;
        }
    }
}
