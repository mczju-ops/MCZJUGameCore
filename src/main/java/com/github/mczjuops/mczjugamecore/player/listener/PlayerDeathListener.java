package com.github.mczjuops.mczjugamecore.player.listener;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class PlayerDeathListener implements Listener {

    @EventHandler
    public void onPlayerDeathInGame(PlayerDeathEvent event){
        PlayerExt player = new PlayerExt(event.getPlayer());
        if (player.isInGame()){
            AbstractGame game = player.getGame();
            assert game != null;
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
