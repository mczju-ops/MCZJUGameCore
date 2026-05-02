package com.github.mczjuops.mczjugamecore.game.manager;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.room.AbstractGameRoom;
import com.github.mczjuops.mczjugamecore.game.room.GameRoomManager;
import com.github.mczjuops.mczjugamecore.game.room.GameRoomState;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class DefaultGameManager implements AbstractGameManager {

    private final ConsoleSender logger = new ConsoleSender(STR."MGC:\{getClass().getName()}");

    private final Map<Class<? extends AbstractGame>, Class<? extends AbstractGameRoom>> registerGameMap = new HashMap<>();
    private final Map<String, Class<? extends AbstractGame>> gameNameMap = new HashMap<>();



    @Override
    public void registerGame(Class<? extends AbstractGame> gameClass, Class<? extends AbstractGameRoom> gameRoomClass) {
        try {
            AbstractGame game = gameClass.getDeclaredConstructor().newInstance();
            String name = game.getName();
            if (gameNameMap.containsKey(name)){
                // 有这个游戏了
                logger.error(STR."无法注册游戏，因为同名游戏已存在: \{name}");
                return;
            }
            registerGameMap.put(gameClass, gameRoomClass);
            gameNameMap.put(name, gameClass);
            MCZJUGameCore.getGameRoomManager().loadGameRoom(name, gameRoomClass);    // 加载并注册所有该游戏的游戏房间
        } catch (InstantiationException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            logger.error(STR."无法注册游戏: \{gameClass} Reason: 无法创建游戏实例，由于无法访问无参构造器");
            throw new RuntimeException(e);
        }

    }

    @Override
    public @Nullable AbstractGame createGame(String name) {
        // 先检查是否有空的游戏房间
        AbstractGameRoom gameRoom = MCZJUGameCore.getGameRoomManager().getLeisureGameRoom(name);
        if (gameRoom == null) return null;
        try {
            AbstractGame game = gameNameMap.get(name).getDeclaredConstructor().newInstance();
            game.setGameRoom(gameRoom);
            gameRoom.setState(GameRoomState.IN_GAME);   // 分配房间后，设置房间为占用状态
            logger.info(STR."将房间\{gameRoom.getRoomName()}分配给游戏\{game.getName()}");
            return game;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            // 这一步应该不可能执行到，因为create前肯定register过，那个时候是能访问构造器的
            logger.error(STR."无法注册游戏: \{name} Reason: 无法创建游戏实例，由于无法访问无参构造器");
            throw new RuntimeException(e);
        }
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
