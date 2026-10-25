package com.github.mczjuops.mczjugamecore.game.room;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class GameRoomManager {

    private final ConsoleSender logger = new ConsoleSender(getClass().getSimpleName());

    private final List<GameRoomLoader> loaders = new LinkedList<>();

    private final Map<String, List<AbstractGameRoom>> gameRoomMap = new HashMap<>();
    private final Map<String, Class<? extends AbstractGameRoom>> roomTypes = new HashMap<>();

    public GameRoomManager() {
        // 传入所有的Loader到责任链中
        loaders.add(new JsonGameRoomLoader());
    }

    /**
     * 记录游戏房间类型并加载该游戏的全部房间，供后续命令重载使用；子插件无需额外登记。责任链模式
     * @param gameId 游戏 ID
     * @param gameRoomClass 游戏房间类
     */
    public void loadGameRoom(String gameId, Class<? extends AbstractGameRoom> gameRoomClass) {
        roomTypes.put(gameId, gameRoomClass);
        for (GameRoomLoader loader : loaders) {
            if (loader.loadAllGameRoom(gameId, gameRoomClass)) {
                break;
            }
        }
    }

    /**
     * 原子替换指定游戏的 JSON 房间；不影响其他游戏，不保存旧快照或重建玩家状态。
     * @param gameId 已注册的游戏 ID
     * @throws Exception 活动对局、未保存修改、占用房间、类型不支持或读取失败；旧房间保持不变
     */
    public void reloadGameRooms(String gameId) throws Exception {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("房间重载必须在服务器线程执行");
        if (!MCZJUGameCore.getGameManager().getRegisteredGameIds().contains(gameId)) {
            throw new IllegalArgumentException("未注册游戏：" + gameId);
        }
        if (MCZJUGameCore.getGameManager().getAllGames().stream().anyMatch(game -> gameId.equals(game.getId()))
                || getGameRooms(gameId).stream().anyMatch(room -> room.getState() == GameRoomState.IN_GAME)) {
            throw new IllegalStateException("该游戏仍有活动对局，请结束后重试");
        }
        if (getGameRooms(gameId).stream().anyMatch(AbstractGameRoom::isModified)) {
            throw new IllegalStateException("该游戏房间有未保存修改，请先保存后重试");
        }
        Class<? extends AbstractGameRoom> roomClass = roomTypes.get(gameId);
        if (roomClass == null) throw new IllegalStateException("未找到该游戏注册的房间类型");
        List<AbstractGameRoom> loaded = new JsonGameRoomLoader().readRooms(gameId, roomClass);
        gameRoomMap.put(gameId, loaded);
    }

    /**
     * 注册游戏地图。创建新游戏或加载老地图后调用此函数。
     * @param gameId 游戏 ID
     * @param gameRoom  游戏房间对象
     */
    public void registerGameRoom(String gameId, AbstractGameRoom gameRoom){
        if (!gameRoomMap.containsKey(gameId)) gameRoomMap.put(gameId, new LinkedList<>());
        gameRoomMap.get(gameId).add(gameRoom);
    }

    /** 异步保存所有修改过的游戏地图 */
    public void saveAllGameRoom(){
        Bukkit.getScheduler().runTaskAsynchronously(MCZJUGameCore.getInstance(), this::saveAllGameRoomDirectly);
    }

    /** 异步保存某个游戏下所有修改过的房间 */
    public void saveGameRoom(String gameId) {
        Bukkit.getScheduler().runTaskAsynchronously(
                MCZJUGameCore.getInstance(),
                () -> saveGameRoomDirectly(gameId)
        );
    }

    /** 异步保存某个指定房间 */
    public void saveGameRoom(String gameId, String roomName) {
        Bukkit.getScheduler().runTaskAsynchronously(
                MCZJUGameCore.getInstance(),
                () -> saveGameRoomDirectly(gameId, roomName)
        );
    }

    /** 直接保存所有修改过的游戏地图 */
    public int saveAllGameRoomDirectly() {
        int count = 0;

        for (List<AbstractGameRoom> gameRoomList : gameRoomMap.values()) {
            count += saveGameRoomListDirectly(gameRoomList);
        }

        return count;
    }

    /** 直接保存某个游戏下所有修改过的房间 */
    public int saveGameRoomDirectly(String gameId) {
        List<AbstractGameRoom> gameRoomList = gameRoomMap.get(gameId);

        if (gameRoomList == null || gameRoomList.isEmpty()) return 0;

        return saveGameRoomListDirectly(gameRoomList);
    }

    /** 直接保存某个指定房间 */
    public boolean saveGameRoomDirectly(String gameId, String roomName) {
        AbstractGameRoom gameRoom = getGameRoom(gameId, roomName);

        if (gameRoom == null) return false;

        if (!gameRoom.isModified()) return false;

        return gameRoom.save();
    }

    /** 保存列表中所有修改过的房间 */
    private int saveGameRoomListDirectly(List<AbstractGameRoom> gameRoomList) {
        int count = 0;

        for (AbstractGameRoom gameRoom : gameRoomList) {
            if (!gameRoom.isModified()) continue;

            boolean success = gameRoom.save();
            if (success) count++;
        }

        return count;
    }

    /**
     * 获取随机一个空闲房间，用于创建游戏
     * @param gameId 游戏 ID
     * @return 游戏房间。没有空闲的，则返回 null
     */
    public @Nullable AbstractGameRoom getRandomLeisureGameRoom(String gameId){
        List<AbstractGameRoom> rooms = gameRoomMap.get(gameId);
        if (rooms == null || rooms.isEmpty()) return null;

        List<AbstractGameRoom> readyRooms = rooms.stream()
                .filter(room -> room.getState() == GameRoomState.READY)
                .toList();

        if (readyRooms.isEmpty()) return null;

        int randomIndex = ThreadLocalRandom.current().nextInt(readyRooms.size());
        return readyRooms.get(randomIndex);
    }


    public AbstractGameRoom createGameRoom(String gameId, String gameRoomName){
        AbstractGameRoom gameRoom = MCZJUGameCore.getGameManager().createGameRoom(gameId, gameRoomName);
        registerGameRoom(gameId, gameRoom);
        gameRoom.save();
        return gameRoom;
    }

    /**
     * 删除房间；被活动游戏占用时先终止该局，清理玩家及该局 Tab 修改。
     * @param gameId 已注册的游戏 ID
     * @param roomName 房间名称
     * @return 是否移除了匹配房间
     */
    public boolean deleteGameRoom(String gameId, String roomName) {
        return gameRoomMap.get(gameId).removeIf(gameRoom -> {
            if (gameRoom.getRoomName().equals(roomName)) {
                AbstractGame game = MCZJUGameCore.getGameManager().getGame(gameId, roomName);
                if (game != null) MCZJUGameCore.getGameManager().forceAbortGame(game);
                gameRoom.deleteRoom();
                return true;
            }
            return false;
        });
    }

    public @Nullable AbstractGameRoom getGameRoom(String gameId, String gameRoomName){
        List<AbstractGameRoom> gameRooms = gameRoomMap.get(gameId);
        if (gameRooms == null) return null;
        for (AbstractGameRoom gameRoom : gameRooms) {
            if (Objects.equals(gameRoom.getRoomName(), gameRoomName)){
                return gameRoom;
            }
        }
        return null;
    }

    public List<AbstractGameRoom> getGameRooms(String gameId) {
        var rooms = gameRoomMap.get(gameId);
        if (rooms == null || rooms.isEmpty()) return List.of();
        return Collections.unmodifiableList(gameRoomMap.get(gameId));
    }

    public Set<String> getGameRoomNames(String gameId) {
        Set<String> names = new HashSet<>();
        List<AbstractGameRoom> gameRooms = gameRoomMap.get(gameId);
        if (gameRooms != null) {
            for (AbstractGameRoom gameRoom : gameRooms) {
                names.add(gameRoom.getRoomName());
            }
        }
        return Collections.unmodifiableSet(names);
    }
}
