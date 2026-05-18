package com.github.mczjuops.mczjugamecore.config;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

public class ConfigManager {

    private boolean debug;
    private String mainServer; // velocity 主服
    private ConfigLocation lobbySpawn; // 大厅出生点

    private final ConsoleSender logger = new ConsoleSender("MGC: %s".formatted(getClass().getSimpleName()));

    public ConfigManager() {
        load();
    }

    private void load() {
        MCZJUGameCore.getInstance().saveDefaultConfig();
        reload();
    }

    public void reload() {
        MCZJUGameCore plugin = MCZJUGameCore.getInstance();
        plugin.reloadConfig();
        var config = plugin.getConfig();
        debug = config.getBoolean("debug", false);
        mainServer = config.getString("main-server", "main");
        lobbySpawn = loadLocation(config);
        if (lobbySpawn == null) logger.warn("config.yml 未正确配置大厅出生点");
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
