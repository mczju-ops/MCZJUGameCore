package com.github.mczjuops.mczjugamecore.player.party;

public enum AcceptResult {
    NO_VALID_INVITE,
    INVITE_EXPIRED,
    INVITER_OFFLINE, // 不会发生
    INVITEE_ALREADY_IN_PARTY,
    INVITER_PARTY_CHANGED,
    CREATED_PARTY,
    JOINED_PARTY
}
