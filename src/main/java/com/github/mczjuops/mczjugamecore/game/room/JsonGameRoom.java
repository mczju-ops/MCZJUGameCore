package com.github.mczjuops.mczjugamecore.game.room;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.serialize.LocationAdapter;
import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class JsonGameRoom extends AbstractGameRoom {


    @Override
    public <T> T getField(String name, Class<T> clazz) {
        try {
            Field field = this.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(this);
            return clazz.cast(value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.error("无法访问字段：%s，所属游戏：%s，游戏房间名：%s".formatted(name, getGameName(), getRoomName()));
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setField(String name, Object value) {
        try {
            Field field = this.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(this, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.error("设置字段失败：%s".formatted(name));
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<String, Class<?>> getAllFields() {
        Field[] fields = this.getClass().getDeclaredFields();
        HashMap<String, Class<?>> fieldMap = new HashMap<>();
        for (Field field : fields) {
            fieldMap.put(field.getName(), field.getType());
        }
        return fieldMap;
    }

    @Override
    public @NotNull Class<?> getFieldType(String name) {
        try {
            Field field = this.getClass().getDeclaredField(name);
            return field.getType();
        } catch (NoSuchFieldException e) {
            logger.error("无法访问字段：%s，所属游戏：%s，游戏房间名：%s".formatted(name, getGameName(), getRoomName()));
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean save() {
        String path = getFilePath();
        File file = new File(path);
        try {
            // 确保父目录存在
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                boolean _ = parent.mkdirs();
            }

            Gson gson = LocationAdapter.getGsonBuilder();

            // 写入 JSON
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                gson.toJson(this, writer);
            }
            logger.success("成功保存地图 %s: %s".formatted(getGameName(), getRoomName()));

        } catch (IOException e) {
            logger.error("无法保存地图 %s: %s".formatted(getGameName(), getRoomName()));
            throw new RuntimeException(e);
        }
        return true;
    }

    private String getFilePath(){
        String dataPath = MCZJUGameCore.getInstance().getDataFolder().getAbsolutePath();
        return "%s/%s/%s.json".formatted(dataPath, getGameName(), getRoomName());
    }

    @Override
    public boolean deleteRoom() {
        String path = getFilePath();
        Path filePath = Path.of(path);
        String roomName = getRoomName();

        try {
            boolean deleted = Files.deleteIfExists(filePath);

            if (deleted) {
                logger.success("成功删除地图 %s: %s".formatted(getGameName(), roomName));
            } else {
                logger.warn("地图文件不存在 %s: %s".formatted(getGameName(), roomName));
            }

            return deleted;

        } catch (IOException e) {
            logger.error("无法删除地图 %s: %s".formatted(getGameName(), roomName));
            throw new RuntimeException(e);
        }
    }
}
