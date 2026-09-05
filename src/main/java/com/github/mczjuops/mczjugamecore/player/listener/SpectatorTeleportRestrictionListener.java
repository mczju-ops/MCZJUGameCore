package com.github.mczjuops.mczjugamecore.player.listener;

import com.github.mczjuops.mczjugamecore.event.PlayerSpectatorTeleportEvent;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** 限制游戏中的旁观玩家只能传送到同一局游戏的玩家。 */
public final class SpectatorTeleportRestrictionListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpectatorTeleport(PlayerSpectatorTeleportEvent event) {
        PlayerExt spectator = new PlayerExt(event.getPlayer());
        AbstractGame spectatorGame = spectator.getGame();
        if (spectatorGame == null) return;

        Player target = event.getEstimatedTarget();
        if (target != null && new PlayerExt(target).getGame() == spectatorGame) return;

        event.setCancelled(true);
        spectator.actionBarSender().error("只能传送到同一局游戏的玩家");
    }
}
