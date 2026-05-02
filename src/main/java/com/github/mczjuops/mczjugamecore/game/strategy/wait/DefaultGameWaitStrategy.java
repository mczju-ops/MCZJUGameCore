package com.github.mczjuops.mczjugamecore.game.strategy.wait;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.party.Party;
import com.github.mczjuops.mczjugamecore.utils.sender.Sender;

import java.util.Collection;
import java.util.List;

/**
 * 默认的最简等待逻辑，到
 */
public class DefaultGameWaitStrategy extends GameWaitStrategy {
    private final int playerLimit;
    DefaultGameWaitStrategy(AbstractGame game, int playerLimit) {
        super(game);
        this.playerLimit = playerLimit;
    }

    @Override
    public boolean onPlayerJoin(PlayerExt player) {
        Sender sender = player.sender();
        return onJoin(sender);
    }

    @Override
    public boolean onPartyJoin(Party party) {
        Sender sender = party.sender();
        return onJoin(sender);
    }

    private boolean onJoin(Sender sender){
        int size = MCZJUGameCore.getPlayerManager().getPlayers(game).size();
        if (size < playerLimit) return true;
        else if (size > playerLimit) {
            sender.success(STR."已加入游戏\{game.getName()}");
            return false;
        }else{
            // 正好等于最大人数
            startGame();
            return true;
        }
    }

    @Override
    public void onPlayerLeave() {
        // do nothing
    }
}
