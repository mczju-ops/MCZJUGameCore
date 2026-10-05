package com.github.mczjuops.mczjugamecore.player.listener;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.event.PlayerProfileLoadedEvent;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.AutoReconnect;
import com.github.mczjuops.mczjugamecore.game.GameState;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 仅通过事件处理 AutoReconnect 游戏的掉线和重连，不持有离线玩家或已结束游戏的强引用。 */
public class PlayerReconnectListener implements Listener {
    private final Map<UUID, WeakReference<AbstractGame>> pendingReconnects = new HashMap<>();

    /**
     * 在普通退出监听器前记录运行中 AutoReconnect 游戏的掉线，不干预退出策略。
     * @param event 玩家离线事件；等待阶段和其他游戏仍走原退出流程
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        PlayerExt player = new PlayerExt(event.getPlayer());
        AbstractGame game = player.getGame();
        if (!(game instanceof AutoReconnect) || game.getState() != GameState.RUNNING) return;

        pendingReconnects.put(event.getPlayer().getUniqueId(), new WeakReference<>(game));
    }

    /**
     * 档案恢复成功后检查原游戏状态，通过已有加入 API 重连原局；失败时回主大厅。
     * @param event 玩家登录档案已恢复事件；无记录或已加入其他游戏时不处理
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerProfileLoaded(PlayerProfileLoadedEvent event) {
        WeakReference<AbstractGame> reference = pendingReconnects.remove(event.getPlayer().getUniqueId());
        if (reference == null || !event.getPlayer().isOnline()) return;
        PlayerExt player = new PlayerExt(event.getPlayer());
        if (player.isInGame()) return;

        AbstractGame game = reference.get();
        if (game != null && game.getState() == GameState.RUNNING && game.getGameRoom() != null) {
            String roomName = game.getGameRoom().getRoomName();
            var gameManager = MCZJUGameCore.getGameManager();
            if (gameManager.getGame(game.getId(), roomName) == game) {
                gameManager.joinGame(player, game.getId(), roomName);
                if (player.getGame() == game) {
                    player.sender().success("已自动重新加入游戏");
                    return;
                }
            }
        }
        teleportToLobby(player);
    }

    private void teleportToLobby(PlayerExt player) {
        // 中途加入失败只撤销成员关系，需要显式恢复大厅档案。
        player.switchProfile(null);
        player.player().playerListName(null);
        player.player().sendPlayerListFooter(Component.empty());
        Location lobby = MCZJUGameCore.getLobbyManager().getMainLobby();
        if (lobby == null) {
            player.sender().warn("无法重连原游戏，主大厅尚未配置或世界未加载");
        } else if (player.player().teleport(lobby)) {
            player.sender().info("原游戏已结束或无法重新加入，已返回大厅");
        } else {
            player.sender().warn("无法重连原游戏，传送到大厅失败");
        }
    }
}
