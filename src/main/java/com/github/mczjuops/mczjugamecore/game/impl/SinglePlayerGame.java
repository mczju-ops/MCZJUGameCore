package com.github.mczjuops.mczjugamecore.game.impl;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.DefaultGameWaitStrategy;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.GameWaitStrategy;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;

public abstract class SinglePlayerGame extends AbstractGame {

    @Override
    public GameWaitStrategy getGameWaitStrategy() {
        return new DefaultGameWaitStrategy(this, 1);
    }

    public PlayerExt getPlayer() {
        return getPlayers().getFirst();
    }
}
