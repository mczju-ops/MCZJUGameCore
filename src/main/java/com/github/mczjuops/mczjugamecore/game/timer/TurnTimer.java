package com.github.mczjuops.mczjugamecore.game.timer;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import com.github.mczjuops.mczjugamecore.utils.TimeFormat;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 回合/行动计时器：轮到某个玩家时倒计时，超时自动触发 {@code onComplete}（用于自动跳过/托管）。
 *
 * <p>支持 {@link #pause()}/{@link #resume()}/{@link #restart()}/{@link #reset()}/{@link #cancel()}，
 * 并提供 ActionBar / BossBar / 经验条三种可视化展示，可指定展示给单个玩家或全房间。
 *
 * <pre>{@code
 * TurnTimer timer = TurnTimer.builder(20)
 *     .displayTo(currentPlayer, TurnTimer.DisplayMode.BOSS_BAR)
 *     .onTick((left, t) -> { })   // 每秒回调
 *     .onComplete(() -> { })      // 超时：自动跳过该玩家
 *     .onCancel(() -> { })        // 被取消：如玩家已行动
 *     .buildAndStart();
 * }</pre>
 */
public class TurnTimer {

    public enum DisplayMode {
        NONE,
        ACTION_BAR,
        BOSS_BAR,
        XP
    }

    private final int totalSeconds;
    private final CountdownEngine engine;
    private final TickCallback onTick;
    private final Runnable onComplete;
    private final Runnable onCancel;

    private final Map<UUID, DisplayMode> viewers = new LinkedHashMap<>();
    private BossBar bossBar;
    private final Map<UUID, float[]> xpSnapshot = new HashMap<>();

    private BukkitTask task;
    private boolean terminal;

    private TurnTimer(Builder builder) {
        this.totalSeconds = builder.totalSeconds;
        this.engine = new CountdownEngine(builder.totalSeconds);
        this.onTick = builder.onTick;
        this.onComplete = builder.onComplete;
        this.onCancel = builder.onCancel;
        builder.viewers.forEach((player, mode) -> viewers.put(player.getUniqueId(), mode));
    }

    public static Builder builder(int totalSeconds) {
        return new Builder(totalSeconds);
    }

    /** 启动倒计时。已运行时忽略；已结束时抛出异常，请改用 {@link #restart()}。 */
    public void start() {
        if (engine.isRunning()) {
            return;
        }
        if (engine.getState() != CountdownEngine.State.IDLE) {
            throw new IllegalStateException("倒计时已结束（" + engine.getState() + "），请调用 restart() 重新使用");
        }
        engine.start();
        if (totalSeconds <= 0) {
            engine.tick();
            complete();
            return;
        }
        terminal = false;
        beginDisplay();
        updateDisplay();
        if (onTick != null) {
            onTick.onTick(engine.getSecondsLeft(), this);
        }
        schedule();
    }

    /** 暂停。剩余时间保留。 */
    public void pause() {
        if (!engine.isRunning()) {
            return;
        }
        engine.pause();
        stopTask();
    }

    /** 从暂停处恢复。 */
    public void resume() {
        if (!engine.isPaused()) {
            return;
        }
        engine.resume();
        schedule();
    }

    /** 重置并立即重新开始。 */
    public void restart() {
        stopTask();
        engine.restart();
        if (totalSeconds <= 0) {
            engine.tick();
            complete();
            return;
        }
        terminal = false;
        beginDisplay();
        updateDisplay();
        if (onTick != null) {
            onTick.onTick(engine.getSecondsLeft(), this);
        }
        schedule();
    }

    /** 重置为初始时长并回到未启动状态，同时清理可视化展示。 */
    public void reset() {
        stopTask();
        engine.reset();
        terminal = false;
        cleanupDisplay();
    }

    /** 取消倒计时，触发 {@code onCancel}（若已注册）。 */
    public void cancel() {
        if (engine.getState() != CountdownEngine.State.RUNNING && engine.getState() != CountdownEngine.State.PAUSED) {
            return;
        }
        engine.cancel();
        if (terminal) {
            return;
        }
        terminal = true;
        stopTask();
        cleanupDisplay();
        if (onCancel != null) {
            onCancel.run();
        }
    }

    /** @return 剩余秒数 */
    public int getSecondsLeft() {
        return engine.getSecondsLeft();
    }

    /** @return 总秒数 */
    public int getTotalSeconds() {
        return engine.getTotalSeconds();
    }

    /** @return 进度值 [0.0, 1.0] */
    public float getProgress() {
        return engine.getProgress();
    }

    /** @return 是否正在倒计时 */
    public boolean isRunning() {
        return engine.isRunning();
    }

    /** @return 是否已暂停 */
    public boolean isPaused() {
        return engine.isPaused();
    }

    public CountdownEngine.State getState() {
        return engine.getState();
    }

    private void schedule() {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (engine.tick()) {
                    complete();
                } else {
                    updateDisplay();
                    if (onTick != null) {
                        onTick.onTick(engine.getSecondsLeft(), TurnTimer.this);
                    }
                }
            }
        }.runTaskTimer(MCZJUGameCore.getInstance(), 20L, 20L);
    }

    private void stopTask() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        task = null;
    }

    private void complete() {
        if (terminal) {
            return;
        }
        terminal = true;
        stopTask();
        cleanupDisplay();
        if (onComplete != null) {
            onComplete.run();
        }
    }

    private void beginDisplay() {
        for (Map.Entry<UUID, DisplayMode> entry : viewers.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            switch (entry.getValue()) {
                case BOSS_BAR -> {
                    if (bossBar == null) {
                        bossBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
                    }
                    bossBar.addViewer(player);
                }
                case XP -> xpSnapshot.putIfAbsent(player.getUniqueId(), new float[]{player.getLevel(), player.getExp()});
                default -> { }
            }
        }
    }

    private void updateDisplay() {
        int left = engine.getSecondsLeft();
        float progress = engine.getProgress();
        String time = TimeFormat.formatMillis0(left * 1000L);
        for (Map.Entry<UUID, DisplayMode> entry : viewers.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            switch (entry.getValue()) {
                case ACTION_BAR -> player.sendActionBar(TextParser.parse("<aqua>剩余时间 <white>" + time));
                case BOSS_BAR -> {
                    if (bossBar != null) {
                        bossBar.name(TextParser.parse("<aqua>剩余时间 <white>" + time));
                        bossBar.progress(progress);
                    }
                }
                case XP -> {
                    player.setLevel(left);
                    player.setExp(progress);
                }
                default -> { }
            }
        }
    }

    private void cleanupDisplay() {
        if (bossBar != null) {
            for (Map.Entry<UUID, DisplayMode> entry : viewers.entrySet()) {
                if (entry.getValue() == DisplayMode.BOSS_BAR) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null) {
                        bossBar.removeViewer(player);
                    }
                }
            }
            bossBar = null;
        }
        for (Map.Entry<UUID, float[]> entry : xpSnapshot.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                player.setLevel((int) entry.getValue()[0]);
                player.setExp(entry.getValue()[1]);
            }
        }
        xpSnapshot.clear();
    }

    @FunctionalInterface
    public interface TickCallback {
        /**
         * @param secondsLeft 当前剩余秒数
         * @param timer       倒计时本身句柄，可直接调用 cancel() 等方法
         */
        void onTick(int secondsLeft, TurnTimer timer);
    }

    public static final class Builder {

        private final int totalSeconds;
        private final Map<Player, DisplayMode> viewers = new LinkedHashMap<>();
        private TickCallback onTick;
        private Runnable onComplete;
        private Runnable onCancel;

        /**
         * @param totalSeconds 倒计时总秒数（&gt;= 0；为 0 时立即触发完成回调）
         */
        public Builder(int totalSeconds) {
            if (totalSeconds < 0) {
                throw new IllegalArgumentException("totalSeconds 不能为负数");
            }
            this.totalSeconds = totalSeconds;
        }

        /** 指定向单个玩家展示倒计时。 */
        public Builder displayTo(Player player, DisplayMode mode) {
            if (player != null && mode != null) {
                viewers.put(player, mode);
            }
            return this;
        }

        /** 指定向单个玩家展示倒计时。 */
        public Builder displayTo(PlayerExt player, DisplayMode mode) {
            return player != null ? displayTo(player.player(), mode) : this;
        }

        /** 指定向一组玩家展示倒计时（同一模式）。 */
        public Builder displayToAll(Collection<? extends Player> players, DisplayMode mode) {
            if (players != null && mode != null) {
                players.forEach(p -> displayTo(p, mode));
            }
            return this;
        }

        /** 指定向一组玩家展示倒计时（同一模式）。 */
        public Builder displayToAll(List<PlayerExt> players, DisplayMode mode) {
            if (players != null && mode != null) {
                players.forEach(p -> displayTo(p, mode));
            }
            return this;
        }

        /** 注册每秒 tick 回调，参数为当前剩余秒数。 */
        public Builder onTick(TickCallback onTick) {
            this.onTick = onTick;
            return this;
        }

        /** 注册倒计时自然归零（超时）后的回调。 */
        public Builder onComplete(Runnable onComplete) {
            this.onComplete = onComplete;
            return this;
        }

        /** 注册倒计时被手动取消后的回调。 */
        public Builder onCancel(Runnable onCancel) {
            this.onCancel = onCancel;
            return this;
        }

        /** 仅构建，不启动。需手动调用 {@link TurnTimer#start()}。 */
        public TurnTimer build() {
            return new TurnTimer(this);
        }

        /** 构建并立即启动，返回句柄。 */
        public TurnTimer buildAndStart() {
            TurnTimer timer = new TurnTimer(this);
            timer.start();
            return timer;
        }
    }
}
