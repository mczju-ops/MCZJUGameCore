package com.github.mczjuops.mczjugamecore.game.strategy.impl;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.GameWaitStrategy;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;

public class SinglePlayerGame extends AbstractGame {

    @Override
    public String getName() {
        return null;
    }

    @Override
    public GameWaitStrategy getGameWaitStrategy() {
        return null;
    }

    @Override
    protected void onPlayerJoin(PlayerExt player) {

    }

    @Override
    protected boolean onGameInit() {
        return false;
    }

    @Override
    protected void onGameStart() {

    }

    @Override
    protected void onGameCancel() {

    }

    @Override
    protected void onGameAbort() {

    }

    @Override
    protected void onGameEnd() {

    }
}
