package com.github.mczjuops.mczjugamecore.utils.sender.impl;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.utils.sender.Sender;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * 插件单独发消息给某个玩家
 * TODO 更改消息格式，和替换过时的ChatColor
 */
public class PlayerSender implements Sender {
    private final Player player;
    private final Logger log = MCZJUGameCore.getInstance().getLogger();

    public PlayerSender(Player player) {
        this.player = player;
    }

    @Override
    public void info(String msg) {
        player.sendMessage(ChatColor.GRAY + msg);
        log.info("Player " + player.getName() + " received msg: " + msg);
    }

    @Override
    public void warn(String msg) {
        player.sendMessage(ChatColor.YELLOW + msg);
        log.info("Player " + player.getName() + " received msg: " + msg);
    }

    @Override
    public void error(String msg) {
        player.sendMessage(ChatColor.RED + msg);
        log.info("Player " + player.getName() + " received msg: " + msg);
    }

    @Override
    public void success(String msg) {
        player.sendMessage(ChatColor.GREEN + msg);
        log.info("Player " + player.getName() + " received msg: " + msg);
    }

    @Override
    public void primary(String msg) {
        player.sendMessage(ChatColor.BLUE + msg);
        log.info("Player " + player.getName() + " received msg: " + msg);
    }
}
