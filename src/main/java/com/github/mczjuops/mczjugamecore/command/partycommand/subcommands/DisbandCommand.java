package com.github.mczjuops.mczjugamecore.command.partycommand.subcommands;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.command.partycommand.PartySubCommands;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.party.DisbandResult;
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

public class DisbandCommand extends PartySubCommands {

    @Override
    public String getName() {
        return "disband";
    }

    @Override
    public String getUsage() {
        return "/party disband";
    }

    @Override
    public String getDescription() {
        return "解散当前队伍";
    }

    @Override
    public void register(LiteralArgumentBuilder<CommandSourceStack> parent) {
        parent.then(
                Commands.literal(getName())
                        .executes(this::executeDisband)
        );
    }

    private int executeDisband(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行"));
            return 0;
        }

        PlayerExt player = new PlayerExt(p);
        Party party = player.getParty();
        List<PlayerExt> members = List.of();
        if (party != null) {
            members = party.getMembers();
        }

        DisbandResult result = MCZJUGameCore.getPartymanager().disband(player);
        return switch (result) {
            case NOT_IN_PARTY -> {
                player.sender().warn("未处于队伍中！");
                yield 0;
            }
            case NOT_LEADER -> {
                player.sender().warn("只有队长才能解散队伍！");
                yield 0;
            }
            case SUCCESS -> {
                player.sender().warn("<blue>成功解散队伍");
                members.forEach(member
                        -> member.sender().info("<blue>%s解散了队伍".formatted(player.getDisplayName())));
                yield Command.SINGLE_SUCCESS;
            }
        };
    }
}
