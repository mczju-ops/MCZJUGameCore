package com.github.mczjuops.mczjugamecore.command;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.GameState;
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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>用法：/mgc join|leave"));
                    return 0;
                })
                .then(Commands.literal("join")
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>请指定要加入的游戏"));
                            return 0;
                        })
                        .then(Commands.argument("game", StringArgumentType.word())
                                .suggests((ctx, builder) ->
                                        CommandUtils.suggestMatching(MCZJUGameCore.getGameManager().getRegisteredGameNames(), builder)
                                )
                                .executes(this::executeJoin)
                        )
                )
                .then(Commands.literal("leave")
                        .executes(this::executeLeave))
                .then(Commands.literal("start") // 尝试开始当前玩家所在的游戏
                        .executes(this::executeStart))
                .build();
    }

    private int executeJoin(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行"));
            return 0;
        }

        PlayerExt player = new PlayerExt(p);

        var gameManager = MCZJUGameCore.getGameManager();
        String gameName = StringArgumentType.getString(ctx, "game");
        if (!gameManager.getRegisteredGameNames().contains(gameName)) {
            player.sender().warn("不存在该游戏");
            return 0;
        }

        MCZJUGameCore.getGameManager().joinGame(player, gameName);
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
