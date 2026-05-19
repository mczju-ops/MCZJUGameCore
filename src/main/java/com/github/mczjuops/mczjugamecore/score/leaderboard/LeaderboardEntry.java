package com.github.mczjuops.mczjugamecore.score.leaderboard;

public record LeaderboardEntry(
        String playerName,
        double value,
        String displayValue
) {}
