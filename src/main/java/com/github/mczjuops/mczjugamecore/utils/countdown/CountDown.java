package com.github.mczjuops.mczjugamecore.utils.countdown;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.Map;


public class CountDown {

    private final Plugin plugin;
    private final Map<CountDownType, Integer> durations;
    private final Map<CountDownType, BukkitTask> activeTasks = new EnumMap<>(CountDownType.class);

    // 通过构造函数注入配置好的时长（由 ConfigManager 提供）
    public CountDown(Plugin plugin, Map<CountDownType, Integer> durations) {
        this.plugin = plugin;
        this.durations = durations;
    }

    /**
     * 启动指定类型的倒计时（自动取消同类型已有任务）
     */
    public void startCountdown(CountDownType type) {
        cancelCountdown(type);

        int totalSeconds = durations.getOrDefault(type, 30);
        //此30为默认值（单位为秒），可以修改
        //TODO：如果需要，可以改成配置文件获取

        if (totalSeconds <= 0) {
            // 时长为 0 直接触发完成逻辑
            completeCountdown(type);
            return;
        }

        CountdownTask task = new CountdownTask(type, totalSeconds);
        BukkitTask bukkitTask = task.runTaskTimer(plugin, 0L, 20L);
        activeTasks.put(type, bukkitTask);
    }

    /**
     * 取消指定类型的倒计时
     */
    public void cancelCountdown(CountDownType type) {
        BukkitTask existing = activeTasks.remove(type);
        if (existing != null && !existing.isCancelled()) {
            existing.cancel();
        }
    }

    /**
     * 取消所有倒计时
     */
    public void cancelAll() {
        activeTasks.values().forEach(task -> {
            if (!task.isCancelled()) task.cancel();
        });
        activeTasks.clear();
    }

    // 供外部判断是否有倒计时在运行
    public boolean isCounting() {
        return !activeTasks.isEmpty();
    }

    // ----- 内部倒计时执行逻辑 -----
    private class CountdownTask extends BukkitRunnable {
        private final CountDownType type;
        private int secondsLeft;

        CountdownTask(CountDownType type, int initialSeconds) {
            this.type = type;
            this.secondsLeft = initialSeconds;
        }

        @Override
        public void run() {
            if (secondsLeft <= 0) {
                completeCountdown(type);
                cancel(); // 自动从 activeTasks 移除会在 completeCountdown 中处理
                return;
            }

            // 广播标题（如果有定义）
            if (type.getTitleSuffix() != null) {
                //广播（标题）逻辑
            }

            // 广播聊天消息（高频提示条件）
            boolean shouldBroadcast = secondsLeft < durations.get(type);//此行表示允许聊天栏广播的条件
            if (shouldBroadcast) {
                //广播（聊天栏）逻辑
            }

            secondsLeft--;
        }

    }

    private void completeCountdown(CountDownType type) {
        activeTasks.remove(type); // 清理任务引用
        type.getOnComplete().accept(new CountDownType.CountdownContext(this));
    }
}