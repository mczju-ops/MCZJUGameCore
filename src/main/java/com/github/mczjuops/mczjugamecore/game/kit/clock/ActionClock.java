package com.github.mczjuops.mczjugamecore.game.kit.clock;

import com.github.mczjuops.mczjugamecore.utils.TextParser;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * 单个玩家的回合主时钟：基础时间 + 每回合刷新的额外时间。
 *
 * <p>扣时顺序：**先扣额外时间，再扣基础时间**。
 * 额外时间每回合重置，基础时间一旦消耗不再恢复 —— 这正是日麻的「持ち時間 + 秒読み」，
 * 也是 SoulCard 的「基础 20 秒 + 每回合 5 秒」。
 *
 * <p>与 {@link com.github.mczjuops.mczjugamecore.game.timer.TurnTimer} 的分工：
 * 这个是**回合主时钟**（决定这一手的时间用光没有）；
 * 那个是**提示倒计时**（提醒某个被询问的玩家还有几秒回话）。
 */
public final class ActionClock {

    private final Plugin plugin;
    private final Player owner;
    private final BossBar bossBar;

    private int main;
    private int extra;
    private BukkitTask task;
    private Runnable onExpire;
    private boolean running;

    public ActionClock(Plugin plugin, Player owner, int main, int extra) {
        this.plugin = plugin;
        this.owner = owner;
        this.main = Math.max(0, main);
        this.extra = Math.max(0, extra);
        this.bossBar = BossBar.bossBar(display(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
    }

    public int main() {
        return main;
    }

    public int extra() {
        return extra;
    }

    public int total() {
        return main + extra;
    }

    public boolean isRunning() {
        return running;
    }

    public void setOnExpire(Runnable onExpire) {
        this.onExpire = onExpire;
    }

    /** 启动倒计时并显示 BossBar。 */
    public void start() {
        if (running) {
            return;
        }
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        running = true;
        attach();
        render();
        try {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        } catch (IllegalStateException e) {
            running = false;
        }
    }

    /** 暂停（保留剩余时间）。 */
    public void pause() {
        if (!running) {
            return;
        }
        running = false;
        cancelTask();
        detach();
    }

    /** 恢复。 */
    public void resume() {
        if (running || total() <= 0) {
            return;
        }
        start();
    }

    /** 彻底停止并移除显示。 */
    public void stop() {
        running = false;
        cancelTask();
        detach();
    }

    /** 刷新额外时间（每回合开始时调用）。 */
    public void refreshExtra(int newExtra) {
        this.extra = Math.max(0, newExtra);
        render();
    }

    private void tick() {
        if (!running) {
            return;
        }
        decrementOneSecond();
        render();
        if (total() <= 0) {
            running = false;
            cancelTask();
            detach();
            if (onExpire != null) {
                onExpire.run();
            }
        }
    }

    /**
     * 扣掉一秒。**先扣额外时间，再扣基础时间** —— 这是「每回合刷新的时间先花」的规则，
     * 与日麻的「秒読み先于持ち時間」一致。包内可见便于单测。
     */
    void decrementOneSecond() {
        if (extra > 0) {
            extra--;
        } else if (main > 0) {
            main--;
        }
    }

    private void cancelTask() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        task = null;
    }

    private void attach() {
        if (owner != null && owner.isOnline()) {
            owner.showBossBar(bossBar);
        }
    }

    private void detach() {
        if (owner != null && owner.isOnline()) {
            owner.hideBossBar(bossBar);
        }
    }

    private void render() {
        int total = total();
        bossBar.name(display());
        float max = Math.max(1, total <= 0 ? 1 : total);
        bossBar.progress(total <= 0 ? 0f : Math.min(1f, total / max));
        if (total <= 10) {
            bossBar.color(BossBar.Color.RED);
        } else if (total <= 20) {
            bossBar.color(BossBar.Color.YELLOW);
        } else {
            bossBar.color(BossBar.Color.GREEN);
        }
        if (owner != null && owner.isOnline()) {
            owner.sendActionBar(display());
        }
    }

    private net.kyori.adventure.text.Component display() {
        return TextParser.parse("<aqua>剩余 <white>" + main + "</white><gray>+</gray><yellow>" + extra
                + "</yellow> <dark_gray>秒");
    }
}
