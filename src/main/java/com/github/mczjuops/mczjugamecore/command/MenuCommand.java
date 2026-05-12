package com.github.mczjuops.mczjugamecore.command;

import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import com.github.mczjuops.mczjugamecore.utils.CommandUtils;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class MenuCommand implements BrigadierCommand {

    @Override
    public String getName() {
        return "menu";
    }

    @Override
    public String getDescription() {
        return "为玩家打开部分 MCZJUGameCore 或小游戏插件的菜单";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> getNode() {
        return Commands.literal(getName())
                .requires(src -> src.getSender().hasPermission("menu.op"))
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>用法：/menu <menu> <player>"));
                    return 0;
                })
                .then(Commands.argument("menu", StringArgumentType.string())
                        .suggests((ctx, builder)
                                -> CommandUtils.suggestMatching(MenuFacade.getMenuIds(), builder)
                        )
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(TextParser.parse("<yellow>请指定一个玩家"));
                            return 0;
                        })
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(this::executeMenu)
                                .then(Commands.argument("args", StringArgumentType.greedyString())
                                        .executes(this::executeMenu)
                                )
                        )
                )
                .build();
    }

    private int executeMenu(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String menuId = StringArgumentType.getString(ctx, "menu");
        if (!MenuFacade.getMenuIds().contains(menuId)) {
            sender.sendMessage(TextParser.parse("<yellow>未注册ID为%s的菜单".formatted(menuId)));
            return 0;
        }

        Player player;
        PlayerSelectorArgumentResolver targetResolver =
                ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
        try {
            player = targetResolver.resolve(ctx.getSource()).getFirst();
        } catch (CommandSyntaxException ignored) {
            sender.sendMessage(TextParser.parse("<yellow>无效的玩家或选择器"));
            return 0;
        }

        String[] args = new String[0];

        try {
            String raw = StringArgumentType.getString(ctx, "args");
            args = raw.split(" ");
        } catch (IllegalArgumentException ignored) {
        }
        boolean result = MenuFacade.open(menuId, player, (Object[]) args);
        if (result) {
            sender.sendMessage(TextParser.parse("<green>为玩家%s打开菜单%s".formatted(player.getName(), menuId)));
            return Command.SINGLE_SUCCESS;
        } else {
            sender.sendMessage(TextParser.parse("<red>无法为玩家打开菜单%s，请检查是否正确注册".formatted(menuId)));
            return 0;
        }
    }
}
