package com.github.mczjuops.mczjugamecore.player;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.AutoReconnect;
import com.github.mczjuops.mczjugamecore.game.GameState;
import com.github.mczjuops.mczjugamecore.game.kit.spectator.SpectatorSession;
import com.github.mczjuops.mczjugamecore.game.kit.team.TeamSession;
import com.github.mczjuops.mczjugamecore.player.strategy.PlayerQuitReason;
import com.github.mczjuops.mczjugamecore.player.party.Party;
import com.github.mczjuops.mczjugamecore.utils.PlayerModeSwitch;
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
     * 注册成员、切换档案并应用核心默认 Tab 名称，页脚沿用游戏介绍；成功入局后发送可关闭的 Wiki 提醒。
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
        Component joinFooter = footer;
        // 页脚等确认仍在本局后再发，避免被拒绝的预加入也显示“正在游玩”
        org.bukkit.Bukkit.getScheduler().runTask(MCZJUGameCore.getInstance(), () -> {
            // 加入策略可能在当前 tick 拒绝入局（例如超出人数上限），只有仍属于同一游戏时才提醒
            if (player.player().isOnline() && playerGameMap.get(player) == game) {
                player.player().sendPlayerListFooter(joinFooter);
                MCZJUGameCore.getGameWikiManager().remindOnJoin(player.player(), game.getId());
            }
        });
    }

    /**
     * 移除成员并清理本局 Tab 修改，随后执行原退出策略；加入失败也清理 Tab。
     * @param player 退出玩家，未入局时不处理
     * @param reason 退出原因，决定队伍退出及档案恢复行为
     */
    @Override
    public void leaveGame(PlayerExt player, PlayerQuitReason reason) {
        // 掉线暂停：原局仍在运行且允许自动重连时只停表、保留旁观登记，重连重新入局后由守卫监听器
        // 用 reapply 恢复淘汰标记、观战锚点与剩余复活倒计时；主动退出、被移除、加入失败回滚
        // 以及无法再重连的原局都要撤销登记，避免旁观模式被带出本局
        boolean keepSpectatorState = reason == PlayerQuitReason.DISCONNECT && canReconnect(player);

        // 离场先作废旧的在途任务，再撤销 kit 登记的旁观/队伍状态，避免把旁观模式或队友关系带出本局；
        // 最后补一次重生包，防止离局时仍停在死亡界面
        PlayerModeSwitch.invalidate(player.player());
        if (!keepSpectatorState) SpectatorSession.releaseFor(player);
        TeamSession.forgetPlayer(player);
        PlayerModeSwitch.forceRespawn(player.player(), null);

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

        // 加入失败只是回滚预加入：不通知等待/退出策略，但档案与 Tab 展示必须恢复，
        // 否则玩家没进游戏，却留着游戏档案和“正在游玩：xxx”的页脚
        if (reason == PlayerQuitReason.JOIN_FAIL) {
            restoreLobbyState(player);
            player.player().playerListName(null);
            return;
        }

        if (game.getState() == GameState.WAITING){
            // 如果是在等待阶段
            game.getGameWaitStrategy().onPlayerLeave(player);
        }else{
            // 不在等待阶段。不做游戏结束阶段的判断，游戏结束调用removeAllPlayer方法
            game.getPlayerQuitStrategy().onPlayerQuit(player, reason);
        }

        restoreLobbyState(player);
        if (reason != PlayerQuitReason.DISCONNECT) {
            player.player().playerListName(null);
        }
    }

    /**
     * 掉线后原局仍在运行、并且游戏实现了 {@link AutoReconnect} 时，旁观登记需要留到重连再用。
     * @param player 掉线玩家
     * @return 该玩家的原有对局仍允许他重新加入时为 true
     */
    private boolean canReconnect(PlayerExt player) {
        AbstractGame game = playerGameMap.get(player);
        return game instanceof AutoReconnect && game.getState() == GameState.RUNNING;
    }

    /**
     * 恢复大厅档案：先作废在途的模式切换（死亡界面中的重生任务除外），再切回大厅档案，
     * 由档案恢复一并带回背包、模式与 Tab 页脚。
     *
     * <p>档案恢复是离场后最终模式的来源，所以不能留给延迟任务去改。
     * @param player 离场或被回滚的玩家
     */
    private void restoreLobbyState(PlayerExt player) {
        if (!PlayerModeSwitch.isOnDeathScreen(player.player())) {
            PlayerModeSwitch.invalidate(player.player());
        }
        player.switchProfile(null);
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
            // 同理：档案恢复决定最终模式，但死亡界面里的重生包仍要发出
            restoreLobbyState(player);
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
