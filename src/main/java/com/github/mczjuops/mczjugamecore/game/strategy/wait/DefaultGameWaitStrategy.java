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
        int size = game.getPlayers().size();
        if (size > playerLimit) {
            party.sender().warn("该游戏人数超出上限（最多%d人），队伍无法加入".formatted(playerLimit));
            return false;
        }
        return onJoin(party.getAllPlayer());
    }

    private boolean onJoin(List<PlayerExt> newPlayers){
        int size = game.getPlayers().size();
        if (size > playerLimit) {
            new MultiPlayerSender(newPlayers).warn("该游戏人数超出上限（最多%d人），无法加入".formatted(playerLimit));
            return false;
        }

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
        int size = game.getPlayers().size();
        if (size > playerLimit) {
            game.sender().warn("当前人数超出上限（最多%d人），无法开始游戏".formatted(playerLimit));
        } else if (size < minPlayer) {
            game.sender().warn("当前人数不足，至少需要%d人才能开始游戏（当前%d人）".formatted(minPlayer, size));
        } else {
            startGame();
        }
    }
}
