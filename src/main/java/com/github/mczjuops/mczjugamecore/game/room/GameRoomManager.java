package com.github.mczjuops.mczjugamecore.game.room;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GameRoomManager {

    private final ConsoleSender logger = new ConsoleSender(STR."MGC:\{getClass().getName()}");

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

    /**
     * 保存所有修改过的游戏地图
     */
    public void saveAllGameRoom(){
        for (List<AbstractGameRoom> gameRoomList : gameRoomMap.values()) {
            for (AbstractGameRoom gameRoom : gameRoomList) {
                if (gameRoom.isModified()) gameRoom.save();
            }
        }
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
        return gameRoom;
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
}
