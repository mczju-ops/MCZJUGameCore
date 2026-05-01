package com.github.mczjuops.mczjugamecore.utils.sender.impl;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.utils.sender.Sender;

import java.util.logging.Logger;

/*
向控制台发送消息
TODO 修改消息格式为 游戏名+消息
 */
public class ConsoleSender implements Sender {
    private final String name;
    private ConsoleSender(String name) {
        this.name = name;
    }

    private Logger getLogger() {
        return MCZJUGameCore.getInstance().getLogger();
    }

    @Override public void info(String msg) { getLogger().info(msg); }
    @Override public void warn(String msg) { getLogger().warning(msg); }
    @Override public void error(String msg) { getLogger().severe(msg); }
    @Override public void success(String msg) { getLogger().info(msg); }
    @Override public void primary(String msg) { getLogger().info(msg); }
}
