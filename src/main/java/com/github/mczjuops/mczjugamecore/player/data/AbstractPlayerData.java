package com.github.mczjuops.mczjugamecore.player.data;

import com.github.mczjuops.mczjugamecore.game.room.GameRoomState;
import com.github.mczjuops.mczjugamecore.serialize.MGCSerializable;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 玩家数据，继承此类即可自动保存玩家相关的游戏数据
 * 用法和GameRoom类似
 */
public abstract class AbstractPlayerData implements MGCSerializable {
    private String playerID; // 由Loader注入
    private String gameID;

    private boolean modified = false;   // 是否修改过。设置为true，则使用指令，或服务终止时，将自动保存。


    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    String getPlayerID() {
        return playerID;
    }

    void setPlayerID(String playerID) {
        this.playerID = playerID;
    }

    String getGameID() {
        return gameID;
    }

    void setGameID(String gameID) {
        this.gameID = gameID;
    }
}
