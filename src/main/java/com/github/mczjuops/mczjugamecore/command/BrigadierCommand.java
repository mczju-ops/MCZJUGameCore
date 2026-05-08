package com.github.mczjuops.mczjugamecore.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.List;

public interface BrigadierCommand {
    String getName();
    String getDescription();
    List<String> getAliases();
    LiteralCommandNode<CommandSourceStack> getNode();

    // 下面这堆东西，只在需要支持 /pl -> /party list 这样的缩写时有用

    /** 子命令路径的顶层缩写 */
    default List<RedirectAlias> getRedirectAliases() {
        return List.of();
    }

    /**
     * @param name 新顶层命令名，如 "pl"
     * @param description Bukkit help 描述，可为 null
     * @param path 相对于 getNode() 的子路径
     */
    record RedirectAlias(String name, String description, List<String> path) {
        public RedirectAlias(String name, String description, String... path) {
            this(name, description, List.of(path));
        }
    }
}