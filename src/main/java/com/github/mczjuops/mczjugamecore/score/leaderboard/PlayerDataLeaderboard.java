package com.github.mczjuops.mczjugamecore.score.leaderboard;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.player.data.AbstractPlayerData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 数据源为 PlayerData 的排行榜
 */
public abstract class PlayerDataLeaderboard extends AbstractLeaderboard {

    @Override
    public List<LeaderboardEntry> fetchEntries() {
        return fetchFromPlayerData();
    }

    /** PlayerData 数据类 */
    protected abstract @NotNull Class<? extends AbstractPlayerData> getPlayerDataClass();

    /** 需要进行排行的字段（double）的字段名 */
    protected abstract @NotNull String getFieldName();

    /** 从 PlayerData 模块获取数据源 */
    private List<LeaderboardEntry> fetchFromPlayerData() {
        List<LeaderboardEntry> entries = new ArrayList<>();

        var allData = MCZJUGameCore.getPlayerDataManager().getAllPlayerData(getPlayerDataClass());

        allData.forEach(playerData -> {
            String playerId = playerData.getPlayerID();
            UUID uuid = UUID.fromString(playerId);
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            String name = offlinePlayer.getName();
            if (name != null) {
                String displayName = "<%s>%s".formatted(offlinePlayer.isOp() ? "dark_red" : "green", name);
                double value = getDoubleValue(playerData, getFieldName());
                entries.add(new LeaderboardEntry(displayName, value));
            }
        });

        return entries;
    }

    private static double getDoubleValue(Object instance, String fieldName) {
        if (instance == null || fieldName == null || fieldName.isEmpty()) return 0.0;

        try {
            Field field = instance.getClass().getField(fieldName);
            Object value = field.get(instance);

            if (value instanceof Number number) {
                return number.doubleValue();
            }

            return 0.0;
        } catch (NoSuchFieldException | IllegalAccessException | SecurityException ignored) {
            return 0.0;
        }
    }
}
