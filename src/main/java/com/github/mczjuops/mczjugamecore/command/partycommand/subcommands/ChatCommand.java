package com.github.mczjuops.mczjugamecore.command.partycommand.subcommands;

import com.github.mczjuops.mczjugamecore.command.partycommand.PartySubCommands;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.party.Party;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatCommand extends PartySubCommands {

    @Override
    public String getName() {
        return "chat";
    }

    @Override
    public String getUsage() {
        return "/party chat <消息>";
    }

    @Override
    public String getDescription() {
        return "发送队内消息";
    }

    @Override
    public void register(LiteralArgumentBuilder<CommandSourceStack> parent) {
        parent.then(
                Commands.literal(getName())
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>请输入消息"));
                            return 0;
                        })
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(this::executeChat)
                        )
        );
    }

    private int executeChat(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行"));
            return 0;
        }

        PlayerExt player = new PlayerExt(p);

        Party party = player.getParty();
        if (party == null) {
            player.sender().warn("你不在队伍中");
            return 0;
        }

        String message = StringArgumentType.getString(ctx, "message");
        Component prefix = TextParser.parse("<blue><b>队伍 ></b> %s<gray>: <reset>".formatted(player.getDisplayName()));
        Component text = prefix.append(Component.text(message).color(NamedTextColor.WHITE));
        party.getAllPlayer().forEach(playerExt -> playerExt.player().sendMessage(text));

        return Command.SINGLE_SUCCESS;
    }
}
