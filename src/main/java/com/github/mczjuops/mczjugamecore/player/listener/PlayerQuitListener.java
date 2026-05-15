package com.github.mczjuops.mczjugamecore.player.listener;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.party.LeaveResult;
import com.github.mczjuops.mczjugamecore.player.party.Party;
import com.github.mczjuops.mczjugamecore.player.strategy.PlayerQuitReason;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

public class PlayerQuitListener implements Listener {

    @EventHandler
    public void onPlayerInGameQuit(PlayerQuitEvent event){
        // 判断在player manager中进行，不在本处进行
        PlayerExt player = new PlayerExt(event.getPlayer());
        MCZJUGameCore.getPlayerManager().leaveGame(player, PlayerQuitReason.DISCONNECT);
    }

    @EventHandler
    public void onPlayerInPartyQuit(PlayerQuitEvent event) {
        PlayerExt player = new PlayerExt(event.getPlayer());
        Party party = player.getParty();
        if (party == null) return;

        LeaveResult result = MCZJUGameCore.getPartymanager().handlePlayerQuit(player);
        List<PlayerExt> members = party.getMembers();
        PlayerExt leader = party.getLeader();

        // 略微延迟后通知队员
        Bukkit.getScheduler().runTaskLater(MCZJUGameCore.getInstance(), () -> {
            switch (result) {
                case NOT_IN_PARTY -> {}
                case SUCCESS_PARTY_DISBANDED_NO_LEADER -> members.forEach(member
                        -> member.sender().info("<blue>队长%s退出了游戏，被移出队伍。由于只剩一人，队伍已自动解散".formatted(player.getDisplayName())));
                case SUCCESS_PARTY_DISBANDED_NO_MEMBERS -> leader.sender().info("<blue>%s退出了游戏，被移出队伍。由于只剩一人，队伍已自动解散".formatted(player.getDisplayName()));
                case SUCCESS -> party.sender().info("<blue>%s退出了游戏，被移出队伍".formatted(player.getDisplayName()));
                case SUCCESS_PROMOTED -> party.sender().info("<blue>队长%s退出了游戏，被移出队伍。%s成为了新队长".formatted(player.getDisplayName(), party.getLeader().getDisplayName()));
            }
        }, 5L);
    }

    @EventHandler
    public void savePlayerDataOnQuit(PlayerQuitEvent event){
        MCZJUGameCore.getPlayerDataManager().savePlayerDataAsync(event.getPlayer().getUniqueId().toString());
    }
}
