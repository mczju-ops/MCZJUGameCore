package com.github.mczjuops.mczjugamecore.utils.sender.impl;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import com.github.mczjuops.mczjugamecore.utils.sender.Sender;

public class GameSender implements Sender {

    private final AbstractGame game;

    public GameSender(AbstractGame game) {
        this.game = game;
    }

    @Override
    public void info(String msg) {
        send(msg, "reset");
    }

    @Override
    public void warn(String msg) {
        send(msg, "yellow");
    }

    @Override
    public void error(String msg) {
        send(msg, "red");
    }

    @Override
    public void success(String msg) {
        send(msg, "green");
    }

    @Override
    public void primary(String msg) {
        send(msg, "blue");
    }

    private void send(String msg, String color){
        for (PlayerExt player : game.getPlayers()) {
            player.player().sendMessage(TextParser.parse("<gold>[</gold>%s<reset><gold>] <%s>%s".formatted(game.getGameMeta().displayName(), color, msg)));
        }
    }
}
