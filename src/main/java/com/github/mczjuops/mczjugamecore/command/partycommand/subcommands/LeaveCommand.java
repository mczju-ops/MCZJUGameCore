package com.github.mczjuops.mczjugamecore.command.partycommand.subcommands;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.command.partycommand.PartySubCommands;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.party.LeaveResult;
import com.github.mczjuops.mczjugamecore.player.party.Party;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class LeaveCommand extends PartySubCommands {

    @Override
    public String getName() {
        return "leave";
    }

    @Override
    public String getUsage() {
        return "/party leave";
    }

    @Override
    public String getDescription() {
        return "离开当前队伍";
    }

    @Override
    public void register(LiteralArgumentBuilder<CommandSourceStack> parent) {
        parent.then(
                Commands.literal(getName())
                        .executes(this::executeLeave)
        );
    }

    private int executeLeave(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行"));
            return 0;
        }

        PlayerExt player = new PlayerExt(p);
        List<PlayerExt> members = List.of();
        Party party = player.getParty();
        PlayerExt leader = null;
        if (party != null) {
            members = party.getMembers();
            leader = party.getLeader();
        }

        LeaveResult result = MCZJUGameCore.getPartymanager().leave(player);
        return switch (result) {
            case NOT_IN_PARTY -> {
                player.sender().warn("未处于队伍中！");
                yield 0;
            }
            case SUCCESS_PARTY_DISBANDED_NO_LEADER -> {
                player.sender().info("<blue>已离开队伍");
                members.forEach(member
                        -> member.sender().info("<blue>队长%s离开了队伍。由于只剩一人，队伍已自动解散".formatted(player.getDisplayName())));
                yield Command.SINGLE_SUCCESS;
            }
            case SUCCESS_PARTY_DISBANDED_NO_MEMBERS -> {
                assert leader != null;
                player.sender().info("<blue>已离开%s的队伍".formatted(leader.getDisplayName()));
                leader.sender().info("<blue>%s离开了队伍。由于只剩一人，队伍已自动解散".formatted(player.getDisplayName()));
                yield Command.SINGLE_SUCCESS;
            }
            case SUCCESS -> {
                assert party != null;
                player.sender().info("<blue>已离开%s的队伍".formatted(party.getLeader().getDisplayName()));
                party.sender().info("<blue>%s已离开队伍".formatted(player.getDisplayName()));
                yield Command.SINGLE_SUCCESS;
            }
            case SUCCESS_PROMOTED -> {
                player.sender().info("<blue>已离开队伍");
                assert party != null;
                party.sender().info("<blue>队长%s已离开队伍，%s成为了新队长".formatted(player.getDisplayName(), party.getLeader().getDisplayName()));
                yield Command.SINGLE_SUCCESS;
            }
        };
    }
}
