package com.github.mczjuops.mczjugamecore.game.kit.prompt;

import com.github.mczjuops.mczjugamecore.game.kit.GameAbortedException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 一次等待玩家输入的挂起操作。
 *
 * <p>游戏线程在 {@link #await(int, Object)} 上阻塞；主线程上的 GUI 点击或超时任务通过
 * {@link #complete(Object)} 唤醒它。超时会关闭菜单并按默认值返回。
 *
 * <p>人机 / 无人作答时 {@code player} 为 {@code null}，此时所有界面操作都是空操作 ——
 * 这是人机能走同一条代码路径的前提。
 */
public final class Pending<T> {

    private final Plugin plugin;
    private final Player player;
    private final CompletableFuture<T> future = new CompletableFuture<>();
    private BukkitTask timeoutTask;
    private Runnable onTimeout;
    private Runnable onComplete;
    private volatile boolean cancelled;

    public Pending(Plugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    /** 安排超时：到点后关闭菜单并唤醒等待方。 */
    public Pending<T> withTimeout(int seconds) {
        if (seconds <= 0 || plugin == null) {
            return this;
        }
        if (!plugin.isEnabled()) {
            // 关服中无法再注册任务，直接视为已超时
            complete(null);
            return this;
        }
        try {
            timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (future.isDone()) {
                    return;
                }
                if (onTimeout != null) {
                    onTimeout.run();
                }
                closeMenu();
                complete(null);
            }, seconds * 20L);
        } catch (IllegalStateException e) {
            complete(null);
        }
        return this;
    }

    /** 超时时的额外清理（例如关闭自定义 GUI）。 */
    public Pending<T> onTimeout(Runnable action) {
        this.onTimeout = action;
        return this;
    }

    /** 主线程调用：交付结果并取消超时任务。 */
    public void complete(T value) {
        if (timeoutTask != null && !timeoutTask.isCancelled()) {
            timeoutTask.cancel();
        }
        timeoutTask = null;
        if (future.complete(value)) {
            Runnable cb = onComplete;
            if (cb != null) {
                cb.run();
            }
        }
    }

    /** 结果交付后的回调（例如取消提示计时器）。 */
    public Pending<T> onComplete(Runnable action) {
        this.onComplete = action;
        return this;
    }

    public boolean isDone() {
        return future.isDone();
    }

    /** 这个挂起操作是在等谁（{@code null} 表示人机 / 无人）。 */
    public Player owner() {
        return player;
    }

    /** 是否被玩家主动取消（关闭界面 / 放弃）。 */
    public boolean isCancelled() {
        return cancelled;
    }

    /** 主线程调用：标记为「玩家取消」并立即唤醒等待方。 */
    public void cancelNow() {
        if (future.isDone()) {
            // 已经交付结果（选择成功或超时），关闭界面不算取消
            return;
        }
        cancelled = true;
        complete(null);
    }

    /** 立即关闭玩家界面（例如回合超时）。 */
    public void forceClose() {
        closeMenu();
    }

    /**
     * 游戏线程调用：等待结果，最多 {@code seconds} 秒。
     *
     * @param fallback 超时 / 中断以外的异常时的返回值
     * @return 玩家选择，或 {@code fallback}
     */
    public T await(int seconds, T fallback) {
        return awaitMillis(Math.max(1, seconds) * 1000L, fallback);
    }

    /**
     * 游戏线程调用：等待结果，最多 {@code millis} 毫秒。
     *
     * <p>并发询问（{@code MultiAsk}）靠它让所有问询共享同一个截止时刻。
     *
     * @return 玩家选择；超时 / 无结果返回 {@code null}
     */
    public T awaitMillis(long millis, T fallback) {
        try {
            T value = future.get(Math.max(1L, millis), TimeUnit.MILLISECONDS);
            return value == null ? fallback : value;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GameAbortedException("等待玩家输入时被中断");
        } catch (TimeoutException e) {
            closeMenu();
            return fallback;
        } catch (ExecutionException e) {
            return fallback;
        }
    }

    /** 主动关闭玩家当前打开的菜单。 */
    public void closeMenu() {
        Player target = player;
        if (target == null) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            if (target.isOnline()) {
                target.closeInventory();
            }
            return;
        }
        // 插件已禁用（关服中）时无法再注册任务，直接放弃关闭界面
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (target.isOnline()) {
                    target.closeInventory();
                }
            });
        } catch (IllegalStateException ignored) {
            // 关服竞态：忽略
        }
    }
}
