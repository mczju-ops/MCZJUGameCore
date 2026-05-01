package com.github.mczjuops.mczjugamecore.game.manager;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.room.AbstractGameRoom;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class DefaultGameManager implements AbstractGameManager {

    private final Logger logger = MCZJUGameCore.getInstance().getLogger();

    private final Map<Class<? extends AbstractGame>, Class<? extends AbstractGameRoom>> registerGameMap = new HashMap<>();
    private final Map<String, Class<? extends AbstractGame>> gameNameMap = new HashMap<>();



    @Override
    public void registerGame(Class<? extends AbstractGame> gameClass, Class<? extends AbstractGameRoom> gameRoomClass) {
        registerGameMap.put(gameClass, gameRoomClass);
        try {
            AbstractGame game = gameClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {

            throw new RuntimeException(e);
        }

    }

    @Override
    public void createGame(AbstractGame name) {

    }

    @Override
    public void startGame(AbstractGame game) {

    }

    @Override
    public void cancelGame(AbstractGame game) {

    }

    @Override
    public void abortGame(AbstractGame game) {

    }

    @Override
    public void endGame(AbstractGame game) {

    }

    @Override
    public void joinGame(PlayerExt player, String gameName) {

    }
}
