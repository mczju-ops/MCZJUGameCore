package com.github.mczjuops.mczjugamecore.command.partycommand;

import com.github.mczjuops.mczjugamecore.command.BrigadierCommand;
import com.github.mczjuops.mczjugamecore.command.partycommand.subcommands.AcceptCommand;
import com.github.mczjuops.mczjugamecore.command.partycommand.subcommands.InviteCommand;
import com.github.mczjuops.mczjugamecore.command.partycommand.subcommands.ListCommand;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class PartyCommand implements BrigadierCommand {

    private final List<PartySubCommands> subCommands = new ArrayList<>();

    public PartyCommand() {
        registerSubCommands();
    }

    @Override
    public String getName() {
        return "party";
    }

    @Override
    public String getDescription() {
        return "队伍功能命令";
    }

    @Override
    public List<String> getAliases() {
        return List.of("p");
    }

    @Override
    public List<RedirectAlias> getRedirectAliases() {
        return List.of(
                new RedirectAlias("pl", "/party list 的简写", "list")
        );
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> getNode() {
        LiteralArgumentBuilder<CommandSourceStack> root =
                Commands.literal(getName())
                        .requires(src -> src.getSender().hasPermission("mgc.party"));

        root.executes(this::showHelp);

        for (PartySubCommands sub : subCommands) sub.register(root);

        return root.build();
    }

    private void registerSubCommands() {
        subCommands.add(new ListCommand());
        subCommands.add(new InviteCommand());
        subCommands.add(new AcceptCommand());
    }

    private int showHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();

        sender.sendMessage(TextParser.parse("<blue>======== 队伍功能命令帮助 ========"));
        for (PartySubCommands sub : subCommands) {
            sender.sendMessage(TextParser.parse(
                    "<blue>/party %s <gray>-</gray> <aqua>%s".formatted(sub.getUsage(), sub.getDescription())
            ));
        }

        return Command.SINGLE_SUCCESS;
    }
}
