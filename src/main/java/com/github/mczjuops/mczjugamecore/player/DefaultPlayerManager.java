package com.github.mczjuops.mczjugamecore.player;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.GameState;
import com.github.mczjuops.mczjugamecore.player.strategy.PlayerQuitReason;
import com.github.mczjuops.mczjugamecore.player.party.Party;
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

    /**
     * 注册成员、切换档案并应用核心默认 Tab 名称，页脚沿用游戏介绍。
     * @param player 要加入的玩家，已有游戏时先退出原局
     * @param game 新游戏实例；子插件应通过 GameManager 加入游戏
     */
    @Override
    public void joinGame(PlayerExt player, AbstractGame game) {
        if (playerGameMap.containsKey(player)) {
            leaveGame(player, PlayerQuitReason.COMMAND_QUIT);
        }
        playerGameMap.put(player, game);
        player.switchProfile(game.getId());
        var gameMeta = game.getGameMeta();
        MCZJUGameCore.getTabManager().refresh();

        Component footer = Component.newline().append(TextParser.parse("<#DEB12D>正在游玩：<reset>")).append(TextParser.parse(gameMeta.displayName()));
        for (String line : gameMeta.description()) {
            footer = footer.append(Component.newline()).append(TextParser.parse(line));
        }
        player.player().sendPlayerListFooter(footer);
    }

    /**
     * 移除成员并清理本局 Tab 修改，随后执行原退出策略；加入失败也清理 Tab。
     * @param player 退出玩家，未入局时不处理
     * @param reason 退出原因，决定队伍退出及档案恢复行为
     */
    @Override
    public void leaveGame(PlayerExt player, PlayerQuitReason reason) {
        if (!playerGameMap.containsKey(player)) return;

        AbstractGame currentGame = playerGameMap.get(player);
        // 队长在等待阶段主动退出时，整支正在等待的队伍一起退出。
        if (reason == PlayerQuitReason.COMMAND_QUIT && currentGame.getState() == GameState.WAITING
                && player.isPartyLeader()) {
            Party party = player.getParty();
            if (party != null) {
                for (PlayerExt member : party.getAllPlayer()) {
                    if (!member.equals(player) && playerGameMap.get(member) == currentGame) {
                        leaveGame(member, reason);
                    }
                }
            }
        }

        // 如果原本在游戏中，则调用game中的退出游戏
        AbstractGame game = playerGameMap.get(player);
        playerGameMap.remove(player);
        MCZJUGameCore.getTabManager().onPlayerLeave(game, player);

        // 如果是加入游戏失败，不做过多处理
        if (reason == PlayerQuitReason.JOIN_FAIL) return;

        if (game.getState() == GameState.WAITING){
            // 如果是在等待阶段
            game.getGameWaitStrategy().onPlayerLeave(player);
        }else{
            // 不在等待阶段。不做游戏结束阶段的判断，游戏结束调用removeAllPlayer方法
            game.getPlayerQuitStrategy().onPlayerQuit(player, reason);
        }

        player.switchProfile(null);
        if (reason != PlayerQuitReason.DISCONNECT) {
            player.player().playerListName(null);
        }
    }

    @Override
    public @Nullable AbstractGame getPlayerGame(PlayerExt player) {
        return playerGameMap.get(player);
    }

    /**
     * 移除整局成员及 Tab 修改，再恢复大厅档案，仅由核心结束流程调用。
     * @param game 要清理的游戏实例，无成员时仍撤销其隐藏名单
     */
    @Override
    public void removeAllPlayer(AbstractGame game) {
        List<PlayerExt> players = getPlayers(game);
        players.forEach(playerGameMap::remove);
        // 先清理整局展示，再恢复档案，避免档案恢复失败遗留 Tab 修改。
        MCZJUGameCore.getTabManager().onGameEnd(game);
        for (PlayerExt player : players) {
            player.switchProfile(null);
            player.player().playerListName(null);
        }
    }

    @Override
    public boolean isPlayerInGame(@NotNull PlayerExt player, @NotNull Class<? extends AbstractGame> gameClass) {
        AbstractGame game = getPlayerGame(player);
        if (game == null)return false;
        return game.getClass() == gameClass;
    }
}
