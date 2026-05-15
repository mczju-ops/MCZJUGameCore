package com.github.mczjuops.mczjugamecore.player.data;

public interface AbstractPlayerDataLoader {
    boolean loadAllPlayerData(String gameId, Class<? extends AbstractPlayerData> gameRoomClass);

}
