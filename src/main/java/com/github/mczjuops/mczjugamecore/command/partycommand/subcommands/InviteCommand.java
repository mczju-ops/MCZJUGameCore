package com.github.mczjuops.mczjugamecore.command.partycommand.subcommands;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.command.partycommand.PartySubCommands;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.party.InviteResult;
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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class InviteCommand extends PartySubCommands {

    @Override
    public String getName() {
        return "invite";
    }

    @Override
    public String getUsage() {
        return "/party invite <玩家>";
    }

    @Override
    public String getDescription() {
        return "邀请玩家组队或邀请玩家加入当前队伍";
    }

    @Override
    public void register(LiteralArgumentBuilder<CommandSourceStack> parent) {
        parent.then(
                Commands.literal(getName())
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>请指定一个玩家"));
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
                                .executes(this::executeInvite)
                        )
        );
    }

    private int executeInvite(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行"));
            return 0;
        }

        PlayerExt inviter = new PlayerExt(player);
        String inviteeName = StringArgumentType.getString(ctx, "player");
        Player inviteePlayer = Bukkit.getPlayer(inviteeName);
        if (inviteePlayer == null) {
            inviter.sender().warn("未找到该玩家");
            return 0;
        }

        PlayerExt invitee = new PlayerExt(inviteePlayer);
        InviteResult result = MCZJUGameCore.getPartymanager().invite(inviter, invitee);

        return switch (result) {
            case SUCCESS_AS_LEADER -> {
                Party party = inviter.getParty();
                assert party != null;
                inviter.sender().info("<blue>已邀请%s加入队伍，对方可以在60秒内接受".formatted(invitee.getDisplayName()));
                party.getMembers().forEach(member
                        -> member.sender().info("<blue>%s已邀请%s加入队伍，对方可以在60秒内接受".formatted(
                                inviter.getDisplayName(), invitee.getDisplayName()
                        )));

                invitee.sender().info("""
                                <blue>%s邀请你加入队伍，\
                                <hover:show_text:"<yellow>点击发送/party accept %s</yellow>">\
                                <click:run_command:party accept %s>\
                                <u>点击接受\
                                """
                                .formatted(inviter.getDisplayName(), inviter.getName(), inviter.getName())
                );

                yield Command.SINGLE_SUCCESS;
            }
            case SUCCESS_AS_MEMBER -> {
                Party party = inviter.getParty();
                assert party != null;
                inviter.sender().info("<blue>已邀请%s加入队伍，对方可以在60秒内接受".formatted(invitee.getDisplayName()));
                party.getAllPlayer().forEach(playerExt -> {
                    if (!playerExt.equals(inviter)) {
                        playerExt.sender().info("<blue>%s已邀请%s加入队伍，对方可以在60秒内接受".formatted(
                                inviter.getDisplayName(), invitee.getDisplayName()
                        ));
                    }
                });

                PlayerExt leader = party.getLeader();
                invitee.sender().info("""
                            <blue>%s邀请你加入%s的队伍，\
                            <hover:show_text:"<yellow>点击发送/party accept %s</yellow>">\
                            <click:run_command:party accept %s>\
                            <u>点击接受\
                            """
                        .formatted(inviter.getDisplayName(), leader.getDisplayName(), inviter.getName(), inviter.getName())
                );

                yield Command.SINGLE_SUCCESS;
            }
            case SUCCESS_AS_SOLO -> {
                inviter.sender().info("<blue>已向%s发送组队邀请，对方可以在60秒内接受".formatted(invitee.getDisplayName()));
                invitee.sender().info("""
                        <blue>%s邀请你组队，\
                        <hover:show_text:"<yellow>点击发送/party accept %s</yellow>">\
                        <click:run_command:party accept %s>\
                        <u>点击接受\
                        """
                        .formatted(inviter.getDisplayName(), inviter.getName(), inviter.getName())
                );
                yield Command.SINGLE_SUCCESS;
            }
            case CANNOT_INVITE_SELF -> {
                inviter.sender().info("<yellow>不能邀请自己！");
                yield Command.SINGLE_SUCCESS;
            }
            case INVITEE_ALREADY_IN_PARTY -> {
                inviter.sender().info("<yellow>对方已经在当前队伍中了！");
                yield Command.SINGLE_SUCCESS;
            }
        };
    }
}
