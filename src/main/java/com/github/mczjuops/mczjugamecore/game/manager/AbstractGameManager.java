package com.github.mczjuops.mczjugamecore.game.manager;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.room.AbstractGameRoom;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.jetbrains.annotations.Nullable;

/**
 * 简单窗口模式，凡是设计对游戏的生命周期操作，都调用GameManager，而不是在其它地方自己修改
 */
public interface AbstractGameManager {
    /**
     * 注册游戏
     * @param gameClass 游戏类
     */
    void registerGame(Class<? extends AbstractGame> gameClass, Class<? extends AbstractGameRoom> gameRoomClass);

    /**
     * 创建游戏
     * @param name 游戏名
     * @return null：没这个游戏，或没空闲房间；非空：创建成功
     */
    AbstractGame createGame(String name);

    void startGame(AbstractGame game);

    void cancelGame(AbstractGame game);

    void abortGame(AbstractGame game);

    void endGame(AbstractGame game);

    /**
     * 玩家加入游戏。如果玩家是队伍队长，则自动带全队加入游戏。
     * 如果游戏无空闲房间，或者玩家在组队但不是队长，或者等待房间塞不下队伍成员，或者没这个游戏，则什么都不会发生。
     * 如果有空闲房间，但没等待中的游戏，则直接调用createGame
     * 如果有等待中的游戏，直接加入等待
     * @param player 玩家
     * @param gameName 游戏名
     */
    void joinGame(PlayerExt player, String gameName);

    /**
     * 创建新的游戏房间
     * @param gameName  游戏名
     * @param gameRoomName  游戏房间名
     * @return  游戏房间；如果为空，可能是名字输错了
     */
    AbstractGameRoom createGameRoom(String gameName, String gameRoomName);

}
