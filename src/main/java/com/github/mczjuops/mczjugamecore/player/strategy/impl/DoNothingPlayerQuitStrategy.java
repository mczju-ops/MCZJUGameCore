package com.github.mczjuops.mczjugamecore.player.strategy.impl;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.strategy.AbstractPlayerQuitStrategy;
import com.github.mczjuops.mczjugamecore.player.strategy.PlayerQuitReason;
import org.jetbrains.annotations.NotNull;

public class DoNothingPlayerQuitStrategy extends AbstractPlayerQuitStrategy {
    public DoNothingPlayerQuitStrategy(AbstractGame game) {
        super(game);
    }

    @Override
    public void onPlayerQuit(@NotNull PlayerExt player, @NotNull PlayerQuitReason reason) {
        // do nothing
    }
}
