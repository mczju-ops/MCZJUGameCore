package com.github.mczjuops.mczjugamecore.game.room;

import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
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

            Class<?> fieldType = field.getType();

            if (!fieldType.isInstance(value)) {
                throw new IllegalArgumentException(
                        STR."类型不匹配: \{name}, 期望: \{fieldType}, 实际: \{value.getClass()}"
                );
            }

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
        // TODO 还没实现保存到json文件
        return false;
    }

    @Override
    public boolean load() {
        // TODO
        return false;
    }
}
