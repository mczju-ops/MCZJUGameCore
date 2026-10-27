package com.github.mczjuops.mczjugamecore.game.kit;

/**
 * 对局被中止（玩家全部退出、插件停用、服务器关闭、逻辑线程被中断）。
 *
 * <p>与「玩家取消某次操作」区分：取消只影响当前这一手，中止表示整局都不该继续了。
 * 游戏逻辑线程在最外层捕获它即可正常收场，不需要记录错误日志。
 */
public class GameAbortedException extends RuntimeException {

    public GameAbortedException(String message) {
        super(message);
    }

    public GameAbortedException(String message, Throwable cause) {
        super(message, cause);
    }
}
