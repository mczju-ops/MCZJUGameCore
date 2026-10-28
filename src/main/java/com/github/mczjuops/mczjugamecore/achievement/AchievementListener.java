package com.github.mczjuops.mczjugamecore.achievement;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.event.GameStatisticChangedEvent;
import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
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
    /** 切换世界后补同步全服共享进度，已完成的投影不会重复授予。 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChanged(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTask(MCZJUGameCore.getInstance(), () -> {
            if (!event.getPlayer().isOnline()) return;
            MCZJUGameCore.getAchievementManager().getNativeDisplay().sync(event.getPlayer());
        });
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
    /**
     * MGC 自定义投影以核心记录为准，在所有世界允许已解锁成就的原生授予和弹窗。
     * 恢复世界插件取消的合法投影，拒绝绕过核心记录的授予；不干预其他命名空间。
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCriterion(PlayerAdvancementCriterionGrantEvent event) {
        var key = event.getAdvancement().getKey();
        if (!key.getNamespace().equals("mgc_achievements")) return;
        event.setCancelled(!MCZJUGameCore.getAchievementManager().getNativeDisplay().mayGrant(event.getPlayer(), key));
    }
    /** 子插件停用时保留数据，仅移除可触发的定义。 */
    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        MCZJUGameCore.getAchievementManager().unregister(event.getPlugin());
    }
}
