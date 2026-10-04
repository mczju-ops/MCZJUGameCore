package com.github.mczjuops.mczjugamecore.lobby;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.serialize.LocationAdapter;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 保存主大厅、小游戏大厅和游戏类别大厅；类别位置独立存储，避免同名 ID 覆盖。 */
public class LobbyManager {
    public static final String MAIN_LOBBY_ID = "main";

    private final ConsoleSender logger = new ConsoleSender("MGC: LobbyManager");
    private final Gson gson = LocationAdapter.getGsonBuilder().newBuilder()
            .setStrictness(Strictness.STRICT).create();
    private final Path storageFile;
    private final Path categoryStorageFile;
    // 保留原始世界名称，避免世界加载顺序或卸载导致位置无法重新保存。
    private final Map<String, JsonObject> categoryLobbies = new HashMap<>();
    private final Map<String, JsonObject> lobbies = new HashMap<>();
    private final Set<Path> unreadableFiles = new HashSet<>();
    private final Map<Path, String> savedContents = new HashMap<>();
    private final Map<Path, Map<String, JsonObject>> savedLocations = new HashMap<>();

    /** 加载两份大厅配置；读取失败的文件禁止写回，仅在主配置文件不存在时迁移旧大厅。 */
    public LobbyManager() {
        storageFile = MCZJUGameCore.getInstance().getDataFolder().toPath().resolve("lobbies.json");
        categoryStorageFile = storageFile.resolveSibling("category-lobbies.json");
        load(storageFile, lobbies, true);
        load(categoryStorageFile, categoryLobbies, false);
        migrateLegacyMainLobby();
    }

    /** @return 主大厅位置的副本；未配置或世界尚未加载时返回 null */
    public @Nullable Location getMainLobby() {
        return getLobby(MAIN_LOBBY_ID);
    }

    /**
     * @param gameId 游戏 ID，不区分大小写
     * @return 游戏大厅位置的副本；未配置或世界尚未加载时返回 null
     */
    public @Nullable Location getGameLobby(String gameId) {
        return getLobby(gameId);
    }

    /**
     * 获取类别大厅位置的副本。
     * @param categoryId config.yml 中的类别 ID
     * @return 未设置类别大厅或所在世界未加载时返回 null；未加载世界的配置仍保留
     */
    public @Nullable Location getCategoryLobby(String categoryId) {
        return resolveLocation(categoryLobbies.get(categoryId));
    }

    /**
     * 设置并保存类别大厅。调用方应在服务器线程调用。
     * @param categoryId 当前配置中存在的类别 ID
     * @param location 属于已加载世界的位置；内部保存副本
     * @return 写入成功时返回 true；写入失败、配置读取失败或磁盘文件被外部修改时保留原设置并返回 false
     * @throws IllegalArgumentException 类别不存在、位置没有已加载的世界或坐标无效
     */
    public boolean setCategoryLobby(String categoryId, Location location) {
        if (!MCZJUGameCore.getConfigManager().getGameCategories().containsKey(categoryId)) {
            throw new IllegalArgumentException("游戏类别不存在：" + categoryId);
        }
        if (location.getWorld() == null) throw new IllegalArgumentException("大厅位置必须属于一个已加载的世界");
        Map<String, JsonObject> updated = new HashMap<>(categoryLobbies);
        updated.put(categoryId, serializeLocation(location));
        if (!save(categoryStorageFile, updated)) return false;
        categoryLobbies.clear();
        categoryLobbies.putAll(updated);
        return true;
    }

    /**
     * 移除并保存类别大厅设置。
     * @param categoryId 类别 ID
     * @return 原来存在设置且保存成功时返回 true，否则返回 false；保存失败或读取失败时保留原设置
     */
    public boolean removeCategoryLobby(String categoryId) {
        if (!categoryLobbies.containsKey(categoryId)) return false;
        Map<String, JsonObject> updated = new HashMap<>(categoryLobbies);
        updated.remove(categoryId);
        if (!save(categoryStorageFile, updated)) return false;
        categoryLobbies.clear();
        categoryLobbies.putAll(updated);
        return true;
    }

