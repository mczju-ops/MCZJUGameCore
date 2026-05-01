package com.github.mczjuops.mczjugamecore.game.room;

import java.lang.reflect.Field;

public class JsonGameRoom implements AbstractGameRoom {

    @Override
    public <T> T getField(String name, Class<T> clazz) {
        try {
            Field field = this.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(this);
            return clazz.cast(value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("获取字段失败: " + name, e);
        }
    }

    @Override
    public <T> void setField(String name, T value, Class<T> clazz) {
        try {
            Field field = this.getClass().getDeclaredField(name);
            field.setAccessible(true);

            // 类型校验（可选）
            if (!field.getType().isAssignableFrom(clazz)) {
                throw new IllegalArgumentException("类型不匹配: " + name);
            }

            field.set(this, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("设置字段失败: " + name, e);
        }
    }

    @Override
    public boolean save(String path) {
        // TODO 还没实现保存到json文件
        return false;
    }

    @Override
    public boolean load(String path) {
        // TODO
        return false;
    }
}
