package com.github.mczjuops.mczjugamecore.player.data;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class PlayerDataManager {
    private final ConsoleSender logger = new ConsoleSender(getClass().getSimpleName());
    private final List<AbstractPlayerDataLoader> loaders = new LinkedList<>();

    private final Map<String, Map<String, AbstractPlayerData>> pDataMap = new HashMap<>();

    private final Map<String, Class<? extends AbstractPlayerData>> registeredPlayerData = new HashMap<>();

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

    /**
     * 注册玩家数据类
     * @param gameId 建议填游戏ID，但实际上填啥都行
     * @param dataClass 你的数据类
     */
    public void registerPlayerData(String gameId, Class<? extends AbstractPlayerData> dataClass){
        registeredPlayerData.put(gameId, dataClass);
        loadAllPlayerData(gameId, dataClass);
    }

    protected void addEmptyData(String gameId) {
        pDataMap.computeIfAbsent(gameId, k -> new HashMap<>());
        logger.debug("Added empty player data map for gameId: %s".formatted(gameId));
    }

    protected void addPlayerData(String gameId, String playerId, AbstractPlayerData data){
        // 获取或创建该游戏ID对应的玩家数据映射
        Map<String, AbstractPlayerData> playerDataMap = pDataMap.computeIfAbsent(gameId, k -> new HashMap<>());

        // 将玩家数据添加到映射中
        playerDataMap.put(playerId, data);
        logger.debug("Added player data for gameId: %s, playerId: %s".formatted(gameId, playerId));
    }

    /**
     * 获取playerData，不做类型检查和转换，一般用于获取其它游戏的playerData
     * @param gameId    游戏ID
     * @param playerId  玩家uuid
     * @return  玩家数据，如果游戏ID不存在，则返回空
     */
    public @Nullable AbstractPlayerData getPlayerData(String gameId, String playerId){
        // 获取该游戏ID对应的玩家数据映射
        Map<String, AbstractPlayerData> playerDataMap = pDataMap.get(gameId);
        if (playerDataMap == null) throw new RuntimeException("%s 数据类未注册，无法获取玩家%s的数据".formatted(gameId, playerId));

        // 获取玩家数据
        AbstractPlayerData playerData = playerDataMap.get(playerId);

        if (playerData == null) {
            // 没有这个玩家的数据，自动创建一个新的
            Class<? extends AbstractPlayerData> playerDataClass = registeredPlayerData.get(gameId);
            if (playerDataClass == null) return null;
            try {
                playerData = playerDataClass.getDeclaredConstructor().newInstance();
                playerData.setGameID(gameId);
                playerData.setPlayerID(playerId);
                playerDataMap.put(playerId, playerData);
                logger.debug("Created new player data for gameId: %s, playerId: %s".formatted(gameId, playerId));
            } catch (Exception e) {
                logger.error("Failed to create new instance of %s: %s".formatted(playerDataClass.getSimpleName(), e.getMessage()));
                throw new RuntimeException(e);
            }
        }
        return playerData;
    }

    /**
     * 获取玩家数据，并自动做类型转换，推荐用这个
     * @param playerId  玩家uuid
     * @param dataClass 数据类
     * @return  转换过的playerData
     * @param <T>   数据类
     */
    public @NotNull <T extends AbstractPlayerData> T getPlayerData(String playerId, Class<T> dataClass){
        String gameId = registeredPlayerData.entrySet().stream()
                .filter(entry -> Objects.equals(dataClass, entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (gameId == null) throw new RuntimeException("数据类 %s 未注册，无法获取玩家 %s 的数据".formatted(dataClass.getSimpleName(), playerId));

        // 获取该游戏ID对应的玩家数据映射
        AbstractPlayerData playerData = getPlayerData(gameId, playerId);
        if (playerData == null) throw new RuntimeException("%s 数据类未注册，无法获取玩家%s的数据".formatted(gameId, playerId));

        // 类型检查和转换
        if (dataClass.isInstance(playerData)) {
            return dataClass.cast(playerData);
        } else {
            String msg = "Player data type mismatch. Expected: %s, Actual: %s"
                    .formatted(dataClass.getSimpleName(), playerData.getClass().getSimpleName());
            throw new RuntimeException(msg);
        }
    }

    /**
     * 获取所有玩家的数据，仅返回已经有数据的
     * @param dataClass 数据类
     * @return  所有玩家的数据
     * @param <T> 数据类
     */
    public @NotNull <T extends AbstractPlayerData> List<T> getAllPlayerData(Class<T> dataClass){
        String gameId = registeredPlayerData.entrySet().stream()
                .filter(entry -> Objects.equals(dataClass, entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (gameId == null) throw new RuntimeException("数据类 %s 未注册，无法获取所有玩家的数据".formatted(dataClass.getSimpleName()));

        Map<String, AbstractPlayerData> playerDataMap = pDataMap.get(gameId);
        if (playerDataMap == null) throw new RuntimeException("%s 数据类未注册，无法获取所有玩家的数据".formatted(gameId));

        return playerDataMap.values().stream()
                .map(dataClass::cast)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有玩家的数据，但不做类型转换，用于强制获取其它游戏的数据
     * @param gameId    游戏ID
     * @return  gameID对应的所有玩家的数据，当不存在这个游戏时，返回null
     */
    public @Nullable List<AbstractPlayerData> getAllPlayerData(String gameId){
        Map<String, AbstractPlayerData> playerDataMap = pDataMap.get(gameId);
        if (playerDataMap == null) return null;

        return playerDataMap.values().stream().toList();
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
