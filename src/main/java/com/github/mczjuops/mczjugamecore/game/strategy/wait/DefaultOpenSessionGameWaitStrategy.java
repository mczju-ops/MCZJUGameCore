package com.github.mczjuops.mczjugamecore.game.strategy.wait;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.party.Party;

public class DefaultOpenSessionGameWaitStrategy extends DefaultGameWaitStrategy {

    public DefaultOpenSessionGameWaitStrategy(AbstractGame game) {
        super(game, 67656); // 无人数上限
    }

    @Override
    public boolean onPlayerJoin(PlayerExt playerExt) {
        startGame();
        return true;
    }

    @Override
    public boolean onPartyJoin(Party party) {
        // 不允许队伍加入
        party.getLeader().sender().warn("该游戏只能单人加入");
        return false;
    }

    // 不适用
    @Override
    public final void onPlayerLeave(PlayerExt player) {}

    // 不适用
    @Override
    public final void tryStart() {}
}
