package com.github.mczjuops.mczjugamecore.player.strategy;

public enum PlayerQuitReason {
    DISCONNECT,
    COMMAND_QUIT,   // 执行指令退出，或者被队长带出去
    JOIN_FAIL;  // 由于人数超上限等原因无法加入游戏
}
