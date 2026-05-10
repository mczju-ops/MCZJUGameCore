package com.github.mczjuops.mczjugamecore.game.manager;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.GameMeta;
import com.github.mczjuops.mczjugamecore.game.GameState;
import com.github.mczjuops.mczjugamecore.game.room.AbstractGameRoom;
import com.github.mczjuops.mczjugamecore.game.room.GameRoomState;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.party.Party;
import com.github.mczjuops.mczjugamecore.player.strategy.PlayerQuitReason;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class DefaultGameManager implements AbstractGameManager {

    private final ConsoleSender logger = new ConsoleSender("MGC: %s".formatted(getClass().getSimpleName()));

    private final Map<Class<? extends AbstractGame>, Class<? extends AbstractGameRoom>> registerGameMap = new HashMap<>();
    private final Map<String, Class<? extends AbstractGame>> gameIdMap = new HashMap<>();
    private final List<AbstractGame> gameList = new LinkedList<>();
    private final Map<String, GameMeta> gameMetaMap = new HashMap<>(); // 反射创建对象后顺手存了，方便菜单用

    @Override
    public void registerGame(Class<? extends AbstractGame> gameClass, Class<? extends AbstractGameRoom> gameRoomClass) {
        try {
            AbstractGame game = gameClass.getDeclaredConstructor().newInstance();
            String gameId = game.getId();
            if (gameIdMap.containsKey(gameId)){
                // 有这个游戏了
                logger.error("无法注册游戏 %s，相同 ID 的游戏已存在".formatted(gameId));
                return;
            }
            registerGameMap.put(gameClass, gameRoomClass);
            gameIdMap.put(gameId, gameClass);
            gameMetaMap.put(gameId, game.getGameMeta());
            MCZJUGameCore.getGameRoomManager().loadGameRoom(gameId, gameRoomClass); // 加载并注册所有该游戏的游戏房间
        } catch (InstantiationException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            logger.error("无法注册游戏%s，原因：无法访问无参构造器，无法创建游戏实例".formatted(gameClass));
            throw new RuntimeException(e);
        }

    }

    @Override
    public @Nullable AbstractGame createGame(String name) {
        // 先检查是否有空的游戏房间
        AbstractGameRoom gameRoom = MCZJUGameCore.getGameRoomManager().getLeisureGameRoom(name);
        if (gameRoom == null) return null;
        try {
            AbstractGame game = gameIdMap.get(name).getDeclaredConstructor().newInstance();
            game.setGameRoom(gameRoom);
            gameRoom.setState(GameRoomState.IN_GAME);   // 分配房间后，设置房间为占用状态
            logger.info("将房间 %s分配给游戏%s".formatted(gameRoom.getRoomName(), game.getId()));
            game.setState(GameState.WAITING);
            gameList.add(game);
            game.gameInit();
            return game;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            // 这一步应该不可能执行到，因为create前肯定register过，那个时候是能访问构造器的
            logger.error("无法注册游戏%s，原因：无法访问无参构造器，无法创建游戏实例".formatted(name));
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
        MCZJUGameCore.getPlayerManager().removeAllPlayer(game);
        game.getGameRoom().setState(GameRoomState.READY);
        gameList.remove(game);
    }

    @Override
    public void joinGame(PlayerExt player, String gameId) {
        if (player.isInParty() && !player.isPartyLeader()) {
            player.sender().warn("只有队长能开启游戏");
            return;
        }

        if (player.isInGame()) {
            // 直接进行下一个游戏

            player.sender().warn("请退出当前游戏，再进行下一个游戏！");
            return;
        }

        // 2. 尝试加入现有房间
        for (AbstractGame game : gameList) {
            if (Objects.equals(game.getId(), gameId) && game.getState() == GameState.WAITING) {
                if (tryJoin(player, game)) return;
            }
        }

        // 3. 尝试创建新房间并加入
        AbstractGame newGame = createGame(gameId);
        if (newGame == null) {
            player.sender().warn("该游戏没有空闲的房间，请稍后再试");
            return;
        }

        if (!tryJoin(player, newGame)) {
            // 如果新创建的房间也加不进去，可能是因为人数仍然超上限了
            // 不做任何处理
            return;
        }
    }

    @Override
    public @Nullable AbstractGameRoom createGameRoom(String gameId, String gameRoomName) {
        Class<? extends AbstractGame> gameClass = gameIdMap.get(gameId);
        if (gameClass == null) return null; // 可能是名称输错，就先不报错了
        Class<? extends AbstractGameRoom> gameRoomClass = registerGameMap.get(gameClass);
        assert gameRoomClass != null;   // 不可能为空，因为这两个得一起注册
        try {
            AbstractGameRoom gameRoom = gameRoomClass.getDeclaredConstructor().newInstance();
            gameRoom.setGameId(gameId);
            gameRoom.setRoomName(gameRoomName);
            return gameRoom;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            logger.error("无法创建新的游戏房间%s，原因：对应的游戏房间缺失无参构造器".formatted(gameId));
            throw new RuntimeException(e);
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
                return true;
            } else {
                // 回滚：全员退出
                party.getAllPlayer().forEach(p -> MCZJUGameCore.getPlayerManager().leaveGame(p, PlayerQuitReason.JOIN_FAIL));
                return false;
            }
        } else {
            // 单人加入
            MCZJUGameCore.getPlayerManager().joinGame(player, game);
            if (game.getGameWaitStrategy().onPlayerJoin(player)) {
                return true;
            } else {
                MCZJUGameCore.getPlayerManager().leaveGame(player, PlayerQuitReason.JOIN_FAIL);
                return false;
            }
        }
    }

    @Override
    public Set<String> getRegisteredGameIds() {
        return Set.copyOf(gameIdMap.keySet());
    }

    @Override
    public Map<String, GameMeta> getGameMetas() {
        return Collections.unmodifiableMap(gameMetaMap);
    }
}
