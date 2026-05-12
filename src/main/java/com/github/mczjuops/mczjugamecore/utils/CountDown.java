package com.github.mczjuops.mczjugamecore.utils;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * 轻量单次倒计时工具（无全局管理器，如有需求，自行持有句柄）
 *
 * <p>通过 Builder 注入回调。
 *
 * <pre>{@code
 * // 示例：创建一个 10 秒倒计时
 * CountDown countDown = new CountDown.Builder(plugin, 10) // plugin 是插件主类实例
 *     .onTick((s, cd) -> {
 *         // 在还剩 s 秒时需要做什么，例如给玩家显示倒计时提示、更新进度条（如果有）
 *         String message = "<green>游戏将在%d秒后开始".formatted(s);
 *         game.sender().info(TextParser.parse(message));
 *         if (shouldCancel()) {
 *             cd.cancel(); // 示例：满足特定条件时 cancel()
 *         }
 *     })
 *     .onComplete(() -> {
 *         getPlayers().forEach(playerExt -> {
 *             Player player = playerExt.player();
 *             player.showTitle(Title.title(TextParser.parse("<gold>游戏开始！"), Component.empty()));
 *             player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
 *         });
 *         // 更多逻辑：让游戏正式开始
 *     })
 *     .onCancel(() -> {
 *         // （如有需要）具体逻辑，例如重置游戏房间等
 *     })
 *     .buildAndStart(); // 或者先 build()，用到了再 start()
 *
 * // 需要时可手动取消或查询：
 * countDown.cancel();
 * countDown.getSecondsLeft();
 * countDown.getProgress();
 * }</pre>
 */
public class CountDown {

    private final JavaPlugin plugin;
    private final int totalSeconds;

    private int secondsLeft;
    private CountDownState state = CountDownState.IDLE;
    private BukkitTask task;
    private final TickCallback onTick; // 每秒回调，参数为当前剩余秒数
    private final Runnable onComplete; // 结束时的回调
    private final Runnable onCancel; // 被取消后的回调

    private CountDown(Builder builder) {
        this.plugin = builder.plugin;
        this.totalSeconds = builder.totalSeconds;
        this.secondsLeft = builder.totalSeconds;
        this.onTick = builder.onTick;
        this.onComplete = builder.onComplete;
        this.onCancel = builder.onCancel;
    }

    public void start() {
        if (state == CountDownState.RUNNING) return;
        if (state != CountDownState.IDLE) {
            throw new IllegalStateException("CountDown 已结束（" + state + "），请调用 restart() 重新使用。");
        }
        doStart();
    }

    /**
     * 无论当前处于何种状态，重置并重新启动倒计时。
     * 适用于小游戏重开、重新准备阶段等场景。
     */
    public void restart() {
        if (state == CountDownState.RUNNING && task != null && !task.isCancelled()) {
            task.cancel();
        }
        secondsLeft = totalSeconds;
        state = CountDownState.IDLE;
        doStart();
    }

    /**
     * 取消倒计时，并触发 {@code onCancel} 回调（若已注册）。
     * 若倒计时当前不在运行，则无任何效果。
     */
    public void cancel() {
        if (state != CountDownState.RUNNING) return;
        state = CountDownState.CANCELLED;
        if (task != null && !task.isCancelled()) task.cancel();
        if (onCancel != null) onCancel.run();
    }

    /** @return 剩余秒数 */
    public int getSecondsLeft() { return secondsLeft; }

    /** @return 总秒数（构造时传入的值） */
    public int getTotalSeconds() { return totalSeconds; }

    /** @return 已经过的秒数 */
    public int getSecondsElapsed() { return totalSeconds - secondsLeft; }

    /** @return 进度值 [0.0, 1.0]，0.0 为刚开始，1.0 为结束 */
    public float getProgress() {
        if (totalSeconds <= 0) return 1f;
        return (float) (totalSeconds - secondsLeft) / totalSeconds;
    }

    /** @return 是否正在倒计时 */
    public boolean isRunning() { return state == CountDownState.RUNNING; }

    private void doStart() {
        state = CountDownState.RUNNING;

        // 边界：时长为 0 直接视为完成
        if (totalSeconds <= 0) {
            state = CountDownState.COMPLETED;
            if (onComplete != null) onComplete.run();
            return;
        }

        task = new BukkitRunnable() {
            @Override
            public void run() {
                // 剩余为 0：倒计时自然结束
                if (secondsLeft <= 0) {
                    state = CountDownState.COMPLETED;
                    cancel(); // 取消 Bukkit 任务
                    if (onComplete != null) onComplete.run();
                    return;
                }
                if (onTick != null) onTick.onTick(secondsLeft, CountDown.this);
                secondsLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public static Builder builder(JavaPlugin plugin, int totalSeconds) {
        return new Builder(plugin, totalSeconds);
    }

    public static final class Builder {

        private final JavaPlugin plugin;
        private final int totalSeconds;

        private TickCallback onTick;
        private Runnable onComplete;
        private Runnable onCancel;

        /**
         * @param plugin       插件实例
         * @param totalSeconds 倒计时总秒数（>= 0；为 0 时立即触发完成回调）
         */
        public Builder(JavaPlugin plugin, int totalSeconds) {
            if (plugin == null) throw new IllegalArgumentException("plugin 不能为 null");
            if (totalSeconds < 0) throw new IllegalArgumentException("totalSeconds 不能为负数");
            this.plugin = plugin;
            this.totalSeconds = totalSeconds;
        }

        /**
         * 注册每秒 tick 回调。
         *
         * @param onTick 参数为当前剩余秒数，范围 [totalSeconds, 1]
         */
        public Builder onTick(TickCallback onTick) {
            this.onTick = onTick;
            return this;
        }

        /** 注册倒计时自然归零后的回调。 */
        public Builder onComplete(Runnable onComplete) {
            this.onComplete = onComplete;
            return this;
        }

        /** 注册倒计时被手动取消后的回调。 */
        public Builder onCancel(Runnable onCancel) {
            this.onCancel = onCancel;
            return this;
        }

        /** 仅构建，不启动。需手动调用 {@link CountDown#start()}。 */
        public CountDown build() {
            return new CountDown(this);
        }

        /** 构建并立即启动，返回句柄。 */
        public CountDown buildAndStart() {
            CountDown cd = new CountDown(this);
            cd.start();
            return cd;
        }
    }

    // 设计它是为了让 onTick 也能拿到句柄
    @FunctionalInterface
    public interface TickCallback {
        /**
         * @param secondsLeft 当前剩余秒数
         * @param countDown   倒计时本身的句柄，可直接调用 cancel() 等方法
         */
        void onTick(int secondsLeft, CountDown countDown);
    }

    public enum CountDownState {
        IDLE, // 未启动
        RUNNING, // 正在倒计时
        COMPLETED, // 倒计时结束
        CANCELLED // 倒计时被取消
    }
}