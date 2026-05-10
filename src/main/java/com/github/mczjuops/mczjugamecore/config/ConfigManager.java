package com.github.mczjuops.mczjugamecore.config;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;

public class ConfigManager {

    private boolean debug;
    private String mainServer; // velocity 主服

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
    }

    public boolean isDebug() {
        return debug;
    }

    public String getMainServer() {
        return mainServer;
    }
}
