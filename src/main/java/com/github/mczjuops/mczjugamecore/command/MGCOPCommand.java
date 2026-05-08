package com.github.mczjuops.mczjugamecore.command;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import java.util.List;

public class MGCOPCommand implements BrigadierCommand {

    @Override
    public String getName() {
        return "mgcop";
    }

    @Override
    public String getDescription() {
        return "MCZJUGameCore 插件管理员命令";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> getNode() {
        return Commands.literal(getName())
                .requires(src -> src.getSender().hasPermission("mgc.dev"))
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>用法：/mgcop reload|save"));
                    return 0;
                })
                .then(Commands.literal("reload")
                        .executes(this::executeReload)
                )
                .then(Commands.literal("save") // 保存地图。不提供重新加载地图的功能，因为这个功能操作有点危险
                        .executes(this::executeSave)
                )
                .build();
    }

    private int executeReload(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>正在重新加载插件配置"));
        MCZJUGameCore.getInstance().reloadConfig();
        return Command.SINGLE_SUCCESS;
    }

    private int executeSave(CommandContext<CommandSourceStack> ctx) {
        MCZJUGameCore.getGameRoomManager().saveAllGameRoom();
        ctx.getSource().getSender().sendMessage(TextParser.parse("<green>成功保存所有地图"));
        return Command.SINGLE_SUCCESS;
    }
}
