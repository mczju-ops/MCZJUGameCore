package com.github.mczjuops.mczjugamecore.game.kit.thread;

import com.github.mczjuops.mczjugamecore.game.kit.GameAbortedException;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 游戏逻辑线程。
 *
 * <p>一局游戏的逻辑写成顺序几段式（选目标 → 询问响应 → 结算 → 摸牌），但 Bukkit 主线程不能阻塞，
 * 因此把逻辑放进**虚拟线程**执行，所有 Bukkit API 通过 {@link #call} 编组回主线程。
 *
 * <p>约束：{@link #call}/{@link #run} 只能在游戏线程调用，不能在主线程调用（会死锁）。
 */
public final class GameThread {

    private final Plugin plugin;
    private final String name;
    private Thread thread;
    private volatile boolean running;

    public GameThread(Plugin plugin, String name) {
        this.plugin = plugin;
        this.name = name;
    }

    public boolean isRunning() {
        return running;
    }

    public String name() {
        return name;
    }

    /**
     * 启动逻辑线程。
     *
     * @param body 一局游戏的完整逻辑
     */
    public void start(Runnable body) {
        running = true;
        thread = Thread.ofVirtual().name("MGC-" + name).start(() -> {
            try {
                body.run();
            } catch (GameAbortedException ignored) {
                // 正常中止路径
            } catch (Throwable t) {
                plugin.getLogger().severe("[" + name + "] 游戏线程异常：" + t);
                t.printStackTrace();
            } finally {
                running = false;
            }
        });
    }

    /** 停止逻辑线程：标记结束并中断等待。 */
    public void stop() {
        running = false;
        Thread t = thread;
        if (t != null) {
            t.interrupt();
        }
        thread = null;
    }

    /** 若已停止则抛出 {@link GameAbortedException}。 */
    public void checkAlive() {
        if (!running || Thread.currentThread().isInterrupted()) {
            throw new GameAbortedException("游戏已结束");
        }
    }

    /** 在主线程执行并同步等待结果。 */
    public <T> T call(Supplier<T> supplier) {
        checkAlive();
        if (Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("GameThread#call 不能在主线程调用");
        }
        // 关服时插件已禁用，不能再注册任务
        if (!plugin.isEnabled()) {
            throw new GameAbortedException("插件已停用");
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        BukkitTask task;
        try {
            task = Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    future.complete(supplier.get());
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        } catch (IllegalStateException e) {
            throw new GameAbortedException("插件已停用");
        }
        if (task == null) {
            throw new GameAbortedException("服务器已关闭");
        }
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GameAbortedException("等待主线程时被中断");
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new GameAbortedException("主线程响应超时");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause);
        }
    }

    /** 在主线程执行。 */
    public void run(Runnable runnable) {
        call(() -> {
            runnable.run();
            return null;
        });
    }

    /** 在主线程延迟执行（指定 tick 后），不等待结果。 */
    public void runLater(Runnable runnable, long delayTicks) {
        checkAlive();
        if (!plugin.isEnabled()) {
            throw new GameAbortedException("插件已停用");
        }
        try {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (running) {
                    runnable.run();
                }
            }, Math.max(0L, delayTicks));
        } catch (IllegalStateException e) {
            throw new GameAbortedException("插件已停用");
        }
    }

    /**
     * 便利方法：把操作编组到主线程，插件停用时安全放弃。
     *
     * <p>停在停服 / 重载途中调用也不会抛 {@code IllegalPluginAccessException}。
     */
    public static void runOnMainSafely(Plugin plugin, Runnable task) {
        if (plugin == null || task == null) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        if (!plugin.isEnabled()) {
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (IllegalStateException ignored) {
            // 关服竞态
        }
    }
}
