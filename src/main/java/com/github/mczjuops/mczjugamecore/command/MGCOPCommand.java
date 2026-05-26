package com.github.mczjuops.mczjugamecore.command;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.room.menu.GameRoomSettingMenu;
import com.github.mczjuops.mczjugamecore.menu.AlertMenu;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.score.leaderboard.textdisplay.JsonTextDisplayRecord;
import com.github.mczjuops.mczjugamecore.score.leaderboard.textdisplay.TextDisplayEditMenu;
import com.github.mczjuops.mczjugamecore.utils.CommandUtils;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
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
                                                -> CommandUtils.suggestMatching(MCZJUGameCore.getGameManager().getRegisteredGameIds(), builder)
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
                                                -> CommandUtils.suggestMatching(MCZJUGameCore.getGameManager().getRegisteredGameIds(), builder)
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
                                                -> CommandUtils.suggestMatching(MCZJUGameCore.getGameManager().getRegisteredGameIds(), builder)
                                        )
                                        .executes(ctx -> {
                                            ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>请指定房间ID"));
                                            return 0;
                                        })
                                        .then(Commands.argument("room", StringArgumentType.string())
                                                .suggests((ctx, builder) -> {
                                                    String gameId = CommandUtils.getToken(builder, 3);
                                                    Set<String> names = MCZJUGameCore.getGameRoomManager().getGameRoomNames(gameId);
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
                                                -> CommandUtils.suggestMatching(MCZJUGameCore.getGameManager().getRegisteredGameIds(), builder)
                                        )
                                        .executes(ctx -> {
                                            ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>请指定要删除的房间"));
                                            return 0;
                                        })
                                        .then(Commands.argument("room", StringArgumentType.string())
                                                .suggests((ctx, builder) -> {
                                                    String gameId = CommandUtils.getToken(builder, 3);
                                                    Set<String> names = MCZJUGameCore.getGameRoomManager().getGameRoomNames(gameId);
                                                    return CommandUtils.suggestMatching(names, builder);
                                                })
                                                .executes(this::executeRoomDelete)
                                        )
                                )
                        )
                )
                .then(Commands.literal("leaderboard")
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>用法：/mgcop leaderboard list|create|edit|delete <leaderboardId> [entityId]"));
                            return 0;
                        })
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>请指定一个排行榜的ID"));
                                    return 0;
                                })
                                .then(Commands.argument("leaderboardId", StringArgumentType.string())
                                        .suggests((ctx, builder)
                                                -> CommandUtils.suggestMatching(MCZJUGameCore.getLeaderboardManager().getAllLeaderboardIds(), builder)
                                        )
                                        .executes(this::executeLeaderboardList)
                                )
                        )
                        .then(Commands.literal("create")
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>用法：/mgcop leaderboard create <leaderboardId> <displayId>"));
                                    return 0;
                                })
                                .then(Commands.argument("leaderboardId", StringArgumentType.string())
                                        .suggests((ctx, builder)
                                                -> CommandUtils.suggestMatching(MCZJUGameCore.getLeaderboardManager().getAllLeaderboardIds(), builder)
                                        )
                                        .executes(ctx -> {
                                            ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>请输入展示实体ID"));
                                            return 0;
                                        })
                                        .then(Commands.argument("displayId", StringArgumentType.string())
                                                .executes(this::executeLeaderboardCreate)
                                        )
                                )
                        )
                        .then(Commands.literal("edit")
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>用法：/mgcop leaderboard edit <leaderboardId> <displayId>"));
                                    return 0;
                                })
                                .then(Commands.argument("leaderboardId", StringArgumentType.string())
                                        .suggests((ctx, builder)
                                                -> CommandUtils.suggestMatching(MCZJUGameCore.getLeaderboardManager().getAllLeaderboardIds(), builder)
                                        )
                                        .executes(ctx -> {
                                            ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>请指定展示实体ID"));
                                            return 0;
                                        })
                                        .then(Commands.argument("displayId", StringArgumentType.string())
                                                .suggests((ctx, builder) -> {
                                                    String leaderboardId = CommandUtils.getToken(builder, 3);
                                                    var entityIds = MCZJUGameCore.getLeaderboardManager().getAllDisplayIds(leaderboardId);
                                                    return CommandUtils.suggestMatching(entityIds, builder);
                                                })
                                                .executes(this::executeLeaderboardEdit)
                                        )
                                )
                        )
                        .then(Commands.literal("delete")
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>用法：/mgcop leaderboard delete <leaderboardId> <displayId>"));
                                    return 0;
                                })
                                .then(Commands.argument("leaderboardId", StringArgumentType.string())
                                        .suggests((ctx, builder)
                                                -> CommandUtils.suggestMatching(MCZJUGameCore.getLeaderboardManager().getAllLeaderboardIds(), builder)
                                        )
                                        .executes(ctx -> {
                                            ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>请指定要删除的实体ID"));
                                            return 0;
                                        })
                                        .then(Commands.argument("displayId", StringArgumentType.string())
                                                .suggests((ctx, builder) -> {
                                                    String leaderboardId = CommandUtils.getToken(builder, 3);
                                                    var entityIds = MCZJUGameCore.getLeaderboardManager().getAllDisplayIds(leaderboardId);
                                                    return CommandUtils.suggestMatching(entityIds, builder);
                                                })
                                                .executes(this::executeLeaderboardDelete)
                                        )
                                )
                        )
                )
                .then(Commands.literal("item")  // 给玩家注册的物品
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>用法：/mgcop item <player> <itemId> [amount]"));
                            return 0;
                        })
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(Commands.argument("itemId", StringArgumentType.string())
                                        .suggests((ctx, builder)
                                                -> CommandUtils.suggestMatching(MCZJUGameCore.getItemManager().getAllRegisteredItemNames(), builder)
                                        )
                                        .executes(ctx -> executeGiveItem(ctx, 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1)).
                                                executes(ctx ->{
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    return executeGiveItem(ctx, amount);
                                                })
                                        )
                                )
                        )
                )

                .build();
    }

    private int executeGiveItem(CommandContext<CommandSourceStack> ctx, int amount){
        CommandSender sender = ctx.getSource().getSender();
        String itemId = StringArgumentType.getString(ctx, "itemId");
        Player player;
        PlayerSelectorArgumentResolver targetResolver =
                ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
        try {
            player = targetResolver.resolve(ctx.getSource()).getFirst();
        } catch (CommandSyntaxException ignored) {
            sender.sendMessage(TextParser.parse("<yellow>无效的玩家或选择器"));
            return 0;
        }
        new PlayerExt(player).giveItem(itemId, amount);
        return Command.SINGLE_SUCCESS;
    }

    private int executeReload(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>正在重新加载插件配置"));
        MCZJUGameCore.getConfigManager().reload();
        return Command.SINGLE_SUCCESS;
    }

    private int executeRoomList(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();

        String gameId = StringArgumentType.getString(ctx, "game");
        if (!MCZJUGameCore.getGameManager().getRegisteredGameIds().contains(gameId)) {
            sender.sendMessage(TextParser.parse("<yellow>未注册游戏%s".formatted(gameId)));
            return 0;
        }

        Set<String> roomNames = MCZJUGameCore.getGameRoomManager().getGameRoomNames(gameId);
        String list = String.join(", ", roomNames);
        String count = !roomNames.isEmpty() ? "<gold>共%s个".formatted(roomNames.size()) : "<red>无";
        sender.sendMessage(TextParser.parse("<yellow>游戏%s的房间：%s".formatted(gameId, count)));
        if (!roomNames.isEmpty()) sender.sendMessage(Component.text(list).color(NamedTextColor.YELLOW));

        return Command.SINGLE_SUCCESS;
    }

    private int executeRoomCreate(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();

        String gameId = StringArgumentType.getString(ctx, "game");
        if (!MCZJUGameCore.getGameManager().getRegisteredGameIds().contains(gameId)) {
            sender.sendMessage(TextParser.parse("<yellow>未注册游戏%s".formatted(gameId)));
            return 0;
        }

        String roomName = StringArgumentType.getString(ctx, "room");
        Set<String> roomNames = MCZJUGameCore.getGameRoomManager().getGameRoomNames(gameId);
        if (roomNames.contains(roomName)) {
            sender.sendMessage(TextParser.parse("<red>游戏%s已存在ID为%s的房间".formatted(gameId, roomName)));
            return 0;
        }

        MCZJUGameCore.getGameRoomManager().createGameRoom(gameId, roomName);
        sender.sendMessage(TextParser.parse("<green>成功为游戏%s创建ID为%s的房间".formatted(gameId, roomName)));

        return Command.SINGLE_SUCCESS;
    }

    private int executeRoomEdit(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();

        if (!(sender instanceof Player p)) {
            sender.sendMessage(TextParser.parse("<yellow>该命令只能由玩家执行"));
            return 0;
        }

        PlayerExt player = new PlayerExt(p);

        String gameId = StringArgumentType.getString(ctx, "game");
        if (!MCZJUGameCore.getGameManager().getRegisteredGameIds().contains(gameId)) {
            player.sender().warn("<yellow>未注册游戏%s".formatted(gameId));
            return 0;
        }

        String roomName = StringArgumentType.getString(ctx, "room");
        if (!MCZJUGameCore.getGameRoomManager().getGameRoomNames(gameId).contains(roomName)) {
            player.sender().warn("""
                    <yellow>游戏%s不存在ID为%s的房间，请先创建 \
                    <gray>\
                    <hover:show_text:"<yellow>点击发送/mgcop room create %s %s</yellow>">\
                    <click:run_command:mgcop room create %s %s>\
                    [点击创建]\
                    """
                    .formatted(gameId, roomName, gameId, roomName, gameId, roomName)
            );
            return 0;
        }

        var gameRoom = MCZJUGameCore.getGameRoomManager().getGameRoom(gameId, roomName);
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

        String gameId = StringArgumentType.getString(ctx, "game");
        if (!MCZJUGameCore.getGameManager().getRegisteredGameIds().contains(gameId)) {
            player.sender().warn("<yellow>未注册游戏%s".formatted(gameId));
            return 0;
        }

        String roomName = StringArgumentType.getString(ctx, "room");
        if (!MCZJUGameCore.getGameRoomManager().getGameRoomNames(gameId).contains(roomName)) {
            player.sender().warn("<yellow>游戏%s不存在ID为%s的房间".formatted(gameId, roomName));
            return 0;
        }

        new AlertMenu(p, () -> {
            boolean success = MCZJUGameCore.getGameRoomManager().deleteGameRoom(gameId, roomName);
            if (success) player.sender().success("已删除游戏%s的房间%s".formatted(gameId, roomName));
            else player.sender().error("出错了，删除失败");
        }).open();
        return Command.SINGLE_SUCCESS;
    }

    private int executeLeaderboardList(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();

        String leaderboardId = StringArgumentType.getString(ctx, "leaderboardId");
        if (!MCZJUGameCore.getLeaderboardManager().getAllLeaderboardIds().contains(leaderboardId)) {
            sender.sendMessage(TextParser.parse("<yellow>未注册排行榜%s".formatted(leaderboardId)));
            return 0;
        }

        var displayIds = MCZJUGameCore.getLeaderboardManager().getAllDisplayIds(leaderboardId);
        String list = String.join(", ", displayIds);
        String count = !displayIds.isEmpty() ? "<gold>共%s个".formatted(displayIds.size()) : "<red>无";
        sender.sendMessage(TextParser.parse("<yellow>排行榜%s的展示实体：%s".formatted(leaderboardId, count)));
        if (!displayIds.isEmpty()) sender.sendMessage(Component.text(list).color(NamedTextColor.YELLOW));

        return Command.SINGLE_SUCCESS;
    }

    private int executeLeaderboardCreate(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();

        String leaderboardId = StringArgumentType.getString(ctx, "leaderboardId");
        if (!MCZJUGameCore.getLeaderboardManager().getAllLeaderboardIds().contains(leaderboardId)) {
            sender.sendMessage(TextParser.parse("<yellow>未注册排行榜%s".formatted(leaderboardId)));
            return 0;
        }

        String displayId = StringArgumentType.getString(ctx, "displayId");
        var displayIds = MCZJUGameCore.getLeaderboardManager().getAllDisplayIds(leaderboardId);
        if (displayIds.contains(displayId)) {
            sender.sendMessage(TextParser.parse("<red>排行榜%s已存在ID为%s的展示实体".formatted(leaderboardId, displayId)));
            return 0;
        }

        var record = new JsonTextDisplayRecord(leaderboardId, displayId);
        MCZJUGameCore.getLeaderboardManager().createTextDisplay(leaderboardId, record);
        sender.sendMessage(TextParser.parse("<green>成功为排行榜%s创建ID为%s的展示实体（请在编辑界面生成实体）".formatted(leaderboardId, displayId)));

        return Command.SINGLE_SUCCESS;
    }

    private int executeLeaderboardEdit(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();

        if (!(sender instanceof Player p)) {
            sender.sendMessage(TextParser.parse("<yellow>该命令只能由玩家执行"));
            return 0;
        }

        PlayerExt player = new PlayerExt(p);

        String leaderboardId = StringArgumentType.getString(ctx, "leaderboardId");
        if (!MCZJUGameCore.getLeaderboardManager().getAllLeaderboardIds().contains(leaderboardId)) {
            sender.sendMessage(TextParser.parse("<yellow>未注册排行榜%s".formatted(leaderboardId)));
            return 0;
        }

        String displayId = StringArgumentType.getString(ctx, "displayId");
        if (!MCZJUGameCore.getLeaderboardManager().getAllDisplayIds(leaderboardId).contains(displayId)) {
            player.sender().warn("""
                    <yellow>排行榜%s不存在ID为%s的房间，请先创建 \
                    <gray>\
                    <hover:show_text:"<yellow>点击发送/mgcop leaderboard create %s %s</yellow>">\
                    <click:run_command:mgcop leaderboard create %s %s>\
                    [点击创建]\
                    """
                    .formatted(leaderboardId, displayId, leaderboardId, displayId, leaderboardId, displayId)
            );
            return 0;
        }

        var record = MCZJUGameCore.getLeaderboardManager().getDisplayRecord(leaderboardId, displayId);
        new TextDisplayEditMenu(p, record).open();

        return Command.SINGLE_SUCCESS;
    }

    private int executeLeaderboardDelete(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();

        if (!(sender instanceof Player p)) {
            sender.sendMessage(TextParser.parse("<yellow>该命令只能由玩家执行"));
            return 0;
        }

        PlayerExt player = new PlayerExt(p);

        String leaderboardId = StringArgumentType.getString(ctx, "leaderboardId");
        if (!MCZJUGameCore.getLeaderboardManager().getAllLeaderboardIds().contains(leaderboardId)) {
            sender.sendMessage(TextParser.parse("<yellow>未注册排行榜%s".formatted(leaderboardId)));
            return 0;
        }

        String displayId = StringArgumentType.getString(ctx, "displayId");
        if (!MCZJUGameCore.getLeaderboardManager().getAllDisplayIds(leaderboardId).contains(displayId)) {
            player.sender().warn("<yellow>排行榜%s不存在ID为%s的展示实体".formatted(leaderboardId, displayId));
            return 0;
        }

        new AlertMenu(p, () -> {
            boolean success = MCZJUGameCore.getLeaderboardManager().removeTextDisplay(leaderboardId, displayId);
            if (success) player.sender().success("已删除排行榜%s的展示实体%s".formatted(leaderboardId, displayId));
            else player.sender().error("出错了，删除失败");
        }).open();
        return Command.SINGLE_SUCCESS;
    }
}
