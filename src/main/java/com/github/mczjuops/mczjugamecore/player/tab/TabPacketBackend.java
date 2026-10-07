package com.github.mczjuops.mczjugamecore.player.tab;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 隔离数据包后端实现和面向子插件的 Tab API。
interface TabPacketBackend {
    /**
     * 在主线程发布规则并同步所有观察者的 Tab 条目。
     * @param online 本次同步的在线玩家
     * @param hidden 全服隐藏名单
     * @param normal 使用普通玩家样式的名单
     * @param displayNames 核心托管的显示名称；缺少的玩家沿用 Paper 名称
     */
    void refresh(List<Player> online, Set<UUID> hidden, Set<UUID> normal, Map<UUID, Component> displayNames);
    /** 立即撤销离线玩家规则；其他观察者的旧条目留到删除包或下一次合批刷新时清理。 */
    void forget(UUID player);
    void close();
}
