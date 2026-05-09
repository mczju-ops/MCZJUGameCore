package com.github.mczjuops.mczjugamecore.game.room;

import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * 游戏房间，仅用于存放一些坐标数据之类的。
 * 一个游戏可以有一个或多个游戏房间。这个不在代码中写，而是在mc中，用菜单创建。
 * 这个要做一个对应的设置坐标等数据的箱子菜单。
 * 每个游戏需要有一个配置类，继承JsonGameRoom，注册GameRoom类到GameManager中
 * 没测过反射能不能获取到private字段，gpt说在9+不一定。可以先把所有字段设置为public
 */
public abstract class AbstractGameRoom {


    private String gameName; // 由Loader注入
    private String roomName;    // 由Loader注入

    private GameRoomState state = GameRoomState.READY;

    private boolean modified = false;   // 是否修改过。设置为true，则使用指令，或服务终止时，将自动保存。

    protected final ConsoleSender logger = new ConsoleSender("MGC: %s".formatted(getClass().getSimpleName()));

    /**
     * 获取游戏房间的配置参数
     * 用JsonGameRoom中的默认实现，写插件时别用这个。
     * @param name 字段名
     * @param clazz 字段所属的类型，比如Location, Integer, String等
     * @return  字段的值
     * @param <T>   字段的类型，如Location.class
     */
    public abstract <T> T getField(String name, Class<T> clazz);

    /**
     * 设置字段值。仅在本插件开发中有用，其它插件一般用不到
     * @param name  字段名
     * @param value 值
     */
    public abstract void setField(String name, Object value);

    /**
     * 获取所有的字段
     * @return 字段集合，name -> type
     */
    public abstract Map<String, Class<?>> getAllFields();

    public abstract Class<?> getFieldType(String name);

    abstract boolean save();

    public String getGameName() {
        return gameName;
    }

    public void setGameName(String gameName) {
        this.gameName = gameName;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public GameRoomState getState() {
        return state;
    }

    public void setState(GameRoomState state) {
        this.state = state;
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    public abstract boolean deleteRoom();
}
