package com.github.mczjuops.mczjugamecore.utils.sender.impl;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import com.github.mczjuops.mczjugamecore.utils.sender.Sender;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;

public class MultiPlayerSender implements Sender {

    private final List<PlayerExt> players;

    private final ConsoleSender logger = new ConsoleSender(STR."MGC:\{getClass().getName()}");


    private boolean shouldActionbar = false;

    public MultiPlayerSender(List<PlayerExt> players) {
        this.players = players;
    }

    private void send(String msg, NamedTextColor color) {
        var component = TextParser.parse(msg).colorIfAbsent(color);

        for (PlayerExt player : players) {
            if (shouldActionbar) {
                player.player().sendActionBar(component);
            } else {
                player.player().sendMessage(component);
            }

            String logMsg = STR."Player \{player.player().getName()} received msg: \{msg}";
            if (color.equals(NamedTextColor.RED)){
                logger.error(logMsg);
            }else{
                logger.debug(logMsg);
            }
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
