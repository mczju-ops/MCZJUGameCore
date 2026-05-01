package com.github.mczjuops.mczjugamecore.utils.sender.impl;

import com.github.mczjuops.mczjugamecore.utils.sender.Sender;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
/*
玩家私信玩家
 */
public class Player2PlayerSender implements Sender {
    private final Player from;
    private final Player to;

    public Player2PlayerSender(Player from, Player to) {
        this.from = from;
        this.to = to;
    }

    @Override public void info(String msg) { primary(msg); }
    @Override public void warn(String msg) { primary(msg); }
    @Override public void error(String msg) { primary(msg); }
    @Override public void success(String msg) { primary(msg); }


    @Override
    public void primary(String msg) {
        // TODO 替换过时的ChatColor
        to.sendMessage(ChatColor.RED + "[ " + ChatColor.WHITE + from + " " + ChatColor.RED + "-> " +
                ChatColor.WHITE + to.getName() + " " + ChatColor.RED + "] " + ChatColor.WHITE + msg);
    }
}