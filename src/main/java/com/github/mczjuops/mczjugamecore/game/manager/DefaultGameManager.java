package com.github.mczjuops.mczjugamecore.game.manager;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.GameMeta;
import com.github.mczjuops.mczjugamecore.game.GameState;
import com.github.mczjuops.mczjugamecore.game.MidGameJoinable;
import com.github.mczjuops.mczjugamecore.game.room.AbstractGameRoom;
import com.github.mczjuops.mczjugamecore.game.room.GameRoomState;
import com.github.mczjuops.mczjugamecore.game.room.PlayerSelectable;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.party.Party;
import com.github.mczjuops.mczjugamecore.player.strategy.PlayerQuitReason;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import org.jetbrains.annotations.NotNull;
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
    public @Nullable AbstractGame createGame(String gameId) {
        // 先检查是否有空的游戏房间
        AbstractGameRoom gameRoom = MCZJUGameCore.getGameRoomManager().getRandomLeisureGameRoom(gameId);
        if (gameRoom == null) return null;
        try {
            AbstractGame game = gameIdMap.get(gameId).getDeclaredConstructor().newInstance();
            game.setGameRoom(gameRoom);
            gameRoom.setState(GameRoomState.IN_GAME);   // 分配房间后，设置房间为占用状态
            logger.info("将房间 %s 分配给游戏 %s".formatted(gameRoom.getRoomName(), game.getId()));
            game.setState(GameState.WAITING);
            gameList.add(game);
            if (game.gameInit()) return game;
            else {
                // 初始化失败，可能是房间设置问题，比如有个参数没设置
                cancelGame(game);
                return null;
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            // 这一步应该不可能执行到，因为create前肯定register过，那个时候是能访问构造器的
            logger.error("无法注册游戏%s，原因：无法访问无参构造器，无法创建游戏实例".formatted(gameId));
            throw new RuntimeException(e);
        }
    }

    @Override
    public @Nullable AbstractGame createGame(String gameId, String roomName) {
        AbstractGameRoom gameRoom = MCZJUGameCore.getGameRoomManager().getGameRoom(gameId, roomName);
        if (gameRoom == null) return null;
        try {
            AbstractGame game = gameIdMap.get(gameId).getDeclaredConstructor().newInstance();
            game.setGameRoom(gameRoom);
            gameRoom.setState(GameRoomState.IN_GAME);
            logger.info("将房间 %s 分配给游戏 %s".formatted(gameRoom.getRoomName(), game.getId()));
            game.setState(GameState.WAITING);
            gameList.add(game);

            if (game.gameInit()) return game;
            else {
                // 初始化失败，可能是房间设置问题，比如有个参数没设置
                cancelGame(game);
                return null;
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            logger.error("无法注册游戏%s，原因：无法访问无参构造器，无法创建游戏实例".formatted(gameId));
            throw new RuntimeException(e);
        }
    }

    @Override
    public void startGame(AbstractGame game) {
        game.gameStart();
    }

    @Override
    public void cancelGame(AbstractGame game) {
        game.cancelGame();
        solveGameEnd(game);
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

    public void forceAbortGame(AbstractGame game){
        try {
            game.abortGame();
        }catch (Exception e){
            logger.error("停止游戏时出错，但仍强制执行停止命令: ");
            logger.error(Arrays.toString(e.getStackTrace()));
        }finally {
            solveGameEnd(game);
        }
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
            if (Objects.equals(game.getId(), gameId)) {
                if (game.getState() == GameState.WAITING){
                    if (tryJoin(player, game)) return;
                }else if (game.getState() == GameState.RUNNING && game instanceof MidGameJoinable){
                    // 允许中途加入，且正在运行的游戏
                    if (((MidGameJoinable) game).onPlayerMidJoin(player)) {
                        // 先判断是否允许玩家加入，允许时再将player放入playerList
                        // 和tryJoin的流程相反，后面不合适再改
                        // 只把这一个玩家拉进去
                        MCZJUGameCore.getPlayerManager().joinGame(player, game);
                        return;
                    }
                }
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
    public void joinGame(PlayerExt player, String gameId, String roomName) {
        if (player.isInParty() && !player.isPartyLeader()) {
            player.sender().warn("只有队长能开启游戏");
            return;
        }

        if (player.isInGame()) {
            player.sender().warn("请退出当前游戏，再进行下一个游戏！");
            return;
        }

        var game = getGame(gameId, roomName);

        if (game != null)  {
            // 尝试加入这个房间
            if (game.getState() == GameState.WAITING) {
                tryJoin(player, game);
                // 无法加入时，不提示
                return;
            }

            if (game.getState() == GameState.RUNNING && game instanceof MidGameJoinable joinable) {
                if (joinable.onPlayerMidJoin(player)) {
                    MCZJUGameCore.getPlayerManager().joinGame(player, game);
                } // 无法加入时，不提示
            } else {
                player.sender().warn("游戏已开始，无法中途加入");
            }
        } else {
            // 尝试使用这个房间创建游戏并加入
            AbstractGame newGame = createGame(gameId, roomName);
            if (newGame == null) {
                player.sender().warn("这个房间无法加入，请询问管理员");
            } else {
                tryJoin(player, newGame);
                // 无法加入时，不提示
            }
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

    @Override
    public boolean playerSelectable(String gameId) {
        var gameClass = gameIdMap.get(gameId);
        if (gameClass == null) return false;
        var gameRoomClass = registerGameMap.get(gameClass);
        if (gameRoomClass == null) return false;
        return gameRoomClass.isAnnotationPresent(PlayerSelectable.class);
    }

    @Override
    public @Nullable AbstractGame getGame(String gameId, String roomName) {
        for (AbstractGame game : gameList) {
            if (Objects.equals(game.getId(), gameId)) {
                if (game.getGameRoom().getRoomName().equals(roomName)) {
                    return game;
                }
            }
        }
        return null;
    }

    @Override
    public @NotNull List<AbstractGame> getAllGames() {
        return gameList;
    }
}
