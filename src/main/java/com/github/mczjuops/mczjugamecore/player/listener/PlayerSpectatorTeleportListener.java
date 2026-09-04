package com.github.mczjuops.mczjugamecore.player.listener;

import com.github.mczjuops.mczjugamecore.event.player.PlayerSpectatorTeleportEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class PlayerSpectatorTeleportListener implements Listener {

    private static final double TARGET_SEARCH_RADIUS = 2.0;
    private static final double TARGET_SEARCH_RADIUS_SQUARED = TARGET_SEARCH_RADIUS * TARGET_SEARCH_RADIUS;

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerSpectatorTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE) return;

        Location to = event.getTo();
        Player player = event.getPlayer();
        Player estimatedTarget = null;
        double nearestDistanceSquared = Double.POSITIVE_INFINITY;

        for (Player candidate : to.getWorld().getNearbyEntitiesByType(
                Player.class,
                to,
                TARGET_SEARCH_RADIUS,
                target -> !target.getUniqueId().equals(player.getUniqueId())
                        && target.getGameMode() != GameMode.SPECTATOR
        )) {
            double distanceSquared = candidate.getLocation().distanceSquared(to);
            if (distanceSquared <= TARGET_SEARCH_RADIUS_SQUARED
                    && distanceSquared < nearestDistanceSquared) {
                estimatedTarget = candidate;
                nearestDistanceSquared = distanceSquared;
            }
        }

        PlayerSpectatorTeleportEvent spectatorTeleportEvent = new PlayerSpectatorTeleportEvent(
                player,
                event.getFrom(),
                to,
                estimatedTarget,
                nearestDistanceSquared
        );
        Bukkit.getPluginManager().callEvent(spectatorTeleportEvent);

        if (spectatorTeleportEvent.isCancelled()) {
            event.setCancelled(true);
        }
    }
}
