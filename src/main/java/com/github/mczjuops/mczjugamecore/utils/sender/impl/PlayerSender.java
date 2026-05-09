package com.github.mczjuops.mczjugamecore.utils.sender.impl;

import com.github.mczjuops.mczjugamecore.utils.TextParser;
import com.github.mczjuops.mczjugamecore.utils.sender.Sender;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * 插件单独发消息给某个玩家
 * 同时在日志中记录
 */
public class PlayerSender implements Sender {
    private final Player player;
    private final ConsoleSender logger = new ConsoleSender("MGC: %s".formatted(getClass().getSimpleName()));
    private boolean shouldActionbar = false;

    public PlayerSender(Player player) {
        this.player = player;
    }

    private void send(String msg, NamedTextColor color) {
        var component = TextParser.parse(msg).colorIfAbsent(color);

        if (shouldActionbar) {
            player.sendActionBar(component);
        } else {
            player.sendMessage(component);
        }

        String logMsg = "Player %s received msg: %s".formatted(player.getName(), msg);
        if (color.equals(NamedTextColor.RED)){
            logger.error(logMsg);
        }else{
            logger.debug(logMsg);
        }
    }

    @Override public void info(String msg)    { send(msg, NamedTextColor.GRAY); }
    @Override public void warn(String msg)    { send(msg, NamedTextColor.YELLOW); }
    @Override public void error(String msg)   { send(msg, NamedTextColor.RED); }
    @Override public void success(String msg) { send(msg, NamedTextColor.GREEN); }
    @Override public void primary(String msg) { send(msg, NamedTextColor.BLUE); }

    public void setShouldActionbar(boolean shouldActionbar) {
        this.shouldActionbar = shouldActionbar;
    }
}
