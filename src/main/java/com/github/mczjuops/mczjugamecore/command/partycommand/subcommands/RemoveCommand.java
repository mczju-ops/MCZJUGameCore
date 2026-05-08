package com.github.mczjuops.mczjugamecore.command.partycommand.subcommands;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.command.partycommand.PartySubCommands;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.party.Party;
import com.github.mczjuops.mczjugamecore.player.party.RemoveResult;
import com.github.mczjuops.mczjugamecore.utils.CommandUtils;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class RemoveCommand extends PartySubCommands {

    @Override
    public String getName() {
        return "remove";
    }

    @Override
    public String getUsage() {
        return "/party remove <玩家>";
    }

    @Override
    public String getDescription() {
        return "将一位玩家移出队伍";
    }

    @Override
    public void register(LiteralArgumentBuilder<CommandSourceStack> parent) {
        parent.then(
                Commands.literal(getName())
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>请指定一个队伍成员"));
                            return 0;
                        })
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    // 只在发送命令的人为队长时，将所有队员名字放入补全列表
                                    var playerNames = new ArrayList<String>();
                                    if (ctx.getSource().getSender() instanceof Player player) {
                                        PlayerExt playerExt = new PlayerExt(player);
                                        Party party = playerExt.getParty();
                                        if (party != null && playerExt.equals(party.getLeader())) {
                                            party.getMembers().forEach(member -> playerNames.add(member.getName()));
                                        }
                                    }
                                    return CommandUtils.suggestMatching(playerNames, builder);
                                })
                                .executes(this::executeRemove)
                        )
        );
    }

    private int executeRemove(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行"));
            return 0;
        }

        PlayerExt player = new PlayerExt(p);
        String targetName = StringArgumentType.getString(ctx, "player");
        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer == null) {
            player.sender().warn("未找到该玩家");
            return 0;
        }
        PlayerExt target = new PlayerExt(targetPlayer);

        RemoveResult result = MCZJUGameCore.getPartymanager().remove(player, target);
        return switch (result) {
            case NOT_IN_PARTY -> {
                player.sender().warn("你不在队伍中");
                yield 0;
            }
            case NOT_LEADER -> {
                player.sender().warn("只有队长才可以将成员移出队伍");
                yield 0;
            }
            case CANNOT_REMOVE_SELF -> {
                player.sender().warn("不能将自己移出队伍！");
                yield 0;
            }
            case TARGET_NOT_IN_PARTY -> {
                player.sender().warn("该玩家不在队伍中！");
                yield 0;
            }
            case SUCCESS_PARTY_DISBANDED -> {
                player.sender().info("<blue>你将%s移出了队伍。由于没有其他成员，队伍已自动解散".formatted(target.getDisplayName()));
                target.sender().info("<blue>%s将你移出了队伍".formatted(player.getDisplayName()));
                yield Command.SINGLE_SUCCESS;
            }
            case SUCCESS -> {
                target.sender().info("<blue>%s将你移出了队伍".formatted(player.getDisplayName()));
                Party party = player.getParty();
                assert party != null;
                party.getMembers().forEach(member -> member.sender()
                        .info("<blue>队长%s将%s移出了队伍".formatted(player.getDisplayName(), target.getDisplayName())));
                yield Command.SINGLE_SUCCESS;
            }
        };
    }
}
