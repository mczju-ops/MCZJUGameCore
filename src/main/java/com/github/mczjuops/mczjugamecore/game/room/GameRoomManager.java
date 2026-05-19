package com.github.mczjuops.mczjugamecore.game.room;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class GameRoomManager {

    private final ConsoleSender logger = new ConsoleSender(getClass().getSimpleName());

    private final List<GameRoomLoader> loaders = new LinkedList<>();

    private final Map<String, List<AbstractGameRoom>> gameRoomMap = new HashMap<>();

    public GameRoomManager() {
        // 传入所有的Loader到责任链中
        loaders.add(new JsonGameRoomLoader());
    }

    /**
     * 加载某个游戏的所有房间。责任链模式
     * @param gameId 游戏 ID
     * @param gameRoomClass 游戏房间类
     */
    public void loadGameRoom(String gameId, Class<? extends AbstractGameRoom> gameRoomClass) {
        for (GameRoomLoader loader : loaders) {
            if (loader.loadAllGameRoom(gameId, gameRoomClass)) {
                break;
            }
        }
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

    public boolean deleteGameRoom(String gameId, String roomName) {
        return gameRoomMap.get(gameId).removeIf(gameRoom -> {
            if (gameRoom.getRoomName().equals(roomName)) {
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
