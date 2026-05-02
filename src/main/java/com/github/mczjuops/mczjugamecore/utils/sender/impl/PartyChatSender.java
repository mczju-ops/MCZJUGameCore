package com.github.mczjuops.mczjugamecore.utils.sender.impl;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.party.Party;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import com.github.mczjuops.mczjugamecore.utils.sender.Sender;
import net.kyori.adventure.text.Component;

public class PartyChatSender implements Sender {

    private final Party party;

    private final PlayerExt player;

    public PartyChatSender(Party party, PlayerExt player) {
        this.party = party;
        this.player = player;
    }

    @Override
    public void info(String msg) {
        send(msg);
    }

    @Override
    public void warn(String msg) {
        send(msg);
    }

    @Override
    public void error(String msg) {
        send(msg);
    }

    @Override
    public void success(String msg) {
        send(msg);
    }

    @Override
    public void primary(String msg) {
        send(msg);
    }

    /**
     * 队伍发消息不需要error之类的，所以全用一样的格式
     * @param msg 玩家输入的消息
     */
    private void send(String msg){
        Component msgComponent = TextParser.parse(STR."<blue> Party > </white>\{player.player().getName()}</white>] <gray>:</gray> <reset>\{msg}");
        party.getAllPlayer().forEach(it -> it.player().sendMessage(msgComponent));
    }
}
