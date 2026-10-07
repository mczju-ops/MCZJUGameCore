package com.github.mczjuops.mczjugamecore.player.tab;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.Action;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// 仅改 player-info 数据包；不发送实体 spawn，也不修改真实游戏模式或 Paper 的隐藏记录。
final class PacketEventsTabBackend extends PacketListenerAbstract implements TabPacketBackend {
    // 网络线程只读不可变规则；Bukkit 玩家数据全部在 refresh 的主线程中读取。
    private volatile Rules rules = new Rules(Set.of(), Set.of(), Set.of(), true);
    private final Map<UUID, PlayerInfo> originals = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> knownEntries = new ConcurrentHashMap<>();

    PacketEventsTabBackend() {
        super(PacketListenerPriority.HIGHEST);
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.isCancelled()) return;
        UUID viewer = event.getUser().getUUID();
        if (viewer == null) return;
        Rules snapshot = rules;
        Set<UUID> known = knownEntries.computeIfAbsent(viewer, id -> ConcurrentHashMap.newKeySet());
        if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
            var packet = new WrapperPlayServerPlayerInfoUpdate(event);
            EnumSet<Action> actions = packet.getActions();
            List<PlayerInfo> entries = new ArrayList<>();
            boolean changed = false;
            for (PlayerInfo entry : packet.getEntries()) {
                UUID id = entry.getProfileId();
                remember(entry, actions);
                if (actions.contains(Action.ADD_PLAYER)) known.add(id);
                PlayerInfo copy = new PlayerInfo(entry);
                if (snapshot.enabled && snapshot.online.contains(id)
                        && actions.contains(Action.UPDATE_LISTED)) {
                    copy.setListed(!snapshot.hidden.contains(id));
                    changed = true;
                }
                if (snapshot.enabled && snapshot.normal.contains(id) && actions.contains(Action.UPDATE_GAME_MODE)
                        && entry.getGameMode() == GameMode.SPECTATOR) {
                    copy.setGameMode(GameMode.SURVIVAL);
                    changed = true;
                }
                entries.add(copy);
            }
            if (changed) {
                packet.setEntries(entries);
                event.markForReEncode(true);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO_REMOVE) {
            var packet = new WrapperPlayServerPlayerInfoRemove(event);
            List<UUID> remaining = packet.getProfileIds().stream()
                    .filter(id -> !snapshot.enabled || !snapshot.online.contains(id)).toList();
            known.removeAll(remaining);
            if (remaining.size() != packet.getProfileIds().size()) {
                if (remaining.isEmpty()) event.setCancelled(true);
                else {
                    packet.setProfileIds(remaining);
                    event.markForReEncode(true);
                }
            }
        }
    }

    @Override
    public void refresh(List<Player> online, Set<UUID> hidden, Set<UUID> normal) {
        synchronize(online, hidden, normal, false);
    }

    private void synchronize(List<Player> online, Set<UUID> hidden, Set<UUID> normal, boolean restoreNative) {
        Set<UUID> onlineIds = new HashSet<>();
        online.forEach(player -> onlineIds.add(player.getUniqueId()));
        rules = new Rules(Set.copyOf(onlineIds), hidden, normal, !restoreNative);

        for (Player viewer : online) {
            if (PacketEvents.getAPI().getPlayerManager().getUser(viewer) == null) continue;
            Set<UUID> known = knownEntries.computeIfAbsent(viewer.getUniqueId(), id -> ConcurrentHashMap.newKeySet());
            List<UUID> remove = new ArrayList<>();
            for (UUID id : known) {
                if (!onlineIds.contains(id)) remove.add(id);
            }
            for (Player target : online) {
                UUID id = target.getUniqueId();
                boolean listed = restoreNative ? viewer.canSee(target) && viewer.isListed(target) : !hidden.contains(id);
                if (restoreNative && !viewer.canSee(target)) {
                    // 核心关闭时收回补入的条目，恢复服务器原有行为。
                    if (known.contains(id)) remove.add(id);
                    continue;
                }
                if (!known.contains(id) && !listed) continue;
                PlayerInfo info = createInfo(target, listed, normal.contains(id));
                EnumSet<Action> actions = EnumSet.of(Action.UPDATE_LISTED, Action.UPDATE_GAME_MODE,
                        Action.UPDATE_DISPLAY_NAME, Action.UPDATE_LATENCY, Action.UPDATE_LIST_ORDER, Action.UPDATE_HAT);
                if (known.add(id)) {
                    actions.add(Action.ADD_PLAYER);
                    // 保留原始签名聊天会话；不为已有条目重置聊天会话。
                    if (info.getChatSession() != null) actions.add(Action.INITIALIZE_CHAT);
                }
                PacketEvents.getAPI().getPlayerManager().sendPacketSilently(viewer,
                        new WrapperPlayServerPlayerInfoUpdate(actions, info));
            }
            if (!remove.isEmpty()) {
                known.removeAll(remove);
                PacketEvents.getAPI().getPlayerManager().sendPacketSilently(viewer,
                        new WrapperPlayServerPlayerInfoRemove(remove));
            }
        }
    }

    private PlayerInfo createInfo(Player player, boolean listed, boolean normal) {
        PlayerInfo original = originals.get(player.getUniqueId());
        PlayerInfo info;
        if (original != null && original.getGameProfile().getName() != null) {
            info = new PlayerInfo(original);
        } else {
            List<TextureProperty> properties = player.getPlayerProfile().getProperties().stream()
                    .map(property -> new TextureProperty(property.getName(), property.getValue(), property.getSignature()))
                    .toList();
            info = new PlayerInfo(new UserProfile(player.getUniqueId(), player.getName(), properties));
        }
        info.setListed(listed);
        info.setLatency(player.getPing());
        info.setDisplayName(player.playerListName());
        GameMode mode = GameMode.valueOf(player.getGameMode().name());
        info.setGameMode(normal && mode == GameMode.SPECTATOR ? GameMode.SURVIVAL : mode);
        info.setListOrder(player.getPlayerListOrder());
        info.setShowHat(original == null || original.isShowHat());
        return info;
    }

    private void remember(PlayerInfo entry, EnumSet<Action> actions) {
        originals.compute(entry.getProfileId(), (id, old) -> {
            PlayerInfo copy = old == null ? new PlayerInfo(entry) : new PlayerInfo(old);
            if (actions.contains(Action.ADD_PLAYER)) copy.setGameProfile(entry.getGameProfile());
            if (actions.contains(Action.INITIALIZE_CHAT)) copy.setChatSession(entry.getChatSession());
            if (actions.contains(Action.UPDATE_HAT)) copy.setShowHat(entry.isShowHat());
            return copy;
        });
    }

    @Override
    public void forget(UUID player) {
        originals.remove(player);
        knownEntries.remove(player);
        knownEntries.values().forEach(known -> known.remove(player));
    }

    @Override
    public void close() {
        synchronize(new ArrayList<>(Bukkit.getOnlinePlayers()), Set.of(), Set.of(), true);
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        originals.clear();
        knownEntries.clear();
    }

    private record Rules(Set<UUID> online, Set<UUID> hidden, Set<UUID> normal, boolean enabled) {}
}
