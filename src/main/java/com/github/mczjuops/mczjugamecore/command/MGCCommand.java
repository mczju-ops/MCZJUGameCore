package com.github.mczjuops.mczjugamecore.command;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.strategy.PlayerQuitReason;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * 加入游戏等，用这个指令
 * TODO 加各种提示
 */
public class MGCCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player)) return false;
        PlayerExt player = new PlayerExt((Player) sender);
        if (args.length == 0) return false;
        switch (args[0]) {
            case "join" -> {
                join(player, args);
            }
            case "leave" ->{
                MCZJUGameCore.getPlayerManager().leaveGame(player, PlayerQuitReason.COMMAND_QUIT);
            }
            default -> {
            }
        }
        return true;
    }

    private boolean join(PlayerExt player, @NotNull String []args){
        if (args.length < 1) return false;
        MCZJUGameCore.getGameManager().joinGame(player, args[1]);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        return null;
    }
}
