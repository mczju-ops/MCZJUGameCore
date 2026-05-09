package com.github.mczjuops.mczjugamecore.command;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.room.menu.GameRoomSettingMenu;
import com.github.mczjuops.mczjugamecore.menu.AlertMenu;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.CommandUtils;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;

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
                    ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>用法：/mgcop reload|room"));
                    return 0;
                })
                .then(Commands.literal("reload")
                        .executes(this::executeReload)
                )
                .then(Commands.literal("room")
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>用法：/mgcop room list|create|edit|delete <game> [room]"));
                            return 0;
                        })
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>请指定一个游戏"));
                                    return 0;
                                })
                                .then(Commands.argument("game", StringArgumentType.string())
                                        .suggests((ctx, builder)
                                                -> CommandUtils.suggestMatching(MCZJUGameCore.getGameManager().getRegisteredGameNames(), builder)
                                        )
                                        .executes(this::executeRoomList)
                                )
                        )
                        .then(Commands.literal("create")
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>用法：/mgcop room create <game> <room>"));
                                    return 0;
                                })
                                .then(Commands.argument("game", StringArgumentType.string())
                                        .suggests((ctx, builder)
                                                -> CommandUtils.suggestMatching(MCZJUGameCore.getGameManager().getRegisteredGameNames(), builder)
                                        )
                                        .executes(ctx -> {
                                            ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>请输入房间ID"));
                                            return 0;
                                        })
                                        .then(Commands.argument("room", StringArgumentType.string())
                                                .executes(this::executeRoomCreate)
                                        )
                                )
                        )
                        .then(Commands.literal("edit")
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>用法：/mgcop room edit <game> <room>"));
                                    return 0;
                                })
                                .then(Commands.argument("game", StringArgumentType.string())
                                        .suggests((ctx, builder)
                                                -> CommandUtils.suggestMatching(MCZJUGameCore.getGameManager().getRegisteredGameNames(), builder)
                                        )
                                        .executes(ctx -> {
                                            ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>请指定房间ID"));
                                            return 0;
                                        })
                                        .then(Commands.argument("room", StringArgumentType.string())
                                                .suggests((ctx, builder) -> {
                                                    String gameName = CommandUtils.getToken(builder, 3);
                                                    Set<String> names = MCZJUGameCore.getGameRoomManager().getGameRoomNames(gameName);
                                                    return CommandUtils.suggestMatching(names, builder);
                                                })
                                                .executes(this::executeRoomEdit)
                                        )
                                )
                        )
                        .then(Commands.literal("delete")
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>用法：/mgcop room delete <game> <room>"));
                                    return 0;
                                })
                                .then(Commands.argument("game", StringArgumentType.string())
                                        .suggests((ctx, builder)
                                                -> CommandUtils.suggestMatching(MCZJUGameCore.getGameManager().getRegisteredGameNames(), builder)
                                        )
                                        .executes(ctx -> {
                                            ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>请指定要删除的房间"));
                                            return 0;
                                        })
                                        .then(Commands.argument("room", StringArgumentType.string())
                                                .suggests((ctx, builder) -> {
                                                    String gameName = CommandUtils.getToken(builder, 3);
                                                    Set<String> names = MCZJUGameCore.getGameRoomManager().getGameRoomNames(gameName);
                                                    return CommandUtils.suggestMatching(names, builder);
                                                })
                                                .executes(this::executeRoomDelete)
                                        )
                                )
                        )
                )
                .build();
    }

    private int executeReload(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>正在重新加载插件配置"));
        MCZJUGameCore.getInstance().reloadConfig();
        return Command.SINGLE_SUCCESS;
    }

    private int executeRoomList(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();

        String gameName = StringArgumentType.getString(ctx, "game");
        if (!MCZJUGameCore.getGameManager().getRegisteredGameNames().contains(gameName)) {
            sender.sendMessage(TextParser.parse("<yellow>未注册游戏%s".formatted(gameName)));
            return 0;
        }

        Set<String> roomNames = MCZJUGameCore.getGameRoomManager().getGameRoomNames(gameName);
        String list = String.join(", ", roomNames);
        String count = !roomNames.isEmpty() ? "<gold>共%s个".formatted(roomNames.size()) : "<red>无";
        sender.sendMessage(TextParser.parse("<yellow>游戏%s的房间：%s".formatted(gameName, count)));
        if (!roomNames.isEmpty()) sender.sendMessage(Component.text(list).color(NamedTextColor.YELLOW));

        return Command.SINGLE_SUCCESS;
    }

    private int executeRoomCreate(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();

        String gameName = StringArgumentType.getString(ctx, "game");
        if (!MCZJUGameCore.getGameManager().getRegisteredGameNames().contains(gameName)) {
            sender.sendMessage(TextParser.parse("<yellow>未注册游戏%s".formatted(gameName)));
            return 0;
        }

        String roomName = StringArgumentType.getString(ctx, "room");
        Set<String> roomNames = MCZJUGameCore.getGameRoomManager().getGameRoomNames(gameName);
        if (roomNames.contains(roomName)) {
            sender.sendMessage(TextParser.parse("<red>游戏%s已存在ID为%s的房间".formatted(gameName, roomName)));
            return 0;
        }

        MCZJUGameCore.getGameRoomManager().createGameRoom(gameName, roomName);
        sender.sendMessage(TextParser.parse("<green>成功为游戏%s创建ID为%s的房间".formatted(gameName, roomName)));

        return Command.SINGLE_SUCCESS;
    }

    private int executeRoomEdit(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();

        if (!(sender instanceof Player p)) {
            sender.sendMessage(TextParser.parse("<yellow>该命令只能由玩家执行"));
            return 0;
        }

        PlayerExt player = new PlayerExt(p);

        String gameName = StringArgumentType.getString(ctx, "game");
        if (!MCZJUGameCore.getGameManager().getRegisteredGameNames().contains(gameName)) {
            player.sender().warn("<yellow>未注册游戏%s".formatted(gameName));
            return 0;
        }

        String roomName = StringArgumentType.getString(ctx, "room");
        if (!MCZJUGameCore.getGameRoomManager().getGameRoomNames(gameName).contains(roomName)) {
            player.sender().warn("""
                    <yellow>游戏%s不存在ID为%s的房间，请先创建 \
                    <gray>\
                    <hover:show_text:"<yellow>点击发送/mgcop room create %s %s</yellow>">\
                    <click:run_command:mgcop room create %s %s>\
                    [点击创建]\
                    """
                    .formatted(gameName, roomName, gameName, roomName, gameName, roomName)
            );
            return 0;
        }

        var gameRoom = MCZJUGameCore.getGameRoomManager().getGameRoom(gameName, roomName);
        new GameRoomSettingMenu(p, gameRoom).open();

        return Command.SINGLE_SUCCESS;
    }

    private int executeRoomDelete(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();

        if (!(sender instanceof Player p)) {
            sender.sendMessage(TextParser.parse("<yellow>该命令只能由玩家执行"));
            return 0;
        }

        PlayerExt player = new PlayerExt(p);

        String gameName = StringArgumentType.getString(ctx, "game");
        if (!MCZJUGameCore.getGameManager().getRegisteredGameNames().contains(gameName)) {
            player.sender().warn("<yellow>未注册游戏%s".formatted(gameName));
            return 0;
        }

        String roomName = StringArgumentType.getString(ctx, "room");
        if (!MCZJUGameCore.getGameRoomManager().getGameRoomNames(gameName).contains(roomName)) {
            player.sender().warn("<yellow>游戏%s不存在ID为%s的房间".formatted(gameName, roomName));
            return 0;
        }

        new AlertMenu(p, () -> {
            boolean success = MCZJUGameCore.getGameRoomManager().deleteGameRoom(gameName, roomName);
            if (success) player.sender().success("已删除游戏%s的房间%s".formatted(gameName, roomName));
            else player.sender().error("出错了，删除失败");
        }).open();
        return Command.SINGLE_SUCCESS;
    }
}
