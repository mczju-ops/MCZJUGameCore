package com.github.mczjuops.mczjugamecore.game.impl;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.DefaultGameWaitStrategy;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.GameWaitStrategy;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Random;

public abstract class TurnBasedTwoPlayerGame extends AbstractGame {
    private int currentPlayerIdx = new Random().nextInt(2);

    @Override
    public @NonNull GameWaitStrategy getGameWaitStrategy(){
        return new DefaultGameWaitStrategy(this, 2, 2);
    }

    public @NotNull PlayerExt getCurrentPlayer(){
        return getPlayers().get(currentPlayerIdx);
    }

    public @NotNull PlayerExt getAnotherPlayer(){
        return getPlayers().get(1 - currentPlayerIdx);
    }

    public void changePlayer(){
        currentPlayerIdx = 1 - currentPlayerIdx;
        onPlayerChange();
    }

    public boolean isCurrentPlayer(@NotNull PlayerExt player){
        return getCurrentPlayer().equals(player);
    }

    public abstract void onPlayerChange();


}
