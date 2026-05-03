package com.github.mczjuops.mczjugamecore.player;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.manager.DefaultGameManager;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class DefaultPlayerManager implements AbstractPlayerManager {

    private final Map<PlayerExt, AbstractGame> playerGameMap = new HashMap<>();

    public DefaultPlayerManager(){}
    @Override
    public List<PlayerExt> getPlayers(AbstractGame game) {
        LinkedList<PlayerExt> playerInGame = new LinkedList<>();
        playerGameMap.forEach((playerExt, game1) -> {
            if (game1 == game) playerInGame.add(playerExt);
        });
        return playerInGame;
    }

    /**
     * 暂时没用，考虑后面删掉
     * @param game  游戏实例
     * @return 是否允许加入游戏
     */
    @Override
    public boolean addPlayer(AbstractGame game) {
        return false;
    }

    @Override
    public void joinGame(PlayerExt player, AbstractGame game) {
        if (playerGameMap.containsKey(player)) {
            leaveGame(player);
        }
        playerGameMap.put(player, game);

    }

    @Override
    public void leaveGame(PlayerExt player) {
        if (playerGameMap.containsKey(player)){
            // 如果原本在游戏中，则调用game中的退出游戏
            // TODO
        }
        playerGameMap.remove(player);
    }

    @Override
    public @Nullable AbstractGame getPlayerGame(PlayerExt player) {
        return playerGameMap.get(player);
    }
}
