package com.github.mczjuops.mczjugamecore.event;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import java.util.UUID;

/** 胜利结算的快捷事件；与 GameResultEvent 共用监听器，每次未取消事件计一次胜利。 */
public final class GameWinEvent extends GameResultEvent {
    /** 上报常规游戏实例中一位玩家的胜利。 */
    public GameWinEvent(AbstractGame game, UUID playerId) { super(game, playerId, Outcome.WIN); }
    /** 按游戏 ID 上报一次胜利，由子插件避免重复结算。 */
    public GameWinEvent(String gameId, UUID playerId) { super(gameId, playerId, Outcome.WIN); }
}
