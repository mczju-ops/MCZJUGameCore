package com.github.mczjuops.mczjugamecore.game.manager;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.GameState;
import com.github.mczjuops.mczjugamecore.game.room.AbstractGameRoom;
import com.github.mczjuops.mczjugamecore.game.room.GameRoomState;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.party.Party;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class DefaultGameManager implements AbstractGameManager {

    private final ConsoleSender logger = new ConsoleSender(STR."MGC:\{getClass().getName()}");

    private final Map<Class<? extends AbstractGame>, Class<? extends AbstractGameRoom>> registerGameMap = new HashMap<>();
    private final Map<String, Class<? extends AbstractGame>> gameNameMap = new HashMap<>();
    private final List<AbstractGame> gameList = new LinkedList<>();



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
            game.setState(GameState.WAITING);
            gameList.add(game);
            return game;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            // 这一步应该不可能执行到，因为create前肯定register过，那个时候是能访问构造器的
            logger.error(STR."无法注册游戏: \{name} Reason: 无法创建游戏实例，由于无法访问无参构造器");
            throw new RuntimeException(e);
        }
    }

    @Override
    public void startGame(AbstractGame game) {
        game.gameStart();
    }

    @Override
    public void cancelGame(AbstractGame game) {
        solveGameEnd(game);
        game.cancelGame();

    }

    @Override
    public void abortGame(AbstractGame game) {
        game.abortGame();
        solveGameEnd(game);
    }

    @Override
    public void endGame(AbstractGame game) {
        game.endGame();
        solveGameEnd(game);
    }

    private void solveGameEnd(AbstractGame game){
        game.setState(GameState.END);
        for (PlayerExt player : MCZJUGameCore.getPlayerManager().getPlayers(game)) {
            MCZJUGameCore.getPlayerManager().leaveGame(player);
        }
    }

    @Override
    public void joinGame(PlayerExt player, String gameName) {
        // 1. 基础状态校验
        if (player.isInGame()) {
            player.sender().warn("请退出当前游戏，再进行下一个游戏！");
            return;
        }
        if (player.isInParty() && !player.isPartyLeader()) {
            player.sender().warn("只有队长能开启游戏");
            return;
        }

        // 2. 尝试加入现有房间
        for (AbstractGame game : gameList) {
            if (Objects.equals(game.getName(), gameName) && game.getState() == GameState.WAITING) {
                if (tryJoin(player, game)) return;
            }
        }

        // 3. 尝试创建新房间并加入
        AbstractGame newGame = createGame(gameName);
        if (newGame == null) {
            player.sender().warn(STR."\{gameName}没有空闲的房间，请稍后再试");
            return;
        }

        if (!tryJoin(player, newGame)) {
            // 如果新创建的房间也加不进去，可能是因为人数仍然超上限了
            // 不做任何处理
            return;
        }
    }

    /**
     * 提取出的通用加入逻辑：封装了单人和组队的入场/退场逻辑
     * @return 是否加入成功
     */
    private boolean tryJoin(PlayerExt player, AbstractGame game) {
        if (player.isInParty()) {
            Party party = player.getParty();
            assert party != null;

            // 预加入：全员注册到管理器
            party.getAllPlayer().forEach(p -> MCZJUGameCore.getPlayerManager().joinGame(p, game));

            if (game.getGameWaitStrategy().onPartyJoin(party)) {
                party.sender().info(STR."加入游戏\{game.getName()}");
                return true;
            } else {
                // 回滚：全员退出
                party.getAllPlayer().forEach(p -> MCZJUGameCore.getPlayerManager().leaveGame(p));
                return false;
            }
        } else {
            // 单人加入
            MCZJUGameCore.getPlayerManager().joinGame(player, game);
            if (game.getGameWaitStrategy().onPlayerJoin(player)) {
                player.sender().info(STR."加入游戏\{game.getName()}");
                return true;
            } else {
                MCZJUGameCore.getPlayerManager().leaveGame(player);
                return false;
            }
        }
    }
}
