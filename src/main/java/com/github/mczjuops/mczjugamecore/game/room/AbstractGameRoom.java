package com.github.mczjuops.mczjugamecore.game.room;

import com.github.mczjuops.mczjugamecore.serialize.MGCSerializable;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;

/**
 * 游戏房间，仅用于存放一些坐标数据之类的。
 * 一个游戏可以有一个或多个游戏房间。这个不在代码中写，而是在mc中，用菜单创建。
 * 这个要做一个对应的设置坐标等数据的箱子菜单。
 * 每个游戏需要有一个配置类，继承JsonGameRoom，注册GameRoom类到GameManager中
 * 没测过反射能不能获取到private字段，gpt说在9+不一定。可以先把所有字段设置为public
 */
public abstract class AbstractGameRoom implements MGCSerializable {


    private String gameId; // 由Loader注入
    private String roomName;    // 由Loader注入

    private GameRoomState state = GameRoomState.READY;

    protected final ConsoleSender logger = new ConsoleSender("MGC: %s".formatted(getClass().getSimpleName()));

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
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

    public abstract boolean deleteRoom();

    private boolean modified = false;
    @Override
    public boolean isModified() {
        return modified;
    }

    @Override
    public void setModified(boolean modified) {
        this.modified = modified;
    }

}