    /**
     * 获取大厅位置，每次调用时查找已加载世界。
     * @param lobbyId 大厅 ID，不区分大小写
     * @return 独立的位置副本；未配置或世界尚未加载时返回 null，配置不会被删除
     */
    public @Nullable Location getLobby(String lobbyId) {
        return resolveLocation(lobbies.get(normalize(lobbyId)));
    }

    public boolean hasLobby(String lobbyId) {
        return lobbies.containsKey(normalize(lobbyId));
    }

    public Set<String> getConfiguredLobbyIds() {
        return Set.copyOf(lobbies.keySet());
    }

    /**
     * 设置并立即保存大厅位置。调用方应在服务器线程调用。
     * @param lobbyId 大厅 ID，不区分大小写
     * @param location 属于已加载世界的位置
     * @throws IllegalArgumentException ID 为空、位置没有已加载世界或坐标无效
     * @throws IllegalStateException 保存失败、配置读取失败或磁盘文件被外部修改；原设置保持不变
     */
    public void setLobby(String lobbyId, Location location) {
        if (location.getWorld() == null) throw new IllegalArgumentException("大厅位置必须属于一个已加载的世界");
        Map<String, JsonObject> updated = new HashMap<>(lobbies);
        updated.put(normalize(lobbyId), serializeLocation(location));
        if (!save(storageFile, updated)) throw new IllegalStateException("无法保存大厅配置，请检查服务器日志");
        lobbies.clear();
        lobbies.putAll(updated);
    }

    /**
     * 移除并立即保存大厅设置。
     * @param lobbyId 大厅 ID，不区分大小写
     * @return 原来存在设置且保存成功时返回 true；失败时保留原设置并返回 false
     */
    public boolean removeLobby(String lobbyId) {
        String id = normalize(lobbyId);
        if (!lobbies.containsKey(id)) return false;
        Map<String, JsonObject> updated = new HashMap<>(lobbies);
        updated.remove(id);
        if (!save(storageFile, updated)) return false;
        lobbies.clear();
        lobbies.putAll(updated);
        return true;
    }

    /**
     * 保存主大厅、游戏大厅和类别大厅。设置和移除已即时落盘，未变化的数据不会重复写回。
     * 配置读取失败或磁盘文件被外部修改时禁止覆盖，失败时记录日志。
     */
    public void save() {
        if (!lobbies.equals(savedLocations.get(storageFile))) save(storageFile, lobbies);
        if (!categoryLobbies.equals(savedLocations.get(categoryStorageFile))) save(categoryStorageFile, categoryLobbies);
    }

