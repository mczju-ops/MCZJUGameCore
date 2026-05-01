package com.github.mczjuops.mczjugamecore.player.strategy.impl;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.strategy.AbstractPlayerQuitStrategy;
import com.github.mczjuops.mczjugamecore.player.strategy.PlayerQuitReason;

/**
 * 默认直接结束当前游戏
 */
public class DefaultPlayerQuitStrategy extends AbstractPlayerQuitStrategy {
    public DefaultPlayerQuitStrategy(AbstractGame game) {
        super(game);
    }

    @Override
    public void onPlayerQuit(PlayerExt player, PlayerQuitReason reason) {
        MCZJUGameCore.getGameManager().abortGame(game);
    }
}
