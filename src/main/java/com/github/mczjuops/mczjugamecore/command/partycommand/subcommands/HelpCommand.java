package com.github.mczjuops.mczjugamecore.command.partycommand.subcommands;

import com.github.mczjuops.mczjugamecore.command.partycommand.PartyCommand;
import com.github.mczjuops.mczjugamecore.command.partycommand.PartySubCommands;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

public class HelpCommand extends PartySubCommands {

    private final PartyCommand partyCommand;

    public HelpCommand(PartyCommand partyCommand) {
        this.partyCommand = partyCommand;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getUsage() {
        return "/party help";
    }

    @Override
    public String getDescription() {
        return "查看命令帮助";
    }

    @Override
    public void register(LiteralArgumentBuilder<CommandSourceStack> parent) {
        parent.then(
                Commands.literal(getName())
                        .executes(partyCommand::showHelp)
        );
    }
}
