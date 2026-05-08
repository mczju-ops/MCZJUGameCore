package com.github.mczjuops.mczjugamecore.command.partycommand.subcommands;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.command.partycommand.PartySubCommands;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.party.AcceptResult;
import com.github.mczjuops.mczjugamecore.player.party.Party;
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
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class AcceptCommand extends PartySubCommands {

    @Override
    public String getName() {
        return "accept";
    }

    @Override
    public String getUsage() {
        return "accept <玩家>";
    }

    @Override
    public String getDescription() {
        return "接受一位玩家的组队邀请或加入对方队伍";
    }

    @Override
    public void register(LiteralArgumentBuilder<CommandSourceStack> parent) {
        parent.then(
                Commands.literal(getName())
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>请指定邀请人"));
                            return 0;
                        })
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    var players = Bukkit.getOnlinePlayers().stream()
                                            .map(Player::getName)
                                            .collect(Collectors.toCollection(ArrayList::new));
                                    if (ctx.getSource().getSender() instanceof Player player) {
                                        players.remove(player.getName());
                                    }
                                    return CommandUtils.suggestMatching(players, builder);
                                })
                                .executes(this::executeAccept)
                        )
        );
    }

    private int executeAccept(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行"));
            return 0;
        }

        PlayerExt invitee = new PlayerExt(player);
        String inviterName = StringArgumentType.getString(ctx, "player");
        Player inviterPlayer = Bukkit.getPlayer(inviterName);
        if (inviterPlayer == null) {
            invitee.sender().warn("未找到该玩家");
            return 0;
        }

        PlayerExt inviter = new PlayerExt(inviterPlayer);
        AcceptResult result = MCZJUGameCore.getPartymanager().accept(invitee, inviterName);

        return switch (result) {
            case NO_VALID_INVITE -> {
                invitee.sender().warn("未找到有效邀请信息");
                yield 0;
            }
            case INVITE_EXPIRED -> {
                invitee.sender().warn("此邀请已过期");
                yield 0;
            }
            case INVITER_OFFLINE -> {
                invitee.sender().warn("对方已离线"); // 实际不会发生
                yield 0;
            }
            case INVITEE_ALREADY_IN_PARTY -> {
                invitee.sender().warn("你已经在队伍中了");
                yield 0;
            }
            case INVITER_PARTY_CHANGED -> {
                invitee.sender().warn("对方已离开原队伍");
                yield 0;
            }
            case CREATED_PARTY -> {
                invitee.sender().info("<blue>成功与%s组队".formatted(inviter.getDisplayName()));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

                inviter.sender().info("<blue>%s接受了组队邀请，成功创建队伍".formatted(invitee.getDisplayName()));
                yield Command.SINGLE_SUCCESS;
            }
            case JOINED_PARTY -> {
                Party party = invitee.getParty();
                if (party != null) {
                    PlayerExt leader = party.getLeader();
                    invitee.sender().info("<blue>成功加入%s的队伍".formatted(leader.getDisplayName()));
                    party.sender().info("<blue>%s加入了队伍".formatted(invitee.getDisplayName()));
                }
                yield Command.SINGLE_SUCCESS;
            }
        };
    }
}
