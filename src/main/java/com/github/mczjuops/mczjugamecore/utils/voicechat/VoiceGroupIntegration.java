package com.github.mczjuops.mczjugamecore.utils.voicechat;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.VoiceGroupUtil;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Simple Voice Chat API 适配器，仅在可选插件存在时加载；不保留玩家对象。 */
public final class VoiceGroupIntegration implements VoicechatPlugin, VoiceGroupUtil.Backend {
    private final JavaPlugin plugin;
    private final Set<UUID> groups = new HashSet<>();
    private volatile VoicechatServerApi api;
    private volatile boolean closed;

    private VoiceGroupIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 注册语音扩展；服务未注册时返回 null，等待服务器启动事件提供 API。 */
    public static @Nullable VoiceGroupIntegration register(JavaPlugin plugin) {
        BukkitVoicechatService service = Bukkit.getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            plugin.getLogger().warning("voicechat 已启用，但 BukkitVoicechatService 不可用");
            return null;
        }
        VoiceGroupIntegration integration = new VoiceGroupIntegration(plugin);
        service.registerPlugin(integration);
        return integration;
    }

    /** @return 语音扩展的唯一标识 */
    @Override
    public String getPluginId() {
        return "mczjugamecore";
    }

    /** 跟随语音服务器生命周期更新 API；停用后的旧回调不能重新激活此实例。 */
    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
            if (!closed) api = event.getVoicechat();
        });
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> api = null);
    }

    /** @return 语音服务器已启动且集成尚未关闭时为 true */
    @Override
    public boolean isAvailable() {
        return !closed && api != null;
    }

    /** 创建群组并将在线、有语音连接的去重玩家加入；没有候选玩家时不创建空组。 */
    @Override
    public Optional<UUID> createGroup(String name, Collection<PlayerExt> players, VoiceGroupUtil.Options options) {
        VoicechatServerApi server = api;
        if (closed || server == null) return Optional.empty();
        List<VoicechatConnection> connections = players.stream()
                .filter(player -> player.player().isOnline())
                .map(PlayerExt::getUniqueId)
                .distinct()
                .map(server::getConnectionOf)
                .filter(Objects::nonNull)
                .filter(VoicechatConnection::isConnected)
                .toList();
        if (connections.isEmpty()) return Optional.empty();

        // 自动删除的临时组不需要继续追踪。
        groups.removeIf(id -> server.getGroup(id) == null);
        Group.Type type = switch (options.type()) {
            case NORMAL -> Group.Type.NORMAL;
            case OPEN -> Group.Type.OPEN;
            case ISOLATED -> Group.Type.ISOLATED;
        };
        Group group = server.groupBuilder()
                .setName(name)
                .setPassword(options.password())
                .setPersistent(options.persistent())
                .setHidden(options.hidden())
                .setType(type)
                .build();
        groups.add(group.getId());
        try {
            connections.forEach(connection -> connection.setGroup(group));
            // setGroup 没有返回值，第三方事件取消也不会抛出异常，必须重新读取成员状态。
            if (server.getGroup(group.getId()) == null || connections.stream().anyMatch(connection -> {
                VoicechatConnection current = server.getConnectionOf(connection.getPlayer().getUuid());
                return current == null || current.getGroup() == null
                        || !current.getGroup().getId().equals(group.getId());
            })) {
                throw new IllegalStateException("语音插件未允许创建群组或部分玩家加入群组");
            }
        } catch (RuntimeException e) {
            // 不留下半创建的群组；已经离开的原群组不会自动恢复。
            try {
                if (!removeGroup(group.getId())) plugin.getLogger().warning("无法清理创建失败的语音群组：" + group.getId());
            } catch (RuntimeException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            throw e;
        }
        return Optional.of(group.getId());
    }

    /** 清理所有当前成员（包含后来手动加入者），仅删除此实例创建的群组。 */
    @Override
    public boolean removeGroup(UUID groupId) {
        VoicechatServerApi server = api;
        if (server == null || !groups.contains(groupId)) return false;
        for (var player : Bukkit.getOnlinePlayers()) {
            VoicechatConnection connection = server.getConnectionOf(player.getUniqueId());
            if (connection == null) continue;
            Group group = connection.getGroup();
            if (group != null && group.getId().equals(groupId)) connection.setGroup(null);
        }
        Group remaining = server.getGroup(groupId);
        boolean removed = remaining == null || (remaining.isPersistent() && server.removeGroup(groupId));
        if (removed) groups.remove(groupId);
        return removed;
    }

    /** 停用时尽力清理全部自建群组；API 无注销扩展接口，因此封闭旧回调并释放引用。 */
    @Override
    public void shutdown() {
        closed = true;
        try {
            for (UUID groupId : Set.copyOf(groups)) {
                try {
                    if (!removeGroup(groupId)) plugin.getLogger().warning("无法清理语音群组：" + groupId);
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("清理语音群组 " + groupId + " 失败：" + e);
                }
            }
        } finally {
            groups.clear();
            api = null;
        }
    }
}
