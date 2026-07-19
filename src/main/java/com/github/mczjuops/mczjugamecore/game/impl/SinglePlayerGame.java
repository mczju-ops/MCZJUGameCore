package com.github.mczjuops.mczjugamecore.game.impl;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.DefaultSinglePlayerGameWaitStrategy;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.GameWaitStrategy;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

public abstract class SinglePlayerGame extends AbstractGame {

    @Override
    public @NonNull GameWaitStrategy getGameWaitStrategy() {
        return new DefaultSinglePlayerGameWaitStrategy(this);
    }

    public @Nullable PlayerExt getPlayer() {
        if (getPlayers().isEmpty()) return null;
        return getPlayers().getFirst();
    }
}
