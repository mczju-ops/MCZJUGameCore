package com.github.mczjuops.mczjugamecore.game.timer;

/**
 * 纯倒计时状态机，不依赖 Bukkit，供 {@link TurnTimer} 使用并便于单元测试。
 *
 * <p>状态流转：IDLE -start-&gt; RUNNING -tick 归零-&gt; COMPLETED；
 * RUNNING -pause-&gt; PAUSED -resume-&gt; RUNNING；
 * RUNNING/PAUSED -cancel-&gt; CANCELLED；restart/reset 回到 IDLE。
 */
public final class CountdownEngine {

    public enum State {
        IDLE,       // 未启动
        RUNNING,    // 正在倒计时
        PAUSED,     // 已暂停
        COMPLETED,  // 自然归零
        CANCELLED   // 被取消
    }

    private final int totalSeconds;
    private int secondsLeft;
    private State state = State.IDLE;

    /**
     * @param totalSeconds 倒计时总秒数（&gt;= 0）
     */
    public CountdownEngine(int totalSeconds) {
        if (totalSeconds < 0) {
            throw new IllegalArgumentException("totalSeconds 不能为负数");
        }
        this.totalSeconds = totalSeconds;
        this.secondsLeft = totalSeconds;
    }

    public int getTotalSeconds() {
        return totalSeconds;
    }

    public int getSecondsLeft() {
        return secondsLeft;
    }

    public State getState() {
        return state;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    public boolean isPaused() {
        return state == State.PAUSED;
    }

    /** @return 已经过秒数 */
    public int getSecondsElapsed() {
        return totalSeconds - secondsLeft;
    }

    /** @return 进度值 [0.0, 1.0]，1.0 表示已经结束 */
    public float getProgress() {
        if (totalSeconds <= 0) {
            return 1f;
        }
        return (float) (totalSeconds - secondsLeft) / totalSeconds;
    }

    /** 启动倒计时。仅 IDLE 状态可启动。 */
    public void start() {
        if (state == State.RUNNING) {
            return;
        }
        if (state != State.IDLE) {
            throw new IllegalStateException("倒计时已结束（" + state + "），请调用 restart() 重新使用");
        }
        state = State.RUNNING;
    }

    /** 暂停。仅 RUNNING 状态生效。 */
    public void pause() {
        if (state != State.RUNNING) {
            return;
        }
        state = State.PAUSED;
    }

    /** 恢复。仅 PAUSED 状态生效。 */
    public void resume() {
        if (state != State.PAUSED) {
            return;
        }
        state = State.RUNNING;
    }

    /** 无论当前处于何种状态，重置并重新启动倒计时。 */
    public void restart() {
        secondsLeft = totalSeconds;
        state = State.IDLE;
        start();
    }

    /** 重置为初始时长并回到 IDLE（需要再次 {@link #start()}）。 */
    public void reset() {
        secondsLeft = totalSeconds;
        state = State.IDLE;
    }

    /** 取消倒计时。仅 RUNNING 或 PAUSED 状态生效。 */
    public void cancel() {
        if (state != State.RUNNING && state != State.PAUSED) {
            return;
        }
        state = State.CANCELLED;
    }

    /**
     * 推进一秒。
     *
     * @return true 表示剩余时间归零并已进入 COMPLETED；false 表示正常递减了一步。
     */
    public boolean tick() {
        if (state != State.RUNNING) {
            return false;
        }
        if (secondsLeft <= 0) {
            state = State.COMPLETED;
            return true;
        }
        secondsLeft--;
        if (secondsLeft <= 0) {
            secondsLeft = 0;
            state = State.COMPLETED;
            return true;
        }
        return false;
    }
}
