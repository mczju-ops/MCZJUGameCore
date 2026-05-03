package com.github.mczjuops.mczjugamecore.game.room;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.serialize.LocationAdapter;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static java.lang.StringTemplate.STR;

public class JsonGameRoom extends AbstractGameRoom {


    @Override
    public <T> T getField(String name, Class<T> clazz) {
        try {
            Field field = this.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(this);
            return clazz.cast(value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.error(STR."无法访问字段: \{name}, 所属游戏: \{getGameName()}, 游戏房间名: \{getRoomName()}");
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
            logger.error(STR."设置字段失败: \{name}");
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
            logger.error(STR."无法访问字段: \{name}, 所属游戏: \{getGameName()}, 游戏房间名: \{getRoomName()}");
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

        } catch (IOException e) {
            logger.error(STR."无法加载地图 \{getGameName()} : \{getRoomName()}");
            throw new RuntimeException(e);
        }
        return false;
    }

    private String getFilePath(){
        String dataPath = MCZJUGameCore.getInstance().getDataFolder().getAbsolutePath();
        return STR."\{dataPath}/\{getGameName()}/\{getRoomName()}.json";
    }
}
