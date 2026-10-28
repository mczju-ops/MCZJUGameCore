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
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// 仅改 player-info 数据包；不发送实体 spawn，也不修改真实游戏模式或 Paper 的隐藏记录。
final class PacketEventsTabBackend extends PacketListenerAbstract implements TabPacketBackend {
    // 网络线程只读不可变规则；Bukkit 玩家数据全部在 refresh 的主线程中读取。
    private volatile Rules rules = new Rules(Set.of(), Set.of(), Set.of(), Map.of(), true);
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
            if (snapshot.enabled && actions.equals(EnumSet.of(Action.UPDATE_DISPLAY_NAME))) {
                // 托管名称已随本次合批刷新同步，过滤 Paper setter 的重复单条名称广播。
                List<PlayerInfo> remaining = packet.getEntries().stream()
                        .filter(entry -> !snapshot.displayNames.containsKey(entry.getProfileId())).toList();
                if (remaining.size() != packet.getEntries().size()) {
                    if (remaining.isEmpty()) event.setCancelled(true);
                    else {
                        packet.setEntries(remaining);
                        event.markForReEncode(true);
                    }
                }
                return;
            }
            List<PlayerInfo> entries = new ArrayList<>();
            boolean changed = false;
            for (PlayerInfo entry : packet.getEntries()) {
                UUID id = entry.getProfileId();
                remember(entry, actions);
                if (actions.contains(Action.ADD_PLAYER)) known.add(id);
                PlayerInfo copy = new PlayerInfo(entry);
                // 排队中的原版初始化包和核心刷新包都使用最新名称。
                // 使用主线程发布的托管名称，不在网络线程读取 Bukkit 玩家状态。
                Component displayName = snapshot.displayNames.get(id);
                if (snapshot.enabled && displayName != null && actions.contains(Action.UPDATE_DISPLAY_NAME)) {
                    copy.setDisplayName(displayName);
                    changed = true;
                }
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
    public void refresh(List<Player> online, Set<UUID> hidden, Set<UUID> normal, Map<UUID, Component> displayNames) {
        synchronize(online, hidden, normal, displayNames, false);
    }

    private void synchronize(List<Player> online, Set<UUID> hidden, Set<UUID> normal,
                             Map<UUID, Component> displayNames, boolean restoreNative) {
        Set<UUID> onlineIds = new HashSet<>();
        online.forEach(player -> onlineIds.add(player.getUniqueId()));
        rules = new Rules(Set.copyOf(onlineIds), hidden, normal, Map.copyOf(displayNames), !restoreNative);

        for (Player viewer : online) {
            if (PacketEvents.getAPI().getPlayerManager().getUser(viewer) == null) continue;
            Set<UUID> known = knownEntries.computeIfAbsent(viewer.getUniqueId(), id -> ConcurrentHashMap.newKeySet());
            List<UUID> remove = new ArrayList<>();
            for (UUID id : known) {
                if (!onlineIds.contains(id)) remove.add(id);
            }
            Map<EnumSet<Action>, List<PlayerInfo>> updates = new LinkedHashMap<>();
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
                if (displayNames.containsKey(id)) info.setDisplayName(displayNames.get(id));
                EnumSet<Action> actions = EnumSet.of(Action.UPDATE_LISTED, Action.UPDATE_GAME_MODE,
                        Action.UPDATE_DISPLAY_NAME, Action.UPDATE_LATENCY, Action.UPDATE_LIST_ORDER, Action.UPDATE_HAT);
                if (known.add(id)) {
                    actions.add(Action.ADD_PLAYER);
                    // 保留原始签名聊天会话；不为已有条目重置聊天会话。
                    if (info.getChatSession() != null) actions.add(Action.INITIALIZE_CHAT);
                }
                updates.computeIfAbsent(actions, key -> new ArrayList<>()).add(info);
            }
            // 同一观察者、同一组操作的所有条目合入一个包；新条目和已有条目分别同步。
            for (var update : updates.entrySet()) {
                // 保留监听流程，真正编码发送时再以最新快照校正名称，避免旧刷新包晚到。
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
                        new WrapperPlayServerPlayerInfoUpdate(update.getKey(), update.getValue()));
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
        // 立即撤销离线玩家规则，让本 tick 的原版删除包通过；无需立即刷新全服。
        Rules snapshot = rules;
        Set<UUID> online = new HashSet<>(snapshot.online);
        Set<UUID> hidden = new HashSet<>(snapshot.hidden);
        Set<UUID> normal = new HashSet<>(snapshot.normal);
        Map<UUID, Component> displayNames = new HashMap<>(snapshot.displayNames);
        online.remove(player);
        hidden.remove(player);
        normal.remove(player);
        displayNames.remove(player);
        rules = new Rules(Set.copyOf(online), Set.copyOf(hidden), Set.copyOf(normal),
                Map.copyOf(displayNames), snapshot.enabled);
        originals.remove(player);
        knownEntries.remove(player);
        // 其他观察者的条目由原版删除包或下一 tick 的同步清理，保留引用以便补发删除。
    }

    @Override
    public void close() {
        synchronize(new ArrayList<>(Bukkit.getOnlinePlayers()), Set.of(), Set.of(), Map.of(), true);
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        originals.clear();
        knownEntries.clear();
    }

    private record Rules(Set<UUID> online, Set<UUID> hidden, Set<UUID> normal,
                         Map<UUID, Component> displayNames, boolean enabled) {}
}
