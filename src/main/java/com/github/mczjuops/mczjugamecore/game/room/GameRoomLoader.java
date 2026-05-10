package com.github.mczjuops.mczjugamecore.game.room;

public interface GameRoomLoader {

    /**
     * 加载所有本游戏的game room
     * @param gameId 游戏 ID
     * @param gameRoomClass 游戏房间的类
     * @return  如果这个game room能由本加载器加载，则返回true，否则返回false（责任链模式）
     */
    boolean loadAllGameRoom(String gameId, Class<? extends AbstractGameRoom> gameRoomClass);
}
