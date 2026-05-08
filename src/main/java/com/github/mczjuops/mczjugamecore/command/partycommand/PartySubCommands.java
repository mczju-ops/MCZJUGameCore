package com.github.mczjuops.mczjugamecore.command.partycommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;

public abstract class PartySubCommands {

    /** 子命令名 */
    public abstract String getName();

    /** 使用格式（如 "invite <玩家>"，用于帮助输出） */
    public abstract String getUsage();

    /** 命令说明（用于帮助输出） */
    public abstract String getDescription();

    /** 注册到父命令的 LiteralArgumentBuilder 上。 */
    public abstract void register(LiteralArgumentBuilder<CommandSourceStack> parent);
}
