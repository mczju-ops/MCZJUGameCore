package com.github.mczjuops.mczjugamecore.score.leaderboard.textdisplay;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.serialize.JsonMGCSerializable;

public class JsonTextDisplayRecord extends TextDisplayRecord implements JsonMGCSerializable {

    public JsonTextDisplayRecord(String leaderboardId, String entityId) {
        super(leaderboardId, entityId);
    }

    @Override
    public String getFilePath() {
        String dataPath = MCZJUGameCore.getInstance().getDataFolder().getAbsolutePath();
        return "%s/text_displays/%s/%s.json".formatted(dataPath, getLeaderboardId(), getEntityId());
    }
}
