package com.github.mczjuops.mczjugamecore.command.partycommand.subcommands;

import com.github.mczjuops.mczjugamecore.command.partycommand.PartySubCommands;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class InviteAllCommand extends PartySubCommands {
    @Override public String getName() { return "inviteall"; }
    @Override public String getUsage() { return "/party inviteall"; }
    @Override public String getDescription() { return "邀请服务器内尚未加入队伍的玩家"; }

    @Override
    public void register(LiteralArgumentBuilder<CommandSourceStack> parent) {
        parent.then(Commands.literal(getName()).executes(ctx -> execute(ctx.getSource().getSender())));
    }

    private int execute(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextParser.parse("<red>该命令只能由玩家执行"));
            return 0;
        }
        PlayerExt inviter = new PlayerExt(player);
        InviteCommand inviteCommand = new InviteCommand();
        for (Player online : Bukkit.getOnlinePlayers()) {
            inviteCommand.invite(inviter, new PlayerExt(online));
        }
        return Command.SINGLE_SUCCESS;
    }
}
