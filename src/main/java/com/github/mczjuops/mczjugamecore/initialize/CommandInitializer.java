package com.github.mczjuops.mczjugamecore.initialize;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.command.MenuCommand;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;

public class CommandInitializer {
    public static void initialize(){
        // registerCommand("mgc", new MGCCommand());
        // registerCommand("party", new PartyCommand());
        registerCommand("menu", new MenuCommand());
        // registerCommand("mgcop", new MGCOPCommand());
    }

    private static void registerCommand(String commandStr, CommandExecutor executor){
        PluginCommand command = MCZJUGameCore.getInstance().getCommand(commandStr);
        if (command == null){
            throw new RuntimeException("无法加载指令：command为空（%s）".formatted(commandStr));
        }else {
            command.setExecutor(executor);
            if (executor instanceof TabCompleter){
                command.setTabCompleter((TabCompleter) executor);
            }
        }
    }
}
