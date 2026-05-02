package com.github.mczjuops.mczjugamecore.game.room;

import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;

import java.lang.reflect.Field;

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
            logger.error(e.toString());
            return null;
        }
    }

    @Override
    public <T> void setField(String name, T value, Class<T> clazz) {
        try {
            Field field = this.getClass().getDeclaredField(name);
            field.setAccessible(true);

            // 类型校验（可选）
            if (!field.getType().isAssignableFrom(clazz)) {
                logger.error(STR."类型不匹配: \{name}");
            }

            field.set(this, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.error(STR."设置字段失败: \{name}");
            logger.error(e.toString());
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
