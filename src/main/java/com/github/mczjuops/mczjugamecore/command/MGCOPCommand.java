package com.github.mczjuops.mczjugamecore.command;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MGCOPCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player)) return false;
        PlayerExt player = new PlayerExt((Player) sender);
        if (args.length == 0) return false; // TODO 做一个菜单来实现更方便的操作
        switch (args[0]){
            case "reload" -> {
                player.sender().info("正在重载config");
                MCZJUGameCore.getInstance().reloadConfig();    // 重载config
            }
            case "save" -> {
                // 保存地图。不提供重新加载地图的功能，因为这个功能操作有点危险。
                player.sender().info("正在保存地图");
                MCZJUGameCore.getGameRoomManager().saveAllGameRoom();
                player.sender().success("成功保存所有地图");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            List<String> commands = Arrays.asList("reload", "save");
            return commands.stream()
                    .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
