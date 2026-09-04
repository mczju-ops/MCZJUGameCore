package com.github.mczjuops.mczjugamecore.command;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.GameState;
import com.github.mczjuops.mczjugamecore.menu.MainMenu;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.strategy.PlayerQuitReason;
import com.github.mczjuops.mczjugamecore.utils.CommandUtils;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class MGCCommand implements BrigadierCommand {

    @Override
    public String getName() {
        return "mgc";
    }

    @Override
    public String getDescription() {
        return "小游戏基础功能命令";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> getNode() {
        return Commands.literal(getName())
                .requires(src -> src.getSender().hasPermission("mgc.mgc"))
                .executes(this::executeMGC)
                .then(Commands.literal("help")
                        .executes(this::showHelp))
                .then(Commands.literal("join")
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>请指定要加入的游戏"));
                            return 0;
                        })
                        .then(Commands.argument("game", StringArgumentType.word())
                                .suggests((ctx, builder) ->
                                        CommandUtils.suggestMatching(MCZJUGameCore.getGameManager().getRegisteredGameIds(), builder)
                                )
                                .executes(this::executeJoin)
                        )
                )
                .then(Commands.literal("joinroom")
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>请指定要加入的游戏"));
                            return 0;
                        })
                        .then(Commands.argument("game", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    List<String> selectable = new ArrayList<>();
                                    var gameManager = MCZJUGameCore.getGameManager();
                                    var games = gameManager.getRegisteredGameIds();
                                    games.forEach(id -> {
                                        if (gameManager.playerSelectable(id)) selectable.add(id);
                                    });
                                    return CommandUtils.suggestMatching(selectable, builder);
                                })
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>请指定游戏房间"));
                                    return 0;
                                })
                                .then(Commands.argument("room", StringArgumentType.string())
                                        .suggests((ctx, builder) -> {
                                            String gameId = CommandUtils.getToken(builder, 2);
                                            var roomNames = MCZJUGameCore.getGameRoomManager().getGameRoomNames(gameId);
                                            return CommandUtils.suggestMatching(roomNames, builder);
                                        })
                                        .executes(this::executeJoinRoom)
                                )
                        )
                )
                .then(Commands.literal("leave")
                        .executes(this::executeLeave))
                .then(Commands.literal("spectator")
                        .executes(this::executeSpectator))
                .then(Commands.literal("start") // 尝试开始当前玩家所在的游戏
                        .executes(this::executeStart))
                .build();
    }

    private int showHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();

        sender.sendMessage(TextParser.parse("<#DEB12D><b>============= 小游戏功能命令帮助 ============="));
        sender.sendMessage(TextParser.parse("<#DEB12D><b>命令列表："));
        sender.sendMessage(TextParser.parse("<yellow>/mgc <gray>-</gray> <aqua>打开小游戏菜单"));
        sender.sendMessage(TextParser.parse("<yellow>/mgc help <gray>-</gray> <aqua>查看命令帮助"));
        sender.sendMessage(TextParser.parse("<yellow>/mgc join <游戏> <gray>-</gray> <aqua>加入指定游戏"));
        sender.sendMessage(TextParser.parse("<yellow>/mgc joinroom <游戏> <房间> <gray>-</gray> <aqua>加入指定游戏房间（部分游戏可用）"));
        sender.sendMessage(TextParser.parse("<yellow>/mgc leave <gray>-</gray> <aqua>退出当前游戏"));
        sender.sendMessage(TextParser.parse("<yellow>/mgc spectator <gray>-</gray> <aqua>进入或退出旁观模式"));
        sender.sendMessage(TextParser.parse("<yellow>/mgc start <gray>-</gray> <aqua>尝试开始当前等待中的游戏"));
        sender.sendMessage(TextParser.parse("<#DEB12D><b>======================================="));

        return Command.SINGLE_SUCCESS;
    }

    private int executeMGC(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextParser.parse("<yellow>该命令只能由玩家执行"));
            return 0;
        }
        new MainMenu(player).open();
        return Command.SINGLE_SUCCESS;
    }

    private int executeJoin(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行"));
            return 0;
        }

        PlayerExt player = new PlayerExt(p);

        var gameManager = MCZJUGameCore.getGameManager();
        String gameId = StringArgumentType.getString(ctx, "game");
        if (!gameManager.getRegisteredGameIds().contains(gameId)) {
            player.sender().warn("不存在该游戏");
            return 0;
        }

        MCZJUGameCore.getGameManager().joinGame(player, gameId);
        return Command.SINGLE_SUCCESS;
    }

    private int executeJoinRoom(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行"));
            return 0;
        }

        PlayerExt player = new PlayerExt(p);

        var gameManager = MCZJUGameCore.getGameManager();
        String gameId = StringArgumentType.getString(ctx, "game");
        if (!gameManager.getRegisteredGameIds().contains(gameId)) {
            player.sender().warn("不存在该游戏");
            return 0;
        }

        if (!gameManager.playerSelectable(gameId)) {
            player.sender().warn("该游戏无法自主选择加入的房间");
            return 0;
        }

        String roomName = StringArgumentType.getString(ctx, "room");
        var room = MCZJUGameCore.getGameRoomManager().getGameRoom(gameId, roomName);
        if (room == null) {
            player.sender().warn("此游戏不存在该游戏房间");
            return 0;
        }

        gameManager.joinGame(player, gameId, roomName);
        return Command.SINGLE_SUCCESS;
    }

    private int executeLeave(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行"));
            return 0;
        }

        PlayerExt player = new PlayerExt(p);
        if (player.getGame() == null) {
            player.sender().warn("你没有在游玩任何游戏，或者该游戏不需要退出");
        }

        MCZJUGameCore.getPlayerManager().leaveGame(player, PlayerQuitReason.COMMAND_QUIT);
        return Command.SINGLE_SUCCESS;
    }

    private int executeSpectator(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行"));
            return 0;
        }

        PlayerExt playerExt = new PlayerExt(player);
        if (playerExt.isInGame()) {
            playerExt.sender().warn("无法在游戏过程中切换旁观模式");
            return 0;
        }

        if (player.getGameMode() != GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SPECTATOR);
            player.showTitle(Title.title(
                    Component.empty(),
                    TextParser.parse("<yellow>再次输入/mgc spectator退出旁观模式")
            ));
            return Command.SINGLE_SUCCESS;
        }

        Location lobby = MCZJUGameCore.getLobbyManager().getMainLobby();
        if (lobby == null || lobby.getWorld() == null) {
            playerExt.sender().warn("主大厅尚未配置，无法退出旁观模式");
            return 0;
        }

        if (!player.teleport(lobby)) {
            playerExt.sender().warn("传送到主大厅失败，无法退出旁观模式");
            return 0;
        }

        player.setGameMode(GameMode.ADVENTURE);
        return Command.SINGLE_SUCCESS;
    }

    private int executeStart(CommandContext<CommandSourceStack> ctx){
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行"));
            return 0;
        }

        PlayerExt player = new PlayerExt(p);
        if (!player.isInGame()){
            player.sender().warn("你没有在等待任何游戏，请先加入游戏再尝试开始游戏");
            return 0;
        }
        AbstractGame game = player.getGame();
        assert game != null;
        if (game.getState() != GameState.WAITING) {
            player.sender().warn("游戏不在等待状态，无法尝试开始游戏");
            return 0;
        }
        game.getGameWaitStrategy().tryStart();
        return Command.SINGLE_SUCCESS;
    }
}
