package com.github.mczjuops.mczjugamecore.player;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.manager.DefaultGameManager;

import java.util.List;

public class DefaultPlayerManager implements AbstractPlayerManager {

    public DefaultPlayerManager(){}
    @Override
    public List<PlayerExt> getPlayers(AbstractGame game) {
        return null;
    }

    @Override
    public boolean addPlayer(AbstractGame game) {
        return false;
    }

    @Override
    public void joinGame(PlayerExt player, AbstractGame game) {

    }
}
