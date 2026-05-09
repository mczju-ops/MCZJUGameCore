package com.github.mczjuops.mczjugamecore.utils.sender.impl;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.utils.sender.Sender;

import java.util.logging.Logger;

/**
 * 向控制台发送消息，消息格式为 [游戏名] 信息
 * 文字不包含任何格式信息
 */
public class ConsoleSender implements Sender {

    private final String name;
    public ConsoleSender(String name) {
        this.name = name;
    }

    private Logger getLogger() {
        return MCZJUGameCore.getInstance().getLogger();
    }

    @Override public void info(String msg) { getLogger().info("[%s] %s".formatted(name, msg)); }
    @Override public void warn(String msg) { getLogger().warning("[%s] %s".formatted(name, msg)); }
    @Override public void error(String msg) { getLogger().severe("[%s] %s".formatted(name, msg)); }

    @Override public void success(String msg) { info(msg); }
    @Override public void primary(String msg) { info(msg); }
}
