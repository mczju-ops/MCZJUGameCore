package com.github.mczjuops.mczjugamecore.game.kit.prompt;

import java.util.Map;

/**
 * 一次询问的全部参数。
 *
 * <p>把「超时保底什么」「取消怎么办」「要不要显示倒计时」从每个调用点收拢到这里，
 * 这样同一款游戏里的几十处询问不必各写一遍。
 */
public final class AskOptions {

    /** 超时保底策略。 */
    public enum TimeoutFallback {
        /** 从候选里随机取一个（SoulCard 的选择目标 / 选牌）。 */
        RANDOM,
        /** 取第一个候选（日麻的声明窗口取「过」）。 */
        FIRST,
        /** 数字询问取下界。 */
        MIN,
        /** 数字询问取上界。 */
        MAX,
        /** 不给保底（返回 {@code null} / {@code false}），由调用方决定怎么办。 */
        REJECT
    }

    /** 物品聚合方式。 */
    public enum GroupMode {
        /** 每张牌一个按钮（SoulCard 现状）。 */
        NONE,
        /** 同一种牌合并成一个按钮并显示张数（日麻暗杠要选「东」而不是某张东）。 */
        BY_KEY
    }

    private final String title;
    private final int seconds;
    private final String yesLabel;
    private final String noLabel;
    private final String hint;
    private final Map<String, String> notes;
    private final TimeoutFallback onTimeout;
    private final GroupMode groupMode;
    private final boolean showPromptTimer;
    private final Runnable onCancel;

    private AskOptions(Builder b) {
        this.title = b.title;
        this.seconds = b.seconds;
        this.yesLabel = b.yesLabel;
        this.noLabel = b.noLabel;
        this.hint = b.hint;
        this.notes = b.notes == null ? Map.of() : Map.copyOf(b.notes);
        this.onTimeout = b.onTimeout;
        this.groupMode = b.groupMode;
        this.showPromptTimer = b.showPromptTimer;
        this.onCancel = b.onCancel;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 大多数询问都用这套：随机保底、取消交给游戏自己处理、显示提示倒计时。 */
    public static Builder standard(String title, int seconds) {
        return builder().title(title).seconds(seconds);
    }

    public String title() {
        return title;
    }

    public int seconds() {
        return seconds;
    }

    public String yesLabel() {
        return yesLabel;
    }

    public String noLabel() {
        return noLabel;
    }

    public String hint() {
        return hint;
    }

    /** 按物品 {@code key} 索引的补充说明行（MiniMessage）。 */
    public Map<String, String> notes() {
        return notes;
    }

    public TimeoutFallback onTimeout() {
        return onTimeout;
    }

    public GroupMode groupMode() {
        return groupMode;
    }

    public boolean showPromptTimer() {
        return showPromptTimer;
    }

    /**
     * 玩家主动取消时执行的动作。
     *
     * <p>通常是抛游戏自己的「取消出牌」异常；返回 {@code null} 表示不由 kit 处理。
     * 允许抛受检以外的异常（{@code Runnable#run} 不声明 throws，抛 {@code RuntimeException} 即可）。
     */
    public Runnable onCancel() {
        return onCancel;
    }

    public static final class Builder {
        private String title = "请选择";
        private int seconds = 10;
        private String yesLabel = "确定";
        private String noLabel = "取消";
        private String hint;
        private Map<String, String> notes;
        private TimeoutFallback onTimeout = TimeoutFallback.RANDOM;
        private GroupMode groupMode = GroupMode.NONE;
        private boolean showPromptTimer = true;
        private Runnable onCancel;

        public Builder title(String value) {
            this.title = value;
            return this;
        }

        public Builder seconds(int value) {
            this.seconds = value;
            return this;
        }

        public Builder labels(String yes, String no) {
            this.yesLabel = yes;
            this.noLabel = no;
            return this;
        }

        public Builder hint(String value) {
            this.hint = value;
            return this;
        }

        public Builder notes(Map<String, String> value) {
            this.notes = value;
            return this;
        }

        public Builder onTimeout(TimeoutFallback value) {
            this.onTimeout = value;
            return this;
        }

        public Builder groupMode(GroupMode value) {
            this.groupMode = value;
            return this;
        }

        public Builder showPromptTimer(boolean value) {
            this.showPromptTimer = value;
            return this;
        }

        public Builder onCancel(Runnable value) {
            this.onCancel = value;
            return this;
        }

        public AskOptions build() {
            return new AskOptions(this);
        }
    }
}
