package com.github.mczjuops.mczjugamecore.game.kit.prompt;

import com.github.mczjuops.mczjugamecore.game.kit.menu.ChoiceOption;
import com.github.mczjuops.mczjugamecore.game.kit.seat.Seat;

import java.util.List;

/**
 * 一次询问的内容快照，交给 {@link BotAnswerer} 决定人机怎么答。
 *
 * <p>人机拿不到 GUI，只能靠这里的信息决策；因此每种询问都把自己的候选放进来。
 */
public final class AskRequest<T> {

    public enum Kind {
        TARGET, ITEM, CHOICE, CONFIRM, NUMBER
    }

    private final Kind kind;
    private final Seat asker;
    private final String title;
    private final AskOptions options;
    private final List<? extends Seat> candidates;
    private final List<T> pool;
    private final List<ChoiceOption> choices;
    private final int min;
    private final int max;

    private AskRequest(Kind kind, Seat asker, String title, AskOptions options,
                       List<? extends Seat> candidates, List<T> pool,
                       List<ChoiceOption> choices, int min, int max) {
        this.kind = kind;
        this.asker = asker;
        this.title = title;
        this.options = options;
        this.candidates = candidates;
        this.pool = pool;
        this.choices = choices;
        this.min = min;
        this.max = max;
    }

    static <T> AskRequest<T> target(Seat asker, String title, AskOptions options,
                                    List<? extends Seat> candidates) {
        return new AskRequest<>(Kind.TARGET, asker, title, options, candidates, null, null, 0, 0);
    }

    static <T> AskRequest<T> item(Seat asker, String title, AskOptions options, List<T> pool) {
        return new AskRequest<>(Kind.ITEM, asker, title, options, null, pool, null, 0, 0);
    }

    static <T> AskRequest<T> choice(Seat asker, String title, AskOptions options,
                                    List<ChoiceOption> choices) {
        return new AskRequest<>(Kind.CHOICE, asker, title, options, null, null, choices, 0, 0);
    }

    static <T> AskRequest<T> confirm(Seat asker, String title, AskOptions options) {
        return new AskRequest<>(Kind.CONFIRM, asker, title, options, null, null, null, 0, 0);
    }

    static <T> AskRequest<T> number(Seat asker, String title, AskOptions options, int min, int max) {
        return new AskRequest<>(Kind.NUMBER, asker, title, options, null, null, null, min, max);
    }

    public Kind kind() {
        return kind;
    }

    public Seat asker() {
        return asker;
    }

    public String title() {
        return title;
    }

    public AskOptions options() {
        return options;
    }

    /** {@link Kind#TARGET} 时的候选玩家。 */
    public List<? extends Seat> candidates() {
        return candidates;
    }

    /** {@link Kind#ITEM} 时的候选物品。 */
    public List<T> pool() {
        return pool;
    }

    /** {@link Kind#CHOICE} 时的候选选项。 */
    public List<ChoiceOption> choices() {
        return choices;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }
}
