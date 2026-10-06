package com.github.mczjuops.mczjugamecore.command.partycommand.subcommands;

import com.github.mczjuops.mczjugamecore.command.partycommand.PartySubCommands;
import com.github.mczjuops.mczjugamecore.game.GameState;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.party.Party;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * 队长通过 /party warp 将队员传送到自己身边。
 * 全体队伍玩家（包括队长）必须未加入游戏或处于 WAITING；只传送位置，不改变游戏归属。
 */
public class WarpCommand extends PartySubCommands {

    /** @return 子命令名称 */
    @Override
    public String getName() {
        return "warp";
    }

    /** @return 帮助中的调用方式 */
    @Override
    public String getUsage() {
        return "/party warp";
    }

    /** @return 帮助中的功能说明及使用限制 */
    @Override
    public String getDescription() {
        return "将全队传送到队长身边（仅队长，全员未加入游戏或游戏等待中）";
    }

    /**
     * 注册无参数的 warp 子命令，沿用 party 根命令的权限；执行时检查队长身份和全队游戏状态。
     * @param parent 队伍命令的父节点
     */
    @Override
    public void register(LiteralArgumentBuilder<CommandSourceStack> parent) {
        parent.then(Commands.literal(getName()).executes(this::executeWarp));
    }

    private int executeWarp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
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
        if (!party.isLeader(player)) {
            player.sender().warn("只有队长才能传送队伍成员！");
            return 0;
        }

        var players = party.getAllPlayer();
        for (PlayerExt member : players) {
            var game = member.getGame();
            if (game != null && game.getState() != GameState.WAITING) {
                player.sender().warn("队伍成员 %s 所在游戏不处于等待状态，无法传送全队！".formatted(member.getDisplayName()));
                return 0;
            }
        }

        Location destination = p.getLocation();
        boolean allTeleported = true;
        for (PlayerExt member : players) {
            if (member.equals(player)) continue;
            if (!member.player().isOnline() || !member.player().teleport(destination)) {
                player.sender().warn("队伍成员 %s 传送失败，请稍后再试。".formatted(member.getDisplayName()));
                allTeleported = false;
                continue;
            }
            member.sender().success("已传送到队长身边。");
        }
        if (!allTeleported) return 0;

        player.sender().success("已将全队传送到你身边。");
        return Command.SINGLE_SUCCESS;
    }
}
