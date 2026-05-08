package com.github.mczjuops.mczjugamecore.player.party;

public enum LeaveResult {
    SUCCESS,
    SUCCESS_PROMOTED, // 队长离开，第一个成员升级为新队长
    SUCCESS_PARTY_DISBANDED_NO_LEADER, // 队长离开后仅剩一个队员，队伍解散
    SUCCESS_PARTY_DISBANDED_NO_MEMBERS, // 队员离开后仅剩队长，队伍解散
    NOT_IN_PARTY
}
