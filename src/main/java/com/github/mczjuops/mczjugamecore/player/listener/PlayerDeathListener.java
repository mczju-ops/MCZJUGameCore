package com.github.mczjuops.mczjugamecore.player.listener;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import com.github.mczjuops.mczjugamecore.event.PlayerGameDeathEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class PlayerDeathListener implements Listener {

    /** 游戏内先发布死亡通知，再执行原死亡策略；大厅死亡维持原有保护行为。 */
    @EventHandler
    public void onPlayerDeathInGame(PlayerDeathEvent event){
        PlayerExt player = new PlayerExt(event.getPlayer());
        if (player.isInGame()){
            AbstractGame game = player.getGame();
            assert game != null;
            if (!event.isCancelled()) {
                Player killer = event.getDamageSource().getCausingEntity() instanceof Player p ? p : event.getPlayer().getKiller();
                Bukkit.getPluginManager().callEvent(new PlayerGameDeathEvent(
                        game, player, killer == null ? null : killer.getUniqueId()));
            }
            game.getPlayerDeathStrategy().onPlayerDeath(player, event);
        } else {
            event.setCancelled(true); // 未游玩小游戏时，不会死亡，而是直接传送到出生点
            player.resetState();

            Location spawn = MCZJUGameCore.getLobbyManager().getMainLobby();
            if (spawn == null) {
                spawn = player.player().getWorld().getSpawnLocation();
            }

            Player p = player.player();
            p.teleport(spawn);
            p.addPotionEffect(new PotionEffect(
                    PotionEffectType.SATURATION,
                    PotionEffect.INFINITE_DURATION,
                    0,
                    true,
                    false,
                    false
            ));
        }
    }
}
