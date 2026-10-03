package com.github.mczjuops.mczjugamecore.command.partycommand.subcommands;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.command.partycommand.PartySubCommands;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.party.Party;
import com.github.mczjuops.mczjugamecore.utils.VoiceGroupUtil;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** 任意队员可通过 /party voice，将当前队伍中已连接语音的玩家加入同一个新群组。 */
public class VoiceCommand extends PartySubCommands {

    /** @return 子命令名称 */
    @Override
    public String getName() {
        return "voice";
    }

    /** @return 帮助中的调用方式 */
    @Override
    public String getUsage() {
        return "/party voice";
    }

    /** @return 帮助中的功能说明 */
    @Override
    public String getDescription() {
        return "让当前队伍成员加入同一个语音群组";
    }

    /** 注册无参数的 voice 子命令，沿用 party 根命令的权限。 */
    @Override
    public void register(LiteralArgumentBuilder<CommandSourceStack> parent) {
        parent.then(Commands.literal(getName()).executes(this::executeVoice));
    }

    private int executeVoice(CommandContext<CommandSourceStack> ctx) {
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
        if (!VoiceGroupUtil.isAvailable()) {
            player.sender().warn("语音服务不可用，请确认服务器已安装并启动 Simple Voice Chat。");
            return 0;
        }

        try {
            var groupId = VoiceGroupUtil.createGroup("Party-" + party.getLeader().getName(), party.getAllPlayer());
            if (groupId.isEmpty()) {
                player.sender().warn("队伍中没有已连接语音的在线玩家，请先连接 Simple Voice Chat。");
                return 0;
            }
            party.sender().success("已将在线且已连接语音的队伍成员加入队伍语音群组。未连接语音的成员请连接后再次执行 /party voice。");
            return Command.SINGLE_SUCCESS;
        } catch (RuntimeException e) {
            player.sender().error("创建队伍语音群组失败，请稍后重试。");
            MCZJUGameCore.getInstance().getLogger().warning("队伍 " + party.getId() + " 创建语音群组失败：" + e);
            return 0;
        }
    }
}
