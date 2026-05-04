package com.github.mczjuops.mczjugamecore.player.listener;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class PlayerDeathListener implements Listener {

    @EventHandler
    public void onPlayerDeathInGame(PlayerDeathEvent event){
        PlayerExt player = new PlayerExt(event.getPlayer());
        if (player.isInGame()){
            AbstractGame game = player.getGame();
            assert game != null;
            game.getPlayerDeathStrategy().onPlayerDeath(player, event);
        }
    }
}
