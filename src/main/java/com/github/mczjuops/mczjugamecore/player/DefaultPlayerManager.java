package com.github.mczjuops.mczjugamecore.player;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.GameState;
import com.github.mczjuops.mczjugamecore.player.strategy.PlayerQuitReason;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
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
            leaveGame(player, PlayerQuitReason.COMMAND_QUIT);
        }
        playerGameMap.put(player, game);
        player.switchProfile(game.getId());
        var gameMeta = game.getGameMeta();
        player.player().playerListName(TextParser.parse(
                "<#DEB12D>%s [<reset>%s<reset><#DEB12D>]".formatted(
                        player.getName(),
                        gameMeta.displayName()
                )
        ));

        Component footer = Component.newline().append(TextParser.parse("<#DEB12D>正在游玩：<reset>")).append(TextParser.parse(gameMeta.displayName()));
        for (String line : gameMeta.description()) {
            footer = footer.append(Component.newline()).append(TextParser.parse(line));
        }
        player.player().sendPlayerListFooter(footer);
    }

    @Override
    public void leaveGame(PlayerExt player, PlayerQuitReason reason) {
        if (!playerGameMap.containsKey(player)) return;

        // 如果原本在游戏中，则调用game中的退出游戏
        AbstractGame game = playerGameMap.get(player);
        playerGameMap.remove(player);

        player.switchProfile(null);
        if (reason != PlayerQuitReason.DISCONNECT) {
            player.player().playerListName(null);
        }

        // 如果是加入游戏失败，不做过多处理
        if (reason == PlayerQuitReason.JOIN_FAIL) return;

        if (game.getState() == GameState.WAITING){
            // 如果是在等待阶段
            game.getGameWaitStrategy().onPlayerLeave(player);
        }else{
            // 不在等待阶段。不做游戏结束阶段的判断，游戏结束调用removeAllPlayer方法
            game.getPlayerQuitStrategy().onPlayerQuit(player, reason);
        }
    }

    @Override
    public @Nullable AbstractGame getPlayerGame(PlayerExt player) {
        return playerGameMap.get(player);
    }

    @Override
    public void removeAllPlayer(AbstractGame game) {
        playerGameMap.entrySet().removeIf(entry -> {
            if (entry.getValue() == game) {
                PlayerExt playerExt = entry.getKey();
                playerExt.switchProfile(null);
                playerExt.player().playerListName(null);
                return true;
            }
            return false;
        });
    }

    @Override
    public boolean isPlayerInGame(@NotNull PlayerExt player, @NotNull Class<? extends AbstractGame> gameClass) {
        AbstractGame game = getPlayerGame(player);
        if (game == null)return false;
        return game.getClass() == gameClass;
    }
}
