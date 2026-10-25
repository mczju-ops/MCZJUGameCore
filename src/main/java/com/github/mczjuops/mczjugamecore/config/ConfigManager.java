package com.github.mczjuops.mczjugamecore.config;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public class ConfigManager {

    private Map<String, GameCategory> gameCategories = Map.of();
    private final Map<String, String> gameCategoryAssignments = new LinkedHashMap<>();

    private boolean debug;
    private String mainServer; // velocity 主服
    private ConfigLocation lobbySpawn; // 大厅出生点
    private boolean lobbyProfileFeaturesEnabled;

    private final ConsoleSender logger = new ConsoleSender("MGC: %s".formatted(getClass().getSimpleName()));

    public ConfigManager() {
        load();
    }

    private void load() {
        MCZJUGameCore.getInstance().saveDefaultConfig();
        reload();
    }

    /** 兼容入口：依次重载核心配置和类别分配；任一失败时抛出异常。命令层通过 ReloadManager 隔离失败。 */
    public void reload() {
        reloadCoreConfig();
        reloadCategoryAssignments();
    }

    /**
     * 只读取 config.yml（核心设置及类别定义），不读取类别分配或大厅文件。
     * 解析失败保留原配置；菜单重新打开后更新，玩家档案不在此处切换或重新加载。
     * @throws IllegalStateException 文件读取或 YAML 解析失败
     */
    public void reloadCoreConfig() {
        MCZJUGameCore plugin = MCZJUGameCore.getInstance();
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(plugin.getDataFolder().toPath().resolve("config.yml").toFile());
        } catch (Exception e) {
            throw new IllegalStateException("无法读取 config.yml：" + e.getMessage(), e);
        }
        ConfigLocation spawn = loadLocation(config);
        loadGameCategories(config);
        plugin.reloadConfig();
        debug = config.getBoolean("debug", false);
        mainServer = config.getString("main-server", "main");
        lobbyProfileFeaturesEnabled = config.getBoolean("enable-lobby-profile-features", true);
        lobbySpawn = spawn;
        if (lobbySpawn == null) logger.warn("config.yml 未正确配置大厅出生点");
    }

    private void loadGameCategories(FileConfiguration config) {
        Map<String, GameCategory> categories = new LinkedHashMap<>();
        var section = config.getConfigurationSection("game-categories");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                var category = section.getConfigurationSection(id);
                if (category == null || id.isBlank()) continue;
                String name = category.getString("name", id);
                String iconName = category.getString("icon", "CHEST");
                Material icon = Material.matchMaterial(iconName);
                if (icon == null || icon.isAir() || !icon.isItem()) {
                    logger.warn("游戏类别 %s 的图标 %s 无效，使用 CHEST".formatted(id, iconName));
                    icon = Material.CHEST;
                }
                categories.put(id, new GameCategory(name, category.getStringList("lore"), icon));
            }
        }
        gameCategories = Collections.unmodifiableMap(categories);
    }

    private Path categoryStorageFile() {
        return MCZJUGameCore.getInstance().getDataFolder().toPath().resolve("game-categories.yml");
    }

    /**
     * 只重载 game-categories.yml 中的游戏类别分配，不重读 config.yml。
     * 缺省文件视为空分配；解析失败保留旧分配；有效类别由当前类别定义动态解析。
     * @throws IllegalStateException 读取、解析失败或分配值不是字符串
     */
    public void reloadCategoryAssignments() {
        Path file = categoryStorageFile();
        YamlConfiguration storage = new YamlConfiguration();
        // 游戏 ID 可以含点号，不能将其解释为 YAML 路径。
        storage.options().pathSeparator('\0');
        try {
            if (!Files.notExists(file)) storage.load(file.toFile());
            Map<String, String> loaded = new LinkedHashMap<>();
            for (var entry : storage.getValues(false).entrySet()) {
                if (!(entry.getValue() instanceof String categoryId) || entry.getKey().isBlank()) {
                    throw new IllegalArgumentException("类别分配必须为游戏 ID 到类别 ID 的字符串映射");
                }
                loaded.put(entry.getKey(), categoryId);
            }
            gameCategoryAssignments.clear();
            gameCategoryAssignments.putAll(loaded);
        } catch (Exception e) {
            throw new IllegalStateException("无法加载游戏分类：" + e.getMessage(), e);
        }
    }

    /**
     * 返回按 config.yml 顺序排列的只读类别表；空表或单个类别时主菜单显示全部游戏。
     * @return 类别 ID 到展示信息的映射
     */
    public Map<String, GameCategory> getGameCategories() {
        return gameCategories;
    }

    /**
     * 获取游戏的有效类别。未分类或原类别已删除时归入配置中的第一个类别。
     * @param gameId 注册的游戏 ID
     * @return 类别 ID；没有配置类别时返回 null
     */
    public @Nullable String getGameCategory(String gameId) {
        String categoryId = gameCategoryAssignments.get(gameId);
        if (categoryId != null && gameCategories.containsKey(categoryId)) return categoryId;
        return gameCategories.isEmpty() ? null : gameCategories.keySet().iterator().next();
    }

    /**
     * 设置并保存游戏类别；保存失败时保留原分类。调用方应在服务器线程调用。
     * @param gameId 已注册的游戏 ID
     * @param categoryId 当前配置中存在的类别 ID
     * @return 游戏和类别有效且写入成功时返回 true，否则返回 false
     */
    public boolean setGameCategory(String gameId, String categoryId) {
        if (!MCZJUGameCore.getGameManager().getRegisteredGameIds().contains(gameId)
                || !gameCategories.containsKey(categoryId)) return false;
        Map<String, String> updated = new LinkedHashMap<>(gameCategoryAssignments);
        updated.put(gameId, categoryId);
        YamlConfiguration storage = new YamlConfiguration();
        storage.options().pathSeparator('\0');
        updated.forEach(storage::set);
        Path file = categoryStorageFile();
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "game-categories-", ".tmp");
            storage.save(temporary.toFile());
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            gameCategoryAssignments.clear();
            gameCategoryAssignments.putAll(updated);
            return true;
        } catch (IOException e) {
            logger.error("无法保存游戏分类：%s".formatted(e.getMessage()));
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException e) {
                    logger.warn("无法清理游戏分类临时文件：%s".formatted(e.getMessage()));
                }
            }
        }
    }

    private ConfigLocation loadLocation(FileConfiguration config) {

        var section = config.getConfigurationSection("lobby-spawn");
        if (section == null) return null;

        String rawWorld = section.getString("world-key");
        if (rawWorld == null) return null;

        NamespacedKey key = NamespacedKey.fromString(rawWorld);
        if (key == null) return null;

        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw");
        float pitch = (float) section.getDouble("pitch");
        return new ConfigLocation(key, x, y, z, yaw, pitch);
    }

    public boolean isDebug() {
        return debug;
    }

    public String getMainServer() {
        return mainServer;
    }

    public boolean isLobbyProfileFeaturesEnabled() {
        return lobbyProfileFeaturesEnabled;
    }

    public @Nullable Location getLobbySpawn() {
        if (lobbySpawn == null) return null;
        return lobbySpawn.toLocation();
    }

    private record ConfigLocation(
            NamespacedKey worldKey,
            double x, double y, double z,
            float yaw, float pitch
    ) {
        public Location toLocation() {
            World world = Bukkit.getWorld(worldKey);
            if (world == null) {
                throw new IllegalStateException("World not loaded: " + worldKey);
            }
            return new Location(world, x, y, z, yaw, pitch);
        }
    }
}
