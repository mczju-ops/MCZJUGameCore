package com.github.mczjuops.mczjugamecore.command.partycommand.subcommands;

import com.github.mczjuops.mczjugamecore.command.partycommand.PartySubCommands;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.party.Party;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.stream.Collectors;

public class ListCommand extends PartySubCommands {

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public String getUsage() {
        return "list";
    }

    @Override
    public String getDescription() {
        return "查看当前队伍成员信息";
    }

    @Override
    public void register(LiteralArgumentBuilder<CommandSourceStack> parent) {
        parent.then(
                Commands.literal(getName())
                        .executes(this::executeList)
        );
    }

    private int executeList(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行"));
            return 0;
        }

        PlayerExt player = new PlayerExt(p);
        Party party = player.getParty();
        if (party == null) {
            player.sender().warn("未处于队伍中！");
            return 0;
        }

        var members = party.getMembers();
        var leader = party.getLeader();
        String memberList = members.stream()
                .filter(m -> !m.equals(leader))
                .map(PlayerExt::getDisplayName)
                .collect(Collectors.joining(" "));

        player.sender().info("<blue><b>队伍信息</b> （%d）".formatted(members.size()));
        player.sender().info("<blue>队长：%s".formatted(leader.getDisplayName()));
        player.sender().info("<blue>成员：%s".formatted(memberList));

        return Command.SINGLE_SUCCESS;
    }
}
