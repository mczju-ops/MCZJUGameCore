# Leaderboards

## Leaderboards

- Extend `PlayerDataLeaderboard` when ranking a numeric field from registered player data. Implement data class and field name; choose ascending order for times and descending for scores/wins.
- Extend `AbstractLeaderboard` for other sources and return raw `LeaderboardEntry` values from `fetchEntries()`; let MGC sort them.
- Register a globally unique leaderboard ID with `LeaderboardManager`.
- Refresh explicitly after score changes when freshness matters, or add `@AutoRefresh` when periodic eventual consistency is acceptable.
- Text-display entities are configured separately through MGC administrative commands. Do not assume registration creates a visible display.

Administrators (`mgc.dev`) and console can run `/mgcop leaderboard clear-player <leaderboardId> <playerName>` immediately. Player names are raw, case-insensitive server-known names, including offline players; no UUID is needed in the command or leaderboard entry. `PlayerDataLeaderboard.clearPlayerRecord(String)` finds existing data without auto-creating it, resets only the score field to **-100.0**, and saves immediately. Supported writable instance fields are byte/short/int/long/float/double and their wrappers. On save failure it restores the previous value and retains the dirty flag for retry. `fetchEntries()` excludes the reset value; zero remains valid. Existing -100.0 scores are excluded too.

Override `getResetValue()` and/or `hasRecord(double)` for different rules; reset values must be exactly representable by the field type and finite for default JSON storage. A new valid score can re-enter normally, without a restore command. Adapt score updates: a shortest-time `Math.min` must first handle -100.0 as missing, and a maximum-score update must account for legitimate scores below the sentinel. Do not delete the whole player file. Shared data class/field leaderboards share the cleared score.

For custom sources, override `AbstractLeaderboard.clearPlayerRecord(String playerName)` (default `UNSUPPORTED`) to mutate and persist the actual source; return `SUCCESS`, `NO_RECORD`, or `FAILED` as appropriate. Prefer `LeaderboardManager.clearPlayerRecord(leaderboardId, playerName)` on the server thread: it returns `LEADERBOARD_NOT_FOUND` for an unknown ID and refreshes all displays of the target and any PlayerData boards sharing the data class/field after success. Display refresh failures log errors without changing persisted success. Direct calls to the leaderboard do not refresh displays. Verify clear/re-entry/restart, zero scores, offline names, repeated/missing records without creation, shared-field displays, permissions, unsupported sources, and persistence failures on Paper.

Validate field existence and numeric compatibility. Format values in `renderLine`; keep data retrieval bounded because refresh may touch all entries.
