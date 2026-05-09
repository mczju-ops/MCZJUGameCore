package com.github.mczjuops.mczjugamecore.game.room;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
     * @param gameName  游戏名
     * @param gameRoomClass 游戏房间类
     */
    public void loadGameRoom(String gameName, Class<? extends AbstractGameRoom> gameRoomClass) {
        for (GameRoomLoader loader : loaders) {
            if (loader.loadAllGameRoom(gameName, gameRoomClass)) {
                break;
            }
        }
    }

    /**
     * 注册游戏地图。创建新游戏或加载老地图后调用此函数。
     * @param gameName  游戏名
     * @param gameRoom  游戏房间对象
     */
    public void registerGameRoom(String gameName, AbstractGameRoom gameRoom){
        if (!gameRoomMap.containsKey(gameName)) gameRoomMap.put(gameName, new LinkedList<>());
        gameRoomMap.get(gameName).add(gameRoom);
    }

    /** 异步保存所有修改过的游戏地图 */
    public void saveAllGameRoom(){
        Bukkit.getScheduler().runTaskAsynchronously(MCZJUGameCore.getInstance(), this::saveAllGameRoomDirectly);
    }

    /** 异步保存某个游戏下所有修改过的房间 */
    public void saveGameRoom(String gameName) {
        Bukkit.getScheduler().runTaskAsynchronously(
                MCZJUGameCore.getInstance(),
                () -> saveGameRoomDirectly(gameName)
        );
    }

    /** 异步保存某个指定房间 */
    public void saveGameRoom(String gameName, String roomName) {
        Bukkit.getScheduler().runTaskAsynchronously(
                MCZJUGameCore.getInstance(),
                () -> saveGameRoomDirectly(gameName, roomName)
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
    public int saveGameRoomDirectly(String gameName) {
        List<AbstractGameRoom> gameRoomList = gameRoomMap.get(gameName);

        if (gameRoomList == null || gameRoomList.isEmpty()) return 0;

        return saveGameRoomListDirectly(gameRoomList);
    }

    /** 直接保存某个指定房间 */
    public boolean saveGameRoomDirectly(String gameName, String roomName) {
        AbstractGameRoom gameRoom = getGameRoom(gameName, roomName);

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
     * 获取空闲的地图，用于创建游戏
     * @param gameName  游戏名
     * @return  游戏地图。没有空闲的，则返回null
     */
    public @Nullable AbstractGameRoom getLeisureGameRoom(String gameName){
        if (!gameRoomMap.containsKey(gameName)) return null;
        for (AbstractGameRoom gameRoom : gameRoomMap.get(gameName)) {
            if (gameRoom.getState() == GameRoomState.READY) return gameRoom;
        }
        return null;
    }


    public AbstractGameRoom createGameRoom(String gameName, String gameRoomName){
        AbstractGameRoom gameRoom = MCZJUGameCore.getGameManager().createGameRoom(gameName, gameRoomName);
        registerGameRoom(gameName, gameRoom);
        gameRoom.save();
        return gameRoom;
    }

    public boolean deleteGameRoom(String gameName, String roomName) {
        return gameRoomMap.get(gameName).removeIf(gameRoom -> {
            if (gameRoom.getRoomName().equals(roomName)) {
                gameRoom.deleteRoom();
                return true;
            }
            return false;
        });
    }

    public @Nullable AbstractGameRoom getGameRoom(String gameName, String gameRoomName){
        List<AbstractGameRoom> gameRooms = gameRoomMap.get(gameName);
        if (gameRooms == null) return null;
        for (AbstractGameRoom gameRoom : gameRooms) {
            if (Objects.equals(gameRoom.getRoomName(), gameRoomName)){
                return gameRoom;
            }
        }
        return null;
    }

    public Set<String> getGameRoomNames(String gameName) {
        Set<String> names = new HashSet<>();
        List<AbstractGameRoom> gameRooms = gameRoomMap.get(gameName);
        if (gameRooms != null) {
            for (AbstractGameRoom gameRoom : gameRooms) {
                names.add(gameRoom.getRoomName());
            }
        }
        return Collections.unmodifiableSet(names);
    }
}
