package com.github.mczjuops.mczjugamecore.game.room;

public interface GameRoomLoader {

    /**
     * 加载所有本游戏的game room
     * @param gameName  游戏名
     * @param gameRoomClass 游戏房间的类
     * @return  如果这个game room能由本加载器加载，则返回true，否则返回false（责任链模式）
     */
    boolean loadAllGameRoom(String gameName, Class<? extends AbstractGameRoom> gameRoomClass);
}
