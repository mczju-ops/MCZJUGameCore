package com.github.mczjuops.mczjugamecore.player.data;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.room.GameRoomLoader;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class PlayerDataManager {
    private final ConsoleSender logger = new ConsoleSender(getClass().getSimpleName());
    private final List<AbstractPlayerDataLoader> loaders = new LinkedList<>();

    private final Map<String, Map<String, AbstractPlayerData>> pDataMap = new HashMap<>();

    private BukkitTask autoSaveTask;
    public PlayerDataManager() {
        loaders.add(new JsonPlayerDataLoader());
    }

    public void loadAllPlayerData(String gameId, Class<? extends AbstractPlayerData> dataClass) {
        for (AbstractPlayerDataLoader loader : loaders) {
            if (loader.loadAllPlayerData(gameId, dataClass)) break;
        }
    }
    public void saveAllPlayerData(){
        for (Map<String, AbstractPlayerData> playerDataMap : pDataMap.values()) {
            for (AbstractPlayerData pData : playerDataMap.values()) {
                if (pData.isModified()) {
                    pData.setModified(false);
                    pData.save();
                }
            }
        }
    }

    public void saveAllPlayerDataAsync(){
        Bukkit.getScheduler().runTaskAsynchronously(
                MCZJUGameCore.getInstance(), this::saveAllPlayerData);
    }

    public void savePlayerData(String playerId){
        for (Map<String, AbstractPlayerData> playerDataMap : pDataMap.values()) {
            AbstractPlayerData pData = playerDataMap.get(playerId);
            if (pData == null) continue;
            if (pData.isModified()) {
                pData.save();
            }
        }
    }

    public void savePlayerDataAsync(String playerId){
        Bukkit.getScheduler().runTaskAsynchronously(
                MCZJUGameCore.getInstance(), () -> savePlayerData(playerId));
    }

    public void registerPlayerData(String gameId, Class<? extends AbstractPlayerData> dataClass){
        loadAllPlayerData(gameId, dataClass);
    }

    protected void addPlayerData(String gameId, String playerId, AbstractPlayerData data){
        // 获取或创建该游戏ID对应的玩家数据映射
        Map<String, AbstractPlayerData> playerDataMap = pDataMap.computeIfAbsent(gameId, k -> new HashMap<>());

        // 将玩家数据添加到映射中
        playerDataMap.put(playerId, data);
        logger.debug("Added player data for gameId: %s, playerId: %s".formatted(gameId, playerId));
    }

    public @NotNull <T extends AbstractPlayerData> T getPlayerData(String gameId, String playerId, Class<T> dataClass){
        // 获取该游戏ID对应的玩家数据映射
        Map<String, AbstractPlayerData> playerDataMap = pDataMap.computeIfAbsent(gameId, k -> new HashMap<>());

        // 获取玩家数据
        AbstractPlayerData playerData = playerDataMap.get(playerId);

        if (playerData == null) {
            // 没有这个玩家的数据，自动创建一个新的
            try {
                playerData = dataClass.getDeclaredConstructor().newInstance();
                playerData.setGameID(gameId);
                playerData.setPlayerID(playerId);
                playerDataMap.put(playerId, playerData);
                logger.debug("Created new player data for gameId: %s, playerId: %s".formatted(gameId, playerId));
            } catch (Exception e) {
                logger.error("Failed to create new instance of %s: %s".formatted(dataClass.getSimpleName(), e.getMessage()));
                throw new RuntimeException(e);
            }
        }

        // 类型检查和转换
        if (dataClass.isInstance(playerData)) {
            return dataClass.cast(playerData);
        } else {
            String msg = "Player data type mismatch. Expected: %s, Actual: %s"
                    .formatted(dataClass.getSimpleName(), playerData.getClass().getSimpleName());
            throw new RuntimeException(msg);
        }
    }

    public void startAutoSave(long intervalTicks) {
        MCZJUGameCore plugin = MCZJUGameCore.getInstance();

        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }

        autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::saveAllPlayerData,
                intervalTicks,
                intervalTicks
        );
    }
}
