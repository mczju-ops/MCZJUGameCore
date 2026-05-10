package com.github.mczjuops.mczjugamecore.profile;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProfileData {

    /** YAML 格式版本，留作未来数据迁移用 */
    public static final int FORMAT_VERSION = 1;

    /** 大厅（非游戏状态）的固定 Profile ID */
    public static final String LOBBY_PROFILE_ID = "lobby";

    private final UUID uuid;
    private volatile String playerName;
    private volatile String currentProfileId;
    private final ConcurrentHashMap<String, PlayerSnapshot> profiles;
    private volatile long lastModified;

    /** 首次上线时构造（磁盘无历史文件） */
    public ProfileData(UUID uuid, String playerName, String initialProfileId) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.currentProfileId = initialProfileId;
        this.profiles = new ConcurrentHashMap<>();
        this.lastModified = System.currentTimeMillis();
    }

    /** 从文件恢复时构造。 */
    public ProfileData(
            UUID uuid, String playerName, String currentProfileId,
            Map<String, PlayerSnapshot> profiles, long lastModified
    ) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.currentProfileId = currentProfileId;
        this.profiles = new ConcurrentHashMap<>(profiles);
        this.lastModified = lastModified;
    }

    // 只读访问

    public UUID uuid() { return uuid; }
    public String playerName() { return playerName; }
    public String currentProfileId() { return currentProfileId; }
    public long lastModified() { return lastModified; }

    public @Nullable PlayerSnapshot getProfile(String profileId) {
        return profiles.get(profileId);
    }

    // 写入（主线程调用）

    public void setPlayerName(String name) { this.playerName = name; }

    public void setCurrentProfileId(String id) {
        this.currentProfileId = id;
        touch();
    }

    public void putProfile(String profileId, PlayerSnapshot snapshot) {
        profiles.put(profileId, snapshot);
        touch();
    }

    public void removeProfile(String profileId) {
        profiles.remove(profileId);
        touch();
    }

    private void touch() { this.lastModified = System.currentTimeMillis(); }

    /**
     * 返回当前所有 profile 的不可变副本
     * 在主线程的"切换完成"或"下线"时刻调用一次
     * 产生的快照可安全传递给任意异步线程，不受后续主线程修改影响
     */
    public Map<String, PlayerSnapshot> snapshotForSave() {
        return Map.copyOf(profiles);
    }
}
