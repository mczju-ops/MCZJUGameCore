package com.github.mczjuops.mczjugamecore.player.party;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.sender.Sender;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.PartySender;

import java.util.Collections;
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

    public boolean isLeader(PlayerExt player) {
        return leader.equals(player);
    }

    public void setLeader(PlayerExt leader) {
        this.leader = leader;
    }

    public List<PlayerExt> getMembers() {
        return Collections.unmodifiableList(members);
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

    public int size() {
        return 1 + members.size();
    }

    public void addMemberInternal(PlayerExt player) {
        members.add(player);
    }

    public void removeMemberInternal(PlayerExt player) {
        members.remove(player);
    }

    public Sender sender(){
        return new PartySender(this);
    }

    public void promoteFirstMemberToLeader() {
        if (members.isEmpty()) {
            throw new IllegalStateException("Cannot promote leader: no members left");
        }

        this.leader = members.removeFirst();
    }

    public void transferLeader(PlayerExt newLeader) {
        members.add(leader);
        members.remove(newLeader);
        this.leader = newLeader;
    }
}
