package com.github.mczjuops.mczjugamecore.player.party;

public enum RemoveResult {
    SUCCESS,
    SUCCESS_PARTY_DISBANDED, // 移除后只剩队长，自动解散
    NOT_IN_PARTY,
    NOT_LEADER,
    TARGET_NOT_IN_PARTY,
    CANNOT_REMOVE_SELF
}
