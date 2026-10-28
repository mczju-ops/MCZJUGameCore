package com.github.mczjuops.mczjugamecore.achievement;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.event.GameStatisticChangedEvent;
import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.PluginDisableEvent;

/** 核心登录分母、统计变更和原生成就同步监听器。 */
public final class AchievementListener implements Listener {
    private final AchievementStorage storage;
    /** 核心注入共享存储。 */
    public AchievementListener(AchievementStorage storage) { this.storage = storage; }
    /** 实际登录记录分母；延迟一秒解锁入口，让首次原生进度包先完成同步以显示入口弹窗。 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        storage.recordLogin(event.getPlayer().getUniqueId());
        storage.recordMinigamesEntry(event.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTaskLater(MCZJUGameCore.getInstance(), () -> {
            if (!event.getPlayer().isOnline()) return;
            MCZJUGameCore.getAchievementManager().getNativeDisplay().sync(event.getPlayer());
        }, 20L);
    }
    /** 成就仅订阅统计变更，统计服务不直接依赖成就触发逻辑。 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onStatisticChanged(GameStatisticChangedEvent event) {
        MCZJUGameCore.getAchievementManager().checkStatistic(event.getPlayerId(), event.getGameId(), event.getStatistic());
    }
    /** 数据包重载会清除非持久原生成就，重新生成全部当前定义。 */
    @EventHandler
    public void onResourcesReloaded(ServerResourcesReloadedEvent event) {
        MCZJUGameCore.getAchievementManager().getNativeDisplay().scheduleRebuild();
    }
    /** 防止通过原生命令绕过核心记录解锁投影成就。 */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCriterion(PlayerAdvancementCriterionGrantEvent event) {
        if (!MCZJUGameCore.getAchievementManager().getNativeDisplay().mayGrant(event.getPlayer(), event.getAdvancement().getKey()))
            event.setCancelled(true);
    }
    /** 子插件停用时保留数据，仅移除可触发的定义。 */
    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        MCZJUGameCore.getAchievementManager().unregister(event.getPlugin());
    }
}
