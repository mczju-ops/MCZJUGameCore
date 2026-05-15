package com.github.mczjuops.mczjugamecore.player.data;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.serialize.JsonMGCSerializable;

public class JsonPlayerData extends AbstractPlayerData implements JsonMGCSerializable {
    @Override
    public String getFilePath() {
        String dataPath = MCZJUGameCore.getInstance().getDataFolder().getAbsolutePath();
        return "%s/player/%s/%s.json".formatted(dataPath, getGameID(), getPlayerID());
    }
}