    private boolean save(Path file, Map<String, JsonObject> locations) {
        if (unreadableFiles.contains(file)) {
            logger.error("拒绝覆盖读取失败的大厅配置 %s，请修复文件后重启插件".formatted(file.getFileName()));
            return false;
        }
        Path temporary = null;
        try {
            if (!matchesSavedFile(file)) return false;
            if (locations.equals(savedLocations.get(file))) return true;
            String contents = gson.toJson(locations, new TypeToken<Map<String, JsonObject>>() {}.getType());
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "lobbies-", ".tmp");
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            if (!matchesSavedFile(file)) return false;
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            savedContents.put(file, contents);
            savedLocations.put(file, Map.copyOf(locations));
            return true;
        } catch (IOException | RuntimeException e) {
            logger.error("无法保存大厅配置 %s：%s".formatted(file.getFileName(), e.getMessage()));
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException e) {
                    logger.warn("无法清理大厅配置临时文件：%s".formatted(e.getMessage()));
                }
            }
        }
    }

    private boolean matchesSavedFile(Path file) throws IOException {
        String currentContents;
        try {
            currentContents = Files.readString(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            currentContents = null;
        }
        if (Objects.equals(currentContents, savedContents.get(file))) return true;
        logger.error("拒绝覆盖已被外部修改的大厅配置 %s，请重启插件以加载磁盘上的配置"
                .formatted(file.toAbsolutePath()));
        return false;
    }

    private void load(Path file, Map<String, JsonObject> locations, boolean normalizeIds) {
        if (Files.notExists(file)) {
            savedLocations.put(file, Map.of());
            logger.info("大厅配置不存在，未创建文件：%s".formatted(file.toAbsolutePath()));
            return;
        }
        try {
            String contents = Files.readString(file, StandardCharsets.UTF_8);
            JsonReader jsonReader = gson.newJsonReader(new StringReader(contents));
            if (jsonReader.peek() != JsonToken.BEGIN_OBJECT) {
                throw new IllegalArgumentException("配置必须是 JSON 对象");
            }
            Map<String, JsonObject> loaded = gson.fromJson(
                    jsonReader, new TypeToken<Map<String, JsonObject>>() {}.getType());
            if (loaded == null) throw new IllegalArgumentException("配置必须是 JSON 对象，不能是空文件或 null");
            if (jsonReader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("配置包含多余的 JSON 内容");
            }
            Map<String, JsonObject> validated = new HashMap<>();
            for (var entry : loaded.entrySet()) {
                String id = normalizeIds ? normalize(entry.getKey()) : entry.getKey();
                if (id.isBlank()) throw new IllegalArgumentException("大厅 ID 不能为空");
                readCoordinates(entry.getValue());
                if (validated.putIfAbsent(id, entry.getValue()) != null) {
                    throw new IllegalArgumentException("大厅 ID 重复：" + id);
                }
            }
            locations.putAll(validated);
            savedContents.put(file, contents);
            savedLocations.put(file, Map.copyOf(validated));
            logger.info("已加载大厅配置 %s，共 %d 条记录".formatted(file.toAbsolutePath(), validated.size()));
        } catch (Exception e) {
            unreadableFiles.add(file);
            logger.error("无法加载大厅配置 %s：%s".formatted(file.getFileName(), e.getMessage()));
        }
    }

    private JsonObject serializeLocation(Location location) {
        location.checkFinite();
        return gson.toJsonTree(location, Location.class).getAsJsonObject();
    }

    private @Nullable Location resolveLocation(@Nullable JsonObject data) {
        if (data == null) return null;
        var world = Bukkit.getWorld(data.get("world").getAsString());
        if (world == null) return null;
        Location location = readCoordinates(data);
        location.setWorld(world);
        return location;
    }

    // 加载时只验证数据，不查询 Bukkit 世界；额外 JSON 字段也会随配置保留。
    private Location readCoordinates(JsonObject data) {
        if (data == null || !data.has("world") || !data.get("world").isJsonPrimitive()
                || !data.getAsJsonPrimitive("world").isString() || data.get("world").getAsString().isBlank()) {
            throw new IllegalArgumentException("大厅位置缺少有效的世界名称");
        }
        Location location = new Location(null, coordinate(data, "x"), coordinate(data, "y"),
                coordinate(data, "z"), (float) coordinate(data, "yaw"), (float) coordinate(data, "pitch"));
        location.checkFinite();
        return location;
    }

    private double coordinate(JsonObject data, String name) {
        // 与旧 LocationAdapter 兼容，缺省坐标或朝向为零。
        if (!data.has(name)) return 0;
        if (!data.get(name).isJsonPrimitive() || !data.getAsJsonPrimitive(name).isNumber()) {
            throw new IllegalArgumentException("大厅位置字段必须是数字：" + name);
        }
        return data.get(name).getAsDouble();
    }

    /** Preserve the old config.yml lobby-spawn setting when upgrading. */
    private void migrateLegacyMainLobby() {
        if (!Files.notExists(storageFile) || unreadableFiles.contains(storageFile)) return;
        try {
            Location legacyLobby = MCZJUGameCore.getConfigManager().getLobbySpawn();
            if (legacyLobby == null || legacyLobby.getWorld() == null) return;
            setLobby(MAIN_LOBBY_ID, legacyLobby);
            logger.info("已将 config.yml 中的旧大厅出生点迁移至 lobbies.json");
        } catch (IllegalStateException e) {
            logger.warn("旧大厅出生点迁移失败（世界未加载或保存失败）：%s".formatted(e.getMessage()));
        }
    }

    private static String normalize(String lobbyId) {
        if (lobbyId == null || lobbyId.isBlank()) throw new IllegalArgumentException("大厅 ID 不能为空");
        return lobbyId.toLowerCase(java.util.Locale.ROOT);
    }
}
