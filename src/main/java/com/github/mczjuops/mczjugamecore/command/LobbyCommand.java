package com.github.mczjuops.mczjugamecore.command;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.CommandUtils;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class LobbyCommand implements BrigadierCommand {
    @Override public String getName() { return "lobby"; }
    @Override public String getDescription() { return "传送到主大厅或小游戏等待大厅"; }
    @Override public List<String> getAliases() { return List.of("hub"); }

    @Override
    public LiteralCommandNode<CommandSourceStack> getNode() {
        return Commands.literal(getName())
                .requires(source -> source.getSender().hasPermission("mgc.lobby"))
                .executes(ctx -> teleport(ctx, null))
                .then(Commands.argument("game_id", StringArgumentType.word())
                        .suggests((ctx, builder) -> CommandUtils.suggestMatching(
                                MCZJUGameCore.getGameManager().getRegisteredGameIds(), builder))
                        .executes(ctx -> teleport(ctx, StringArgumentType.getString(ctx, "game_id"))))
                .build();
    }

    private int teleport(CommandContext<CommandSourceStack> ctx, String gameId) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextParser.parse("<yellow>该命令只能由玩家执行"));
            return 0;
        }

        PlayerExt playerExt = new PlayerExt(player);
        if (playerExt.isInGame()) {
            playerExt.sender().warn("无法在游戏过程中进行传送");
            return 0;
        }

        if (gameId != null && !MCZJUGameCore.getGameManager().getRegisteredGameIds().contains(gameId)) {
            playerExt.sender().warn("小游戏 <white>%s</white> 不存在".formatted(gameId));
            return 0;
        }

        Location destination = gameId == null
                ? MCZJUGameCore.getLobbyManager().getMainLobby()
                : MCZJUGameCore.getLobbyManager().getGameLobby(gameId);
        if (destination == null || destination.getWorld() == null) {
            playerExt.sender().warn(gameId == null ? "主大厅尚未配置" : "该小游戏没有大厅");
            return 0;
        }

        player.teleport(destination);
        player.playSound(player, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        return Command.SINGLE_SUCCESS;
    }
}
