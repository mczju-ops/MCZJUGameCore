package com.github.mczjuops.mczjugamecore.player.listener;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.GameState;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.strategy.PlayerQuitReason;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    @EventHandler
    public void onPlayerInGameQuit(PlayerQuitEvent event){
        // 判断在player manager中进行，不在本处进行
        PlayerExt player = new PlayerExt(event.getPlayer());
        MCZJUGameCore.getPlayerManager().leaveGame(player, PlayerQuitReason.DISCONNECT);
    }
}
