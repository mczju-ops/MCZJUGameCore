package com.github.mczjuops.mczjugamecore.score.leaderboard.textdisplay;

import com.github.mczjuops.mczjugamecore.serialize.MGCSerializable;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public abstract class TextDisplayRecord implements MGCSerializable {

    public String leaderboardId;
    public String entityId;
    public UUID uuid;
    @Nullable public Location location;
    public TextDisplayProperties properties;

    private boolean modified = false;

    public TextDisplayRecord(String leaderboardId, String entityId) {
        this.leaderboardId = leaderboardId;
        this.entityId = entityId;
        this.properties = new TextDisplayProperties();
    }

    public String getLeaderboardId() {
        return leaderboardId;
    }

    public String getEntityId() {
        return entityId;
    }

    public UUID getUniqueId() {
        return uuid;
    }

    public void setUniqueId(UUID uuid) {
        this.uuid = uuid;
    }

    public @Nullable Location getLocation() {
        return location;
    }
    public void setLocation(@Nullable Location location) {
        this.location = location;
    }

    public TextDisplayProperties getProperties() {
        return properties;
    }

    @Override
    public boolean isModified() {
        return modified;
    }

    @Override
    public void setModified(boolean modified) {
        this.modified = modified;
    }
}
