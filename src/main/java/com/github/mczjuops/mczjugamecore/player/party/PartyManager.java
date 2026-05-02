package com.github.mczjuops.mczjugamecore.player.party;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;

import java.util.LinkedList;
import java.util.List;

// TODO
public class PartyManager {

    private final List<Party> partyList = new LinkedList<>();

    public PartyManager() {}


    public void joinParty(PlayerExt player, Party party){
        party.addMember(player);
    }

    public void createParty(PlayerExt player){
        if (player.isInParty()){
            player.sender().warn("请退出当前组队，再创建队伍！");
            return;
        }
        Party party = new Party();
        party.setLeader(player);
        partyList.add(party);
    }
}
