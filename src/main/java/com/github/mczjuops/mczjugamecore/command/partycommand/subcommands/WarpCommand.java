package com.github.mczjuops.mczjugamecore.command.partycommand.subcommands;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.command.partycommand.PartySubCommands;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.party.Party;
import com.github.mczjuops.mczjugamecore.player.strategy.PlayerQuitReason;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 队长通过 /party warp 将队员传送到自己身边。
 * 不限制队长的游戏状态；在游戏中的队员直接通过玩家管理器退出，
 * 等待 10 tick 后复查队伍、在线状态和队员游戏归属，再传送到队长执行命令时的位置，不自动加入游戏。
 */
public class WarpCommand extends PartySubCommands {

    private final Set<UUID> pendingWarps = new HashSet<>();

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
        return "将队员传送到队长身边（仅队长，队员先退出游戏，10 tick 后传送）";
    }

    /**
     * 注册无参数的 warp 子命令，沿用 party 根命令的权限；执行时检查队长身份并退出队员的游戏。
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
        if (pendingWarps.contains(party.getId())) {
            player.sender().warn("队伍正在退出游戏并等待传送，请稍候！");
            return 0;
        }

        var players = party.getAllPlayer();
        Location destination = p.getLocation();
        if (players.stream().anyMatch(member -> !member.equals(player) && member.getGame() != null)) {
            pendingWarps.add(party.getId());
            try {
                for (PlayerExt member : players) {
                    if (!member.equals(player)) {
                        member.quitGame(PlayerQuitReason.COMMAND_QUIT);
                    }
                }
                Bukkit.getScheduler().runTaskLater(MCZJUGameCore.getInstance(), () -> {
                    try {
                        completeDelayedWarp(player, party, players, destination);
                    } finally {
                        pendingWarps.remove(party.getId());
                    }
                }, 10L);
            } catch (RuntimeException e) {
                pendingWarps.remove(party.getId());
                throw e;
            }
            player.sender().info("已让队员退出游戏，将在 10 tick 后传送全队。");
            return Command.SINGLE_SUCCESS;
        }
        return teleportMembers(player, players, destination);
    }

    private void completeDelayedWarp(PlayerExt player, Party party, List<PlayerExt> players, Location destination) {
        if (!player.player().isOnline()) return;
        var currentPlayers = party.getAllPlayer();
        if (player.getParty() != party || !party.isLeader(player)
                || currentPlayers.size() != players.size() || !currentPlayers.containsAll(players)) {
            player.sender().warn("队伍已发生变化，本次传送已取消，请重新执行 /party warp。");
            return;
        }
        for (PlayerExt member : players) {
            if (member.equals(player)) continue;
            if (!member.player().isOnline() || member.getParty() != party || member.getGame() != null) {
                player.sender().warn("队伍成员 %s 已离线、离队或仍在游戏中，本次传送已取消。".formatted(member.getDisplayName()));
                return;
            }
        }
        teleportMembers(player, players, destination);
    }

    private int teleportMembers(PlayerExt player, List<PlayerExt> players, Location destination) {
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
