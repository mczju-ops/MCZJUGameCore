package com.github.mczjuops.mczjugamecore.player.strategy.impl;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.impl.OpenSessionGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.strategy.PlayerQuitReason;
import org.jetbrains.annotations.NotNull;

public class DefaultQuitOpenSessionGameStrategy extends DefaultPlayerQuitStrategy {

    public DefaultQuitOpenSessionGameStrategy(AbstractGame game) {
        super(game);
    }

    @Override
    public void onPlayerQuit(@NotNull PlayerExt player, @NotNull PlayerQuitReason reason) {
        if (game instanceof OpenSessionGame osg) {
            osg.onPlayerQuit(player);
        }
    }
}
