package com.github.mczjuops.mczjugamecore.game.room;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.serialize.JsonMGCSerializable;

public class JsonGameRoom extends AbstractGameRoom implements JsonMGCSerializable {

    @Override
    public String getFilePath(){
        String dataPath = MCZJUGameCore.getInstance().getDataFolder().getAbsolutePath();
        return "%s/rooms/%s/%s.json".formatted(dataPath, getGameId(), getRoomName());
    }

    @Override
    public boolean deleteRoom() {
        return deleteData();
    }

}
