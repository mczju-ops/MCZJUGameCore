package com.github.mczjuops.mczjugamecore.game.kit.spectator;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.event.PlayerProfileLoadedEvent;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * 旁观会话的通用守卫监听器：重生点修正、死亡界面兜底、掉线与重连恢复。
 *
 * <p>由 MGC 启动时自动注册，子插件不需要再注册一次。所有判断都以
 * {@link SpectatorSession} 中"本局具体游戏实例"的登记为准，因此它只会处理
 * 真正被本局标记为旁观/等复活的玩家，不影响其他房间、其他游戏和大厅玩家。
 */
public final class SpectatorGuardListener implements Listener {

    /**
     * 旁观者重生时把重生点钉在观战锚点，避免被送到世界出生点或别人的床位；
     * 并在下一 tick 补一次旁观模式，防止重生过程中模式被还原。
     * @param event 玩家重生事件
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpectatorRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        SpectatorSession session = SpectatorSession.ofPlayer(player);
        if (session == null || !session.isSpectating(player.getUniqueId())) return;

        Location anchor = session.anchorFor(player.getUniqueId());
        if (anchor != null && anchor.getWorld() != null) event.setRespawnLocation(anchor);

        runNextTick(player, () -> session.reapply(new PlayerExt(player)));
    }

    /**
     * 旁观者再次死亡（例如被其他插件强杀）时，下一 tick 把他拉回旁观，
     * 不让他卡在死亡界面。正在等复活的玩家跳过，交给死亡流程本身的倒计时处理。
     * @param event 玩家死亡事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpectatorDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        SpectatorSession session = SpectatorSession.ofPlayer(player);
        if (session == null || !session.isSpectating(player.getUniqueId())) return;
        if (session.isWaitingRespawn(player.getUniqueId())) return;

        runNextTick(player, () -> session.reapply(new PlayerExt(player)));
    }

    /**
     * 玩家掉线时停掉复活倒计时；登记会保留，重连后继续。
     * @param event 玩家离线事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PlayerExt player = new PlayerExt(event.getPlayer());
        for (SpectatorSession session : SpectatorSession.activeSessions()) {
            session.onPlayerQuit(player);
        }
    }

    /**
     * 档案加载完成（此时核心可能已经把玩家自动加回原局）后重新施加旁观，
     * 让掉线重连的旁观者/等复活玩家回到原来的观战位置与倒计时。
     * @param event 玩家档案加载完成事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileLoaded(PlayerProfileLoadedEvent event) {
        Player player = event.getPlayer();
        SpectatorSession session = SpectatorSession.ofPlayer(player);
        if (session == null) return;
        session.reapply(new PlayerExt(player));
    }

    private static void runNextTick(Player player, Runnable task) {
        Bukkit.getScheduler().runTaskLater(MCZJUGameCore.getInstance(), () -> {
            if (player.isOnline()) task.run();
        }, 1L);
    }
}
