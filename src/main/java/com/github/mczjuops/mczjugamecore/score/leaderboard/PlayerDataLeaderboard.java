package com.github.mczjuops.mczjugamecore.score.leaderboard;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.player.data.AbstractPlayerData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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

    /**
     * 无成绩时的重置值，默认 -100.0。子类可覆盖以适配自身的成绩更新算法。
     *
     * @return 可由成绩字段类型准确表示的重置值
     */
    protected double getResetValue() {
        return -100.0;
    }

    /**
     * 判断成绩是否参与排名；默认只排除重置值，零分仍参与排名。
     *
     * @param value 原始成绩
     * @return 是否为有效成绩
     */
    protected boolean hasRecord(double value) {
        return value != getResetValue();
    }

    /**
     * 按名称（忽略大小写）查找已有玩家数据，只重置本榜的成绩字段并立即保存。
     * 支持 byte、short、int、long、float、double 及其包装类型；保存失败时恢复旧值，
     * 保留修改标记以便后续保存重试。不会删除其他字段或创建新玩家数据。
     *
     * @param playerName 玩家的原始名称，不包含显示格式
     * @return 清除结果
     */
    @Override
    public ClearRecordResult clearPlayerRecord(String playerName) {
        try {
            var allData = MCZJUGameCore.getPlayerDataManager().getAllPlayerData(getPlayerDataClass());
            for (var playerData : allData) {
                String name = Bukkit.getOfflinePlayer(UUID.fromString(playerData.getPlayerID())).getName();
                if (name == null || !name.equalsIgnoreCase(playerName)) continue;

                Field field = playerData.getClass().getField(getFieldName());
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                    throw new IllegalArgumentException("Score field must be a writable instance field: " + getFieldName());
                }
                Object oldValue = field.get(playerData);
                if (!(oldValue instanceof Number number)) {
                    throw new IllegalArgumentException("Score field must contain a number: " + getFieldName());
                }
                if (!hasRecord(number.doubleValue())) return ClearRecordResult.NO_RECORD;

                Object resetValue = convertResetValue(field.getType());
                field.set(playerData, resetValue);
                playerData.setModified(true);
                try {
                    if (!playerData.save()) throw new IllegalStateException("Player data save failed");
                } catch (RuntimeException e) {
                    field.set(playerData, oldValue);
                    playerData.setModified(true);
                    MCZJUGameCore.getInstance().getLogger().warning("清除玩家成绩保存失败：" + e.getMessage());
                    return ClearRecordResult.FAILED;
                }
                return ClearRecordResult.SUCCESS;
            }
            return ClearRecordResult.NO_RECORD;
        } catch (ReflectiveOperationException | RuntimeException e) {
            MCZJUGameCore.getInstance().getLogger().warning("清除玩家成绩失败：" + e.getMessage());
            return ClearRecordResult.FAILED;
        }
    }

    private Object convertResetValue(Class<?> type) {
        double value = getResetValue();
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Reset value must be finite");
        Object converted;
        if (type == double.class || type == Double.class) converted = value;
        else if (type == float.class || type == Float.class) converted = (float) value;
        else if (type == long.class || type == Long.class) converted = (long) value;
        else if (type == int.class || type == Integer.class) converted = (int) value;
        else if (type == short.class || type == Short.class) converted = (short) value;
        else if (type == byte.class || type == Byte.class) converted = (byte) value;
        else throw new IllegalArgumentException("Unsupported score field type: " + type.getName());
        if (((Number) converted).doubleValue() != value) {
            throw new IllegalArgumentException("Reset value cannot be represented by " + type.getName());
        }
        return converted;
    }

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
                if (hasRecord(value)) entries.add(new LeaderboardEntry(displayName, value));
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
