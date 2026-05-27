package com.github.mczjuops.mczjugamecore.game.strategy.wait;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.party.Party;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.MultiPlayerSender;

import java.util.Collections;
import java.util.List;

/**
 * 默认的最简等待逻辑，到
 */
public class DefaultGameWaitStrategy extends GameWaitStrategy {
    private final int playerLimit;

    private final int minPlayer;
    public DefaultGameWaitStrategy(AbstractGame game, int playerLimit) {
        super(game);
        this.playerLimit = playerLimit;
        this.minPlayer = 1;
    }

    public DefaultGameWaitStrategy(AbstractGame game, int playerLimit, int minPlayer) {
        super(game);
        this.playerLimit = playerLimit;
        this.minPlayer = minPlayer;
    }

    @Override
    public boolean onPlayerJoin(PlayerExt player) {
        return onJoin(Collections.singletonList(player));
    }

    @Override
    public boolean onPartyJoin(Party party) {
        // 这里不发消息，因为即使这个房间无法进人，也可以加其它房间
        return onJoin(party.getAllPlayer());
    }

    private boolean onJoin(List<PlayerExt> newPlayers){
        int size = game.getPlayers().size();
        if (size > playerLimit) return false;

        // 加入成功，先发消息
        MultiPlayerSender sender = new MultiPlayerSender(game.getPlayers());
        int count = size - newPlayers.size();
        for (PlayerExt newPlayer : newPlayers) {
            count += 1;
            sender.info("玩家%s加入了该游戏（%d/%d）".formatted(newPlayer.getDisplayName(), count, playerLimit));
        }
        if (size == playerLimit) {
            // 正好等于最大人数
            startGame();
        }
        return true;
    }

    @Override
    public void onPlayerLeave(PlayerExt player) {
        game.sender().warn("玩家%s退出了该游戏（%d/%d）".formatted(player.getDisplayName(), game.getPlayers().size(), playerLimit));
        if (game.getPlayers().isEmpty()) MCZJUGameCore.getGameManager().cancelGame(game);
    }

    @Override
    public void tryStart() {
        if (game.getPlayers().size() >= minPlayer){
            startGame();
        }
    }
}
