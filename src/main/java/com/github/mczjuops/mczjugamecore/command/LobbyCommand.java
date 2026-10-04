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
import java.util.LinkedHashSet;
import java.util.Set;

/** 传送到主大厅、小游戏大厅或配置中的游戏类别大厅。 */
public class LobbyCommand implements BrigadierCommand {
    @Override public String getName() { return "lobby"; }
    @Override public String getDescription() { return "传送到主大厅、小游戏大厅或游戏类别大厅"; }
    @Override public List<String> getAliases() { return List.of("hub"); }

    @Override
    public LiteralCommandNode<CommandSourceStack> getNode() {
        return Commands.literal(getName())
                .requires(source -> source.getSender().hasPermission("mgc.lobby"))
                .executes(ctx -> teleport(ctx, null))
                .then(Commands.argument("lobby_id", StringArgumentType.word())
                        .suggests((ctx, builder) -> CommandUtils.suggestMatching(
                                lobbySuggestions(), builder))
                        .executes(ctx -> teleport(ctx, StringArgumentType.getString(ctx, "lobby_id"))))
                .build();
    }

    private Set<String> lobbySuggestions() {
        Set<String> ids = new LinkedHashSet<>(MCZJUGameCore.getGameManager().getRegisteredGameIds());
        var categories = MCZJUGameCore.getConfigManager().getGameCategories().keySet();
        ids.addAll(categories);
        categories.forEach(id -> ids.add("category:" + id));
        return ids;
    }

    private int teleport(CommandContext<CommandSourceStack> ctx, String lobbyId) {
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

        boolean categoryTarget = false;
        String categoryId = lobbyId;
        if (lobbyId != null) {
            boolean explicitCategory = lobbyId.startsWith("category:");
            if (explicitCategory) categoryId = lobbyId.substring("category:".length());
            categoryTarget = explicitCategory
                    || !MCZJUGameCore.getGameManager().getRegisteredGameIds().contains(lobbyId);
            if (categoryTarget && !MCZJUGameCore.getConfigManager().getGameCategories().containsKey(categoryId)) {
                playerExt.sender().warn("小游戏或类别 <white>%s</white> 不存在".formatted(lobbyId));
                return 0;
            }
        }

        Location destination = lobbyId == null
                ? MCZJUGameCore.getLobbyManager().getMainLobby()
                : categoryTarget
                        ? MCZJUGameCore.getLobbyManager().getCategoryLobby(categoryId)
                        : MCZJUGameCore.getLobbyManager().getGameLobby(lobbyId);
        if (destination == null || destination.getWorld() == null) {
            playerExt.sender().warn(lobbyId == null ? "主大厅尚未配置"
                    : categoryTarget ? "该游戏类别没有大厅" : "该小游戏没有大厅");
            return 0;
        }

        if (!player.teleport(destination)) {
            playerExt.sender().warn("传送失败，请稍后再试");
            return 0;
        }
        playerExt.sender().success("已传送到大厅");
        player.playSound(player, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        return Command.SINGLE_SUCCESS;
    }
}
