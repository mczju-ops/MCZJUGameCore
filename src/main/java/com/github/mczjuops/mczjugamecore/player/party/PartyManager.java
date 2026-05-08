package com.github.mczjuops.mczjugamecore.player.party;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;

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
            return acceptPendingCreateInvite(inviter, invitee, invite);
        } else {
            return acceptJoinPartyInvite(inviter, invitee, invite);
        }
    }

    /** 无队伍邀请：自动创建队伍 */
    private AcceptResult acceptPendingCreateInvite(
            PlayerExt inviter,
            PlayerExt invitee,
            PartyInvite invite
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
}
