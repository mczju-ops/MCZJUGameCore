package com.github.mczjuops.mczjugamecore.serialize;

import com.github.mczjuops.mczjugamecore.utils.sender.Sender;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public interface JsonMGCSerializable extends MGCSerializable {

    @Override
    default <T> T getField(String name, Class<T> clazz) {
        try {
            Field field = this.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(this);
            return clazz.cast(value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            getLogger().debug("无法访问字段：%s，所属类：%s，文件路径：%s".formatted(name, getClass().getSimpleName(), getFilePath()));
            return null;
        }
    }
    
    default Sender getLogger(){
        return new ConsoleSender(getClass().getSimpleName());
    }

    @Override
    default void setField(String name, Object value) {
        try {
            Field field = this.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(this, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            getLogger().error("设置字段失败：%s".formatted(name));
            throw new RuntimeException(e);
        }
    }

    @Override
    default Map<String, Class<?>> getAllFields() {
        Field[] fields = this.getClass().getDeclaredFields();
        HashMap<String, Class<?>> fieldMap = new LinkedHashMap<>();
        for (Field field : fields) {
            fieldMap.put(field.getName(), field.getType());
        }
        return fieldMap;
    }

    @Override
    @NotNull
    default Class<?> getFieldType(String name) {
        try {
            Field field = this.getClass().getDeclaredField(name);
            return field.getType();
        } catch (NoSuchFieldException e) {
            getLogger().error("无法访问字段：%s，所属类：%s，文件路径：%s".formatted(name, getClass().getSimpleName(), getFilePath()));
            throw new RuntimeException(e);
        }
    }

    @Override
    default boolean save() {
        String path = getFilePath();
        File file = new File(path);
        try {
            // 确保父目录存在
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                // noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }

            Gson gson = LocationAdapter.getGsonBuilder();

            // 写入 JSON
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                gson.toJson(this, writer);
            }
            getLogger().success("成功保存数据 %s".formatted(getFilePath()));
            setModified(false);
        } catch (IOException e) {
            getLogger().error("无法保存数据 %s".formatted(getFilePath()));
            throw new RuntimeException(e);
        }
        return true;
    }

    String getFilePath();
    @Override
    default boolean deleteData() {
        String path = getFilePath();
        Path filePath = Path.of(path);

        try {
            boolean deleted = Files.deleteIfExists(filePath);

            if (deleted) {
                getLogger().success("成功删除数据 %s".formatted(path));
            } else {
                getLogger().warn("地图文件不存在 %s".formatted(path));
            }

            return deleted;

        } catch (IOException e) {
            getLogger().error("无法删除数据 %s".formatted(path));
            throw new RuntimeException(e);
        }
    }


    /**
     * 获取数组。i遍历到最多64，获取name${i}字段
     * @param name 数组名
     * @param clazz 类
     * @return  数组, 长度为0, 代表没找到
     * @param <T>   数据类型
     */
    @Override
    default <T> @NotNull List<T> getList(String name, Class<T> clazz){
        ArrayList<T> resultList = new ArrayList<>();
        for (int i = 0; i < 64; i++){
            T value = getField(name + i, clazz);
            if (value == null) break;
            resultList.add(value);
        }
        return resultList;
    }
}
