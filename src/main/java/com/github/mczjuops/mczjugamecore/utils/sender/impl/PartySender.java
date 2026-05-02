package com.github.mczjuops.mczjugamecore.utils.sender.impl;

import com.github.mczjuops.mczjugamecore.player.party.Party;
import com.github.mczjuops.mczjugamecore.utils.sender.Sender;

public class PartySender implements Sender {

    private final Party party;

    public PartySender(Party party) {
        this.party = party;
    }

    @Override
    public void info(String msg) {
        party.getMembers().forEach(member -> member.sender().info(msg));
    }

    @Override
    public void warn(String msg) {
        party.getMembers().forEach(member -> member.sender().warn(msg));
    }

    @Override
    public void error(String msg) {
        party.getMembers().forEach(member -> member.sender().error(msg));
    }

    @Override
    public void success(String msg) {
        party.getMembers().forEach(member -> member.sender().success(msg));
    }

    @Override
    public void primary(String msg) {
        party.getMembers().forEach(member -> member.sender().primary(msg));
    }
}
