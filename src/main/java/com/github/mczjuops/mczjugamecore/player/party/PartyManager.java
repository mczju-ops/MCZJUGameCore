package com.github.mczjuops.mczjugamecore.player.party;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class PartyManager {

    private final List<Party> partyList = new LinkedList<>();
    private final List<PartyInvite> invites = new LinkedList<>();

    /**
     * 返回第一个匹配的队伍）
     * 如果玩家不在任何队伍中，返回 null
     */
    public Party getPlayerParty(PlayerExt player) {
        for (Party party : partyList) {
            if (party.hasPlayer(player)) return party;
        }
        return null;
    }

    public @Nullable Party getPartyById(UUID id) {
        for (Party party : partyList) {
            if (party.getId().equals(id)) {
                return party;
            }
        }
        return null;
    }

    public boolean isPlayerInParty(PlayerExt player) {
        return getPlayerParty(player) != null;
    }

    private void purgeExpiredInvites(long now) {
        invites.removeIf(invite -> invite.isExpired(now));
    }

    /*
     * 顺序：
     * 清理过期邀请
     * 目标玩家是否在线
     * 是否邀请自己
     * 被邀请人是否已经在队伍中
     * 邀请方是否已经和对方同队
     * 如果有权限规则，检查邀请方是否能邀请
     * 如果有权限规则，检查邀请方是否能邀请
     * 写入或刷新邀请
     * 给被邀请人发送提示
     */
    public InviteResult invite(PlayerExt inviter, PlayerExt invitee) {
        long now = System.currentTimeMillis();
        purgeExpiredInvites(now);

        if (inviter.equals(invitee)) {
            return InviteResult.CANNOT_INVITE_SELF;
        }

        Party inviteeParty = getPlayerParty(invitee);
        if (inviteeParty != null) {
            return InviteResult.INVITEE_ALREADY_IN_PARTY;
        }

        Party inviterParty = getPlayerParty(inviter);
        UUID inviterPartyIdAtInvite = inviterParty == null
                ? null
                : inviterParty.getId();

        PartyInvite invite = new PartyInvite(
                inviter.getUniqueId(),
                inviter.getName(),
                invitee.getUniqueId(),
                inviterPartyIdAtInvite,
                now + 60_000L
        );

        // 同一个邀请方给同一个被邀请方发邀请时，刷新旧邀请
        invites.removeIf(old ->
                old.inviterId().equals(inviter.getUniqueId())
                        && old.inviteeId().equals(invitee.getUniqueId())
        );

        invites.add(invite);

        if (inviterParty != null) {
            return inviterParty.getLeader().equals(inviter)
                    ? InviteResult.SUCCESS_AS_LEADER
                    : InviteResult.SUCCESS_AS_MEMBER;
        } else {
            return InviteResult.SUCCESS_AS_SOLO;
        }
    }

    /*
     * 顺序：
     * 清理过期邀请
     * 找到对应邀请
     * 没找到则提示没有有效邀请
     * 找到后立即移除这张邀请
     * 检查是否过期
     * 检查邀请方是否在线
     * 检查接受方是否已经在队伍中
     * 检查邀请方当前队伍是否和邀请时一致（特例：单人邀请多人时允许不一致，因为第一次被接受后创建了新队伍）
     * 如果邀请时无队伍：创建双人队伍，同时升级符合条件的邀请
     * 如果邀请时有队伍：加入原队伍
     * 清理相关旧邀请
     * 给队伍广播消息
     */
    public AcceptResult accept(PlayerExt invitee, String inviterName) {
        long now = System.currentTimeMillis();
        purgeExpiredInvites(now);

        PartyInvite invite = findInvite(invitee, inviterName);

        if (invite == null) {
            return AcceptResult.NO_VALID_INVITE;
        }

        // 先移除，避免重复接受
        invites.remove(invite);

        if (invite.isExpired(now)) {
            return AcceptResult.INVITE_EXPIRED;
        }

        Player inviterPlayer = Bukkit.getPlayer(invite.inviterId());

        if (inviterPlayer == null) {
            return AcceptResult.INVITER_OFFLINE;
        }

        if (getPlayerParty(invitee) != null) {
            return AcceptResult.INVITEE_ALREADY_IN_PARTY;
        }

        PlayerExt inviter = new PlayerExt(inviterPlayer);

        if (invite.targetPartyId() == null) {
            return acceptPendingCreateInvite(inviter, invitee);
        } else {
            return acceptJoinPartyInvite(inviter, invitee, invite);
        }
    }

    /** 无队伍邀请：自动创建队伍 */
    private AcceptResult acceptPendingCreateInvite(
            PlayerExt inviter,
            PlayerExt invitee
    ) {
        Party inviterCurrentParty = getPlayerParty(inviter);

        if (inviterCurrentParty != null) {
            return AcceptResult.INVITER_PARTY_CHANGED;
        }

        Party party = new Party(inviter);
        party.addMemberInternal(invitee);
        partyList.add(party);

        // 被邀请人已经入队，他收到的其他邀请全部失效
        removeInvitesForInvitee(invitee);

        // 把邀请方其他待建队邀请升级为加入这个新队伍的邀请（处理某个玩家同时邀请多个玩家的情况）
        promotePendingCreateInvitesFrom(inviter, party);

        return AcceptResult.CREATED_PARTY;
    }

    private void promotePendingCreateInvitesFrom(PlayerExt inviter, Party party) {
        long now = System.currentTimeMillis();
        UUID inviterId = inviter.getUniqueId();

        ListIterator<PartyInvite> iterator = invites.listIterator();

        while (iterator.hasNext()) {
            PartyInvite old = iterator.next();

            if (old.isExpired(now)) {
                iterator.remove();
                continue;
            }

            if (!old.inviterId().equals(inviterId)) {
                continue;
            }

            if (old.targetPartyId() != null) {
                continue;
            }

            iterator.set(old.withTargetPartyId(party.getId()));
        }
    }

    private AcceptResult acceptJoinPartyInvite(
            PlayerExt inviter,
            PlayerExt invitee,
            PartyInvite invite
    ) {
        Party inviterCurrentParty = getPlayerParty(inviter);

        if (inviterCurrentParty == null) {
            return AcceptResult.INVITER_PARTY_CHANGED;
        }

        if (!inviterCurrentParty.getId().equals(invite.targetPartyId())) {
            return AcceptResult.INVITER_PARTY_CHANGED;
        }

        if (getPlayerParty(invitee) != null) {
            return AcceptResult.INVITEE_ALREADY_IN_PARTY;
        }

        inviterCurrentParty.addMemberInternal(invitee);

        removeInvitesForInvitee(invitee);

        return AcceptResult.JOINED_PARTY;
    }

    private @Nullable PartyInvite findInvite(PlayerExt invitee, String inviterName) {
        UUID inviteeId = invitee.getUniqueId();

        for (PartyInvite invite : invites) {
            if (!invite.inviteeId().equals(inviteeId)) {
                continue;
            }

            if (invite.inviterNameAtInvite().equalsIgnoreCase(inviterName)) {
                return invite;
            }
        }

        return null;
    }

    private void removeInvitesForInvitee(PlayerExt invitee) {
        UUID inviteeId = invitee.getUniqueId();

        invites.removeIf(invite ->
                invite.inviteeId().equals(inviteeId)
        );
    }

    public DisbandResult disband(PlayerExt leader) {
        Party party = getPlayerParty(leader);
        if (party == null) return DisbandResult.NOT_IN_PARTY;
        if (!party.isLeader(leader)) return DisbandResult.NOT_LEADER;

        disbandPartyInternal(party);
        return DisbandResult.SUCCESS;
    }

    public RemoveResult remove(PlayerExt leader, PlayerExt target) {
        if (leader.equals(target)) return RemoveResult.CANNOT_REMOVE_SELF;

        Party party = getPlayerParty(leader);
        if (party == null) return RemoveResult.NOT_IN_PARTY;
        if (!party.isLeader(leader)) return RemoveResult.NOT_LEADER;
        if (!party.hasPlayer(target)) return RemoveResult.TARGET_NOT_IN_PARTY;

        party.removeMemberInternal(target);
        removeInvitesSentBy(target);

        // 移除后只剩队长一人，直接解散
        if (party.size() == 1) {
            disbandPartyInternal(party);
            return RemoveResult.SUCCESS_PARTY_DISBANDED;
        }

        return RemoveResult.SUCCESS;
    }

    public LeaveResult leave(PlayerExt player) {
        Party party = getPlayerParty(player);
        if (party == null) return LeaveResult.NOT_IN_PARTY;

        if (party.isLeader(player)) {
            return leaveAsLeader(player, party);
        } else {
            return leaveAsMember(player, party);
        }
    }

    private LeaveResult leaveAsLeader(PlayerExt leader, Party party) {
        removeInvitesSentBy(leader);

        // 除了队长外只剩一名成员，解散
        if (party.size() == 2) {
            disbandPartyInternal(party);
            return LeaveResult.SUCCESS_PARTY_DISBANDED_NO_LEADER;
        }

        // 还有其他成员，升级第一个成员为新队长
        party.promoteFirstMemberToLeader();
        return LeaveResult.SUCCESS_PROMOTED;
    }

    private LeaveResult leaveAsMember(PlayerExt member, Party party) {
        party.removeMemberInternal(member);
        removeInvitesSentBy(member);

        // 移除后只剩队长一人，自动解散
        if (party.size() == 1) {
            disbandPartyInternal(party);
            return LeaveResult.SUCCESS_PARTY_DISBANDED_NO_MEMBERS;
        }

        return LeaveResult.SUCCESS;
    }

    /**
     * 监听器调用此方法处理玩家下线
     * 在 leave() 的基础上额外清理该玩家收到的邀请
     * （下线后无法接受任何邀请，保留无意义）
     */
    public LeaveResult handlePlayerQuit(PlayerExt player) {
        removeInvitesForInvitee(player);
        return leave(player);
        // leave() 内部已清理发出的邀请；
        // 此处额外清理收到的邀请（下线则失效）
    }

    /**
     * 解散队伍的统一出口：
     * 1. 从列表移除队伍
     * 2. 清理以该队伍为目标的所有邀请
     */
    private void disbandPartyInternal(Party party) {
        partyList.remove(party);
        UUID partyId = party.getId();
        invites.removeIf(invite -> partyId.equals(invite.targetPartyId()));
    }

    /**
     * 清理某个玩家发出的所有邀请
     * 用于该玩家离队/被移除时，其以队伍名义发出的邀请全部作废
     */
    private void removeInvitesSentBy(PlayerExt player) {
        UUID playerId = player.getUniqueId();
        invites.removeIf(invite -> invite.inviterId().equals(playerId));
    }

    public TransferResult transfer(PlayerExt leader, PlayerExt target) {
        if (leader.equals(target)) return TransferResult.CANNOT_TRANSFER_TO_SELF;

        Party party = getPlayerParty(leader);
        if (party == null) return TransferResult.NOT_IN_PARTY;
        if (!party.isLeader(leader)) return TransferResult.NOT_LEADER;
        if (!party.hasPlayer(target)) return TransferResult.TARGET_NOT_IN_PARTY;

        party.transferLeader(target);
        return TransferResult.SUCCESS;
    }

    /**
     * 随机分队
     * 尽可能平均地将队伍分成多个，随机指定新队长
     *
     * @param originalParty 原队伍
     * @param splitCount 需要的队伍数量
     * @return 最终队伍，若无法分队，将返回原来的队伍
     * */
    public Party[] splitParty(@NotNull Party originalParty, int splitCount) {

        // 直接不分队
        if (splitCount <= 1) {
            return new Party[]{originalParty};
        }

        // 使用 partyList 中的真实对象
        Party party = getPartyById(originalParty.getId());
        if (party == null) {
            return new Party[]{originalParty};
        }

        int totalSize = party.size();

        // 每个队伍至少 2 人，所以总人数必须 >= splitCount * 2
        if (totalSize < splitCount * 2) {
            return new Party[]{party};
        }

        PlayerExt originalLeader = party.getLeader();

        /*
         * 当前模型：
         * leader 不在 members 中
         * 原 leader 固定成为第 1 支新队伍的 leader
         * 其他 leader 从原 members 中随机抽取
         */
        List<PlayerExt> remainingPlayers = new ArrayList<>(party.getMembers());
        Collections.shuffle(remainingPlayers, ThreadLocalRandom.current());

        List<PlayerExt> newLeaders = new ArrayList<>(splitCount);
        newLeaders.add(originalLeader);

        for (int i = 1; i < splitCount; i++) {
            newLeaders.add(remainingPlayers.removeLast());
        }

        // 剩余普通成员再次洗牌，提高随机性
        Collections.shuffle(remainingPlayers, ThreadLocalRandom.current());

        // 计算每支队伍目标人数，保证尽可能平均
        int[] targetSizes = createRandomBalancedSizes(totalSize, splitCount);

        /*
         * 先解散旧队伍：
         * 1. 从 partyList 移除旧队伍
         * 2. 清理旧队伍相关邀请
         */
        disbandPartyInternal(party);

        Party[] result = new Party[splitCount];

        int memberIndex = 0;

        for (int i = 0; i < splitCount; i++) {
            Party newParty = new Party(newLeaders.get(i));

            int needMembers = targetSizes[i] - 1; // 减去 leader

            for (int j = 0; j < needMembers; j++) {
                newParty.addMemberInternal(remainingPlayers.get(memberIndex++));
            }

            partyList.add(newParty);
            newParty.sender().info("<blue>服务器已调整当前队伍，通过/party list查看新队伍信息");
            result[i] = newParty;
        }

        return result;
    }

    private int[] createRandomBalancedSizes(int totalSize, int splitCount) {
        int baseSize = totalSize / splitCount;
        int remainder = totalSize % splitCount;

        int[] sizes = new int[splitCount];
        Arrays.fill(sizes, baseSize);

        /*
         * 多出来的人随机分配给若干支队伍。
         * 例如 10 人分 3 队：基础 3 人，余 1 人；
         * 最终会随机变成某一队 4 人，其他队 3 人。
         */
        List<Integer> indexes = new ArrayList<>(splitCount);
        for (int i = 0; i < splitCount; i++) {
            indexes.add(i);
        }

        Collections.shuffle(indexes, ThreadLocalRandom.current());

        for (int i = 0; i < remainder; i++) {
            sizes[indexes.get(i)]++;
        }

        return sizes;
    }
}
