package com.github.mczjuops.mczjugamecore.utils.sender.impl;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import com.github.mczjuops.mczjugamecore.utils.sender.Sender;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * 插件单独发消息给某个玩家
 * 同时在日志中记录
 */
public class PlayerSender implements Sender {
    private final Player player;
    private final Logger logger = MCZJUGameCore.getInstance().getLogger();

    private boolean shouldActionbar = false;

    public PlayerSender(Player player) {
        this.player = player;
    }

    @Override
    public void info(String msg) {
        if (shouldActionbar) {
            player.sendActionBar(TextParser.parse(msg).colorIfAbsent(NamedTextColor.GRAY));
        } else {
            player.sendMessage(TextParser.parse(msg).colorIfAbsent(NamedTextColor.GRAY));
        }
        logger.info(STR."Player \{player.getName()} received msg: \{msg}");
    }

    @Override
    public void warn(String msg) {
        if (shouldActionbar) {
            player.sendActionBar(TextParser.parse(msg).colorIfAbsent(NamedTextColor.YELLOW));
        } else {
            player.sendMessage(TextParser.parse(msg).colorIfAbsent(NamedTextColor.YELLOW));
        }
        logger.info(STR."Player \{player.getName()} received msg: \{msg}");
    }

    @Override
    public void error(String msg) {
        if (shouldActionbar) {
            player.sendActionBar(TextParser.parse(msg).colorIfAbsent(NamedTextColor.RED));
        } else {
            player.sendMessage(TextParser.parse(msg).colorIfAbsent(NamedTextColor.RED));
        }
        logger.info(STR."Player \{player.getName()} received msg: \{msg}");
    }

    @Override
    public void success(String msg) {
        if (shouldActionbar) {
            player.sendActionBar(TextParser.parse(msg).colorIfAbsent(NamedTextColor.GREEN));
        } else {
            player.sendMessage(TextParser.parse(msg).colorIfAbsent(NamedTextColor.GREEN));
        }
        logger.info(STR."Player \{player.getName()} received msg: \{msg}");
    }

    @Override
    public void primary(String msg) {
        if (shouldActionbar) {
            player.sendActionBar(TextParser.parse(msg).colorIfAbsent(NamedTextColor.BLUE));
        } else {
            player.sendMessage(TextParser.parse(msg).colorIfAbsent(NamedTextColor.BLUE));
        }
        logger.info(STR."Player \{player.getName()} received msg: \{msg}");
    }

    public void setShouldActionbar(boolean shouldActionbar) {
        this.shouldActionbar = shouldActionbar;
    }
}
