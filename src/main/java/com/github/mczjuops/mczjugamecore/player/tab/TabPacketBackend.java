package com.github.mczjuops.mczjugamecore.player.tab;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.UUID;

// 隔离数据包后端实现和面向子插件的 Tab API。
interface TabPacketBackend {
    void refresh(List<Player> online, Set<UUID> hidden, Set<UUID> normal);
    void forget(UUID player);
    void close();
}
