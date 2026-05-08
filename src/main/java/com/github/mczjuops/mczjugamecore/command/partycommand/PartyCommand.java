package com.github.mczjuops.mczjugamecore.command.partycommand;

import com.github.mczjuops.mczjugamecore.command.BrigadierCommand;
import com.github.mczjuops.mczjugamecore.command.partycommand.subcommands.*;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
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
                new RedirectAlias("pc", "/party chat 的简写", "chat"),
                new RedirectAlias("pi", "/party invite 的简写", "invite"),
                new RedirectAlias("pl", "/party list 的简写", "list"),
                new RedirectAlias("pt", "/party transfer 的简写", "transfer")
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
        subCommands.add(new InviteCommand());
        subCommands.add(new AcceptCommand());
        subCommands.add(new ChatCommand());
        subCommands.add(new ListCommand());
        subCommands.add(new HelpCommand(this));
        subCommands.add(new LeaveCommand());
        subCommands.add(new DisbandCommand());
        subCommands.add(new TransferCommand());
        subCommands.add(new RemoveCommand());
    }

    public int showHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();

        sender.sendMessage(TextParser.parse("<blue><b>============= 队伍功能命令帮助 ============="));
        sender.sendMessage(TextParser.parse("<blue><b>命令列表："));
        for (PartySubCommands sub : subCommands) {
            sender.sendMessage(TextParser.parse(
                    "<yellow>%s <gray>-</gray> <aqua>%s".formatted(sub.getUsage(), sub.getDescription())
            ));
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(TextParser.parse("<blue><b>支持的简写："));
        sender.sendMessage(TextParser.parse("<yellow>/party <gray>→</gray> /p"));
        sender.sendMessage(TextParser.parse("<yellow>/party chat <gray>→</gray> /pc"));
        sender.sendMessage(TextParser.parse("<yellow>/party invite <gray>→</gray> /pi"));
        sender.sendMessage(TextParser.parse("<yellow>/party list <gray>→</gray> /pl"));
        sender.sendMessage(TextParser.parse("<yellow>/party transfer <gray>→</gray> /pt"));

        sender.sendMessage(TextParser.parse("<blue><b>======================================"));

        return Command.SINGLE_SUCCESS;
    }
}
