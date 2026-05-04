package com.github.mczjuops.mczjugamecore.utils.sender;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;

/*
发信器，任何和消息有关的都可以来实现这个
包括控制台发消息、给玩家发消息、全体广播消息等
TODO action bar sender等更多的sender实现
 */
public interface Sender {
    void info(String msg);
    void warn(String msg);
    void error(String msg);
    void success(String msg);
    void primary(String msg);

    default void debug(String msg) {
        if (MCZJUGameCore.getMGCConfig().getBoolean("debug")){
            info(msg);
        }
    }
}
