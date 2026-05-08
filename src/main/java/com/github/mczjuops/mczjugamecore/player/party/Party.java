package com.github.mczjuops.mczjugamecore.player.party;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.sender.Sender;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.PartySender;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class Party {

    private final UUID id = UUID.randomUUID();

    private PlayerExt leader;
    private final List<PlayerExt> members = new LinkedList<>(); // 当前模型中，leader 不在 members 中

    public Party(PlayerExt leader) {
        this.leader = leader;
    }

    public UUID getId() {
        return id;
    }

    public PlayerExt getLeader() {
        return leader;
    }

    public void setLeader(PlayerExt leader) {
        this.leader = leader;
    }

    public List<PlayerExt> getMembers() {
        return members;
    }

    public List<PlayerExt> getAllPlayer() {
        LinkedList<PlayerExt> players = new LinkedList<>();
        players.add(leader);
        players.addAll(members);
        return players;
    }

    public boolean hasPlayer(PlayerExt player) {
        return leader.equals(player) || members.contains(player);
    }

    void addMemberInternal(PlayerExt player) {
        members.add(player);
    }

    public Sender sender(){
        return new PartySender(this);
    }
}
