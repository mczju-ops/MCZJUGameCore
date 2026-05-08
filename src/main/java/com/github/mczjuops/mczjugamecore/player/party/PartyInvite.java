package com.github.mczjuops.mczjugamecore.player.party;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** 记录一个邀请信息，由于在接受时检验是否合法 */
public record PartyInvite(
        UUID inviterId,
        String inviterNameAtInvite,
        UUID inviteeId,
        @Nullable UUID targetPartyId,
        long expiresAtMillis
) {
    public boolean isExpired(long now) {
        return now > expiresAtMillis;
    }

    public PartyInvite withTargetPartyId(UUID partyId) {
        return new PartyInvite(
                inviterId,
                inviterNameAtInvite,
                inviteeId,
                partyId,
                expiresAtMillis
        );
    }
}
