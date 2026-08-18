package com.github.mczjuops.mczjugamecore.lobby;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.serialize.LocationAdapter;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Stores the main lobby and the waiting lobby of every registered game. */
public class LobbyManager {
    public static final String MAIN_LOBBY_ID = "main";

    private final ConsoleSender logger = new ConsoleSender("MGC: LobbyManager");
    private final Gson gson = LocationAdapter.getGsonBuilder();
    private final Path storageFile;
    private final Map<String, Location> lobbies = new HashMap<>();

    public LobbyManager() {
        storageFile = MCZJUGameCore.getInstance().getDataFolder().toPath().resolve("lobbies.json");
        load();
        migrateLegacyMainLobby();
    }

    public @Nullable Location getMainLobby() {
        return getLobby(MAIN_LOBBY_ID);
    }

    public @Nullable Location getGameLobby(String gameId) {
        return getLobby(gameId);
    }

    public @Nullable Location getLobby(String lobbyId) {
        Location location = lobbies.get(normalize(lobbyId));
        return location == null ? null : location.clone();
    }

    public boolean hasLobby(String lobbyId) {
        return lobbies.containsKey(normalize(lobbyId));
    }

    public Set<String> getConfiguredLobbyIds() {
        return Set.copyOf(lobbies.keySet());
    }

    public void setLobby(String lobbyId, Location location) {
        if (location.getWorld() == null) throw new IllegalArgumentException("大厅位置必须属于一个已加载的世界");
        lobbies.put(normalize(lobbyId), location.clone());
        save();
    }

    public boolean removeLobby(String lobbyId) {
        if (lobbies.remove(normalize(lobbyId)) == null) return false;
        save();
        return true;
    }

    public void save() {
        try {
            Files.createDirectories(storageFile.getParent());
            try (Writer writer = Files.newBufferedWriter(storageFile, StandardCharsets.UTF_8)) {
                gson.toJson(lobbies, new TypeToken<Map<String, Location>>() {}.getType(), writer);
            }
        } catch (IOException e) {
            logger.error("无法保存大厅配置：%s".formatted(e.getMessage()));
        }
    }

    private void load() {
        if (!Files.exists(storageFile)) return;
        try (Reader reader = Files.newBufferedReader(storageFile, StandardCharsets.UTF_8)) {
            Map<String, Location> loaded = gson.fromJson(
                    reader, new TypeToken<Map<String, Location>>() {}.getType());
            if (loaded != null) loaded.forEach((id, location) -> {
                if (location != null) lobbies.put(normalize(id), location);
            });
        } catch (Exception e) {
            logger.error("无法加载大厅配置：%s".formatted(e.getMessage()));
        }
    }

    /** Preserve the old config.yml lobby-spawn setting when upgrading. */
    private void migrateLegacyMainLobby() {
        if (hasLobby(MAIN_LOBBY_ID)) return;
        try {
            Location legacyLobby = MCZJUGameCore.getConfigManager().getLobbySpawn();
            if (legacyLobby == null || legacyLobby.getWorld() == null) return;
            setLobby(MAIN_LOBBY_ID, legacyLobby);
            logger.info("已将 config.yml 中的旧大厅出生点迁移至 lobbies.json");
        } catch (IllegalStateException e) {
            logger.warn("旧大厅出生点所在世界尚未加载，未执行迁移：%s".formatted(e.getMessage()));
        }
    }

    private static String normalize(String lobbyId) {
        if (lobbyId == null || lobbyId.isBlank()) throw new IllegalArgumentException("大厅 ID 不能为空");
        return lobbyId.toLowerCase(java.util.Locale.ROOT);
    }
}
