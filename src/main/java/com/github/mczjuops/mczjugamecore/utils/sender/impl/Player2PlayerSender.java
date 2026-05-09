package com.github.mczjuops.mczjugamecore.utils.sender.impl;

import com.github.mczjuops.mczjugamecore.utils.TextParser;
import com.github.mczjuops.mczjugamecore.utils.sender.Sender;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * 玩家私信玩家
 * 文本不包含格式信息
 * 玩家对玩家的消息，所以全都用默认格式
 */
public class Player2PlayerSender implements Sender {
    private final Player from;
    private final Player to;

    public Player2PlayerSender(Player from, Player to) {
        this.from = from;
        this.to = to;
    }

    @Override public void info(String msg) { send(msg); }
    @Override public void warn(String msg) { send(msg); }
    @Override public void error(String msg) { send(msg); }
    @Override public void success(String msg) { send(msg); }

    @Override
    public void primary(String msg) {
        send(msg);
    }

    private void send(String msg){
        to.sendMessage(TextParser.parse("<red>[<white>%s</white>] <gray>-></gray> [<white>%s</white>] <reset><white>%s".formatted(from.getName(), to.getName(), msg)));
    }
}