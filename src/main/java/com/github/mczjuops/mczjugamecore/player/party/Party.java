package com.github.mczjuops.mczjugamecore.player.party;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.sender.Sender;

import java.util.LinkedList;
import java.util.List;

public class Party {
    private PlayerExt leader;

    private final List<PlayerExt> members = new LinkedList<>();


    public PlayerExt getLeader() {
        return leader;
    }

    public void setLeader(PlayerExt leader) {
        this.leader = leader;
    }

    public List<PlayerExt> getMembers() {
        return members;
    }

    public List<PlayerExt> getAllPlayer(){
        LinkedList<PlayerExt> players = new LinkedList<>(members);
        players.add(leader);
        return players;
    }

    public void addMember(PlayerExt player){
        if (members.contains(player) || leader == player){
            player.sender().warn("您已在这个队伍中！");
            return;
        }
        if (player.isInParty()){
            player.sender().warn("请退出当前组队！");
            return;
        }
        members.add(player);
    }

    public Sender sender(){
        // TODO 队伍广播，通知全队加入游戏等
        return null;
    }
}
