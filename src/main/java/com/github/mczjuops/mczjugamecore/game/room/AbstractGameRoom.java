package com.github.mczjuops.mczjugamecore.game.room;

/**
 * 游戏房间，仅用于存放一些坐标数据之类的。
 * 一个游戏可以有一个或多个游戏房间。这个不在代码中写，而是在mc中，用菜单创建。
 * 这个要做一个对应的设置坐标等数据的箱子菜单。
 * 每个游戏需要有一个配置类，继承JsonGameRoom，注册GameRoom类到GameManager中
 */
public interface AbstractGameRoom {
    /**
     * 获取游戏房间的配置参数
     * 用JsonGameRoom中的默认实现，写插件时别用这个。
     * @param name 字段名
     * @param clazz 字段所属的类型，比如Location, Integer, String等
     * @return  字段的值
     * @param <T>   字段的类型，如Location.class
     */
    <T> T getField(String name, Class<T> clazz);
    <T> void setField(String name, T value, Class<T> clazz);

    boolean save(String path);

    boolean load(String path);
}
