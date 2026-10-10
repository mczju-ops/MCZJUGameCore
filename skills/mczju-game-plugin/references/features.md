# Optional MGC features

## Contents

- PlayerExt and managers
- Unified Tab display
- Persistent player data
- Menus
- Leaderboards
- Items
- Parties and utilities
- Optional voice-chat groups

## PlayerExt and managers

Wrap Bukkit players with `new PlayerExt(player)` when an MGC API expects framework behavior. `PlayerExt` delegates to managers and provides conveniences such as game/party lookup, display name, reset state, data lookup, and safe item giving. Do not treat it as an independent durable player model.

Access framework services through `MCZJUGameCore` getters, including game, player, room, party, item, menu, player-data, profile, and leaderboard managers. Note the historical accessor spelling `getPartymanager()` in the inspected source; verify the target version rather than correcting calls speculatively.

## Unified Tab display

**Mandatory for child plugins:** all in-game Tab player-name changes, including prefixes and suffixes, must use `MCZJUGameCore.getTabManager()`. Use `setPrefix`/`setSuffix` for decorations and `resetPlayer` to reset the player's game-owned Tab settings. Do not write names directly through `playerListName(Component)`/`setPlayerListName(...)`, scoreboard team prefixes/suffixes intended to alter Tab names, or custom display-name packets. These bypass the core's managed snapshots, batched refreshes, and cleanup, and can be overwritten or cause inconsistent names.

Use `MCZJUGameCore.getTabManager()` on the server thread. All viewers use the same rules; this API does not change the header/footer. Pass the actual active `AbstractGame` instance, not a registration prototype. Prefix/suffix and appearance changes require an online player who already belongs to that game. Unmodified names retain the core `player [game name]` format. The player name and brackets remain orange (`#DEB12D`); the game name uses the MiniMessage colors from `GameMeta.displayName()`, defaulting to white where no color is specified.

Changes and automatic refresh requests within a tick coalesce into one refresh on the next tick, using the final state. Neither client display nor `playerListName()` is guaranteed to change before a setter returns. Bukkit reads/name updates remain on the server thread; PacketEvents handles network sending. Entries are grouped per viewer/action set: one update packet for existing entries, up to two addition packets depending on chat-session initialization, and one removal packet when required. Redundant Paper name-only broadcasts for managed entries are filtered, and unchanged names skip the Paper setter. Disconnect rules are removed immediately so native removal packets pass; shutdown cancels pending work and restores display immediately.

```java
TabManager tab = MCZJUGameCore.getTabManager();
tab.setPrefix(game, player, "<red>[Red]</red> ");
tab.setSuffix(game, player, " <yellow>10 points</yellow>");
player.player().setGameMode(GameMode.SPECTATOR);
tab.setNormalAppearance(game, player, true); // preserves existing prefix/suffix

tab.setHiddenPlayer(game, player, true);
tab.setHiddenPlayer(game, player, false);
tab.setHiddenPlayers(game, game.getPlayers()); // replaces this game's blacklist
```

`TabManager` is in `com.github.mczjuops.mczjugamecore.player.tab`. Prefer the MiniMessage String overloads of `setPrefix` and `setSuffix`, with no automatic spacing. Do not pass null to the MiniMessage String overloads. Pass an empty string to restore no prefix or the core game-name suffix, respectively. For example: `tab.setPrefix(game, player, "")` clears the prefix, and `tab.setSuffix(game, player, "")` restores the default game-name suffix. Normal appearance removes spectator dimming without changing actual game mode, entity visibility or existing prefix/suffix. False restores vanilla appearance.

When PacketEvents is enabled, by default **all real online players remain in Tab**, including those hidden through Paper `hidePlayer`/`hideEntity`. Do not set an explicit visible list to retain hidden entities in Tab. Only the core hidden-player API hides entries; Paper `unlistPlayer` is overridden while the core is active. No entity spawn/show or server visibility changes occur.

Core-managed game names publish their new immutable display-name snapshot before Paper broadcasts the new names. Synthesized Tab updates also pass through the listener and use the latest snapshot when encoded, so old snapshots and delayed initialization/refresh packets cannot replace current prefixes/suffixes with a default name. Name ownership ends on leave/game end; unmanaged lobby names are not overridden. Also validate consecutive `setPrefix`/`setSuffix` calls in the same tick, replacing both and changing only one, with visible players and with two players hiding each other's entities. Verify final values appear on the next tick and many same-tick changes to existing players yield one combined core update per viewer, with no redundant managed name-only broadcasts. Include spectator mode, Tab hiding, both hide-before-decoration and decoration-before-hide order, a new viewer joining after entities were hidden, change-then-leave/disconnect/end within one tick, and shutdown with pending refresh work. These regression scenarios still require manual server validation.

`setHiddenPlayer(game, player, boolean)` adds/removes one hide request. `setHiddenPlayers(AbstractGame, Collection<PlayerExt>)` copies and replaces that game's entire blacklist; empty clears it. Hidden lists may include online players from other games or the lobby. Multiple games' lists are combined by union: any remaining request keeps that player hidden for all viewers. Removing/ending a game withdraws only its requests. New logins appear by default; ordering is unchanged. The old `setVisiblePlayers`/`resetVisiblePlayers` API is removed.

`resetPlayer(game, player)` clears that game's decorations, normal appearance and hide request for the player. `resetHiddenPlayers(game)` removes that game's entire blacklist. Core cleanup clears the player's owning-game changes on leave (including rejected joins), clears all of a game's settings on end/cancel/abort/room destruction, and removes disconnected players from every blacklist. Reapply desired changes after reconnect. Game cleanup resumes the core default (entity hiding never implies Tab hiding); core shutdown restores Paper's native visibility/listing and spectator appearance. Nothing persists across restarts.

PacketEvents is **optional** via `softdepend: [packetevents]`; use a compatible 2.14.0+ server plugin for packet features. Without it, the core starts normally and names/prefixes/suffixes still work. Entity hiding and spectator appearance follow native Paper behavior. Each call to `setNormalAppearance`, `setHiddenPlayer`, `setHiddenPlayers` or `resetHiddenPlayers` logs a console-only warning identifying the ineffective API, returns without modifying settings, and throws no dependency exception. No warning is sent to player chat or proactively on startup, login, automatic refresh or entity hide events. Child plugins still declare `depend: [MCZJUGameCore]` and need no PacketEvents compile dependency or packet types. Off-thread calls raise IllegalStateException; inactive game instances, prefix/suffix/appearance changes to players outside the owning game, or offline blacklist entries raise IllegalArgumentException. Null API arguments/list elements, including MiniMessage String prefix/suffix values, raise NullPointerException; use empty strings to reset MiniMessage decorations.

Validate with two clients: default game suffix, spectator appearance preserving prefixes/suffixes after mode changes, hidden entity retained in Tab **without any list API call**, explicit Tab hiding and restoration while the entity remains hidden, signed chat/skins, new viewers/world changes, overlapping blacklists and partial cleanup, batch replacement/empty lists, and cleanup on leave/reconnect/failed join/every end path. Without PacketEvents, verify normal core loading and prefix/suffix changes, and console-only warnings on every packet API call with no dependency exception. Startup/login/entity hide events should not proactively warn. With it installed, check native Paper listing resumes after core shutdown. See “全服统一的 Tab 展示” in `docs/dev-advanced.md` for the manual checklist.

## Persistent player data

Extend `JsonPlayerData` for per-player JSON persistence:

```java
public final class ExampleData extends JsonPlayerData {
    public int wins = 0;
    private transientRuntimeType runtimeOnly;
}
```

In the inspected implementation/docs, public fields are persisted while private fields are excluded. Use types supported by Gson/MGC adapters; do not assume Bukkit objects such as `ItemStack` serialize correctly.

Register before lookup:

```java
MCZJUGameCore.getPlayerDataManager()
        .registerPlayerData("example:data", ExampleData.class);
```

Retrieve through `PlayerExt.getData(ExampleData.class)` or the data manager. After mutation, always call `setModified(true)`. Avoid async Bukkit access while preparing async persistence.

## Menus

Extend `Menu`, call `super(player, args)`, and implement `setup()`, title, rows (1-6), and permission. Populate slots with `setSlot(slot, display)` or `setSlot(slot, display, action)`. Use `refresh()` after state changes; override `handleClose()` only when cleanup is required.

Open directly with `new ExampleMenu(player).open()`. If MGC's `/menu` command must construct it, register it using the actual `MenuFacade` API in the target version. The documentation shows a static `MenuFacade.registerMenu(...)`, while inspected sources may expose the facade through `MCZJUGameCore.getMenuFacade()`; resolve this discrepancy from the dependency source before coding.

Use `AlertMenu` for confirmation of impactful actions, but keep the confirmed action scoped and revalidate state inside its callback.

### Main menu game categories

MGC reads `game-categories` from its `config.yml`: each stable category ID maps to a MiniMessage `name`, a list of MiniMessage `lore` lines, and an optional Bukkit Material `icon` (e.g. `GRASS_BLOCK` or `DIAMOND_SWORD`). Icons are used in the main menu, category assignment menu, and category lobby settings. Missing icons default to `CHEST`; invalid, air, or non-item materials log a warning and fall back to `CHEST`. With zero or one category the main menu displays all games; with multiple categories it displays categories first, then the selected category's games. Category lists and per-category game lists use the existing 27-slot layout without pagination. Without categories, the full game list also retains the 27-game limit.

Administrators with `mgc.dev` use `/mgcop category`: choose a registered game in a chest menu (45 games per page), then choose an existing category in a non-paginated chest menu. Successful saves send feedback and return to the original game-list page. Assignments persist by game ID in MGC's `game-categories.yml`; child plugins need no `GameMeta` or registration changes. Unassigned games and assignments to deleted categories resolve to the first configured category. Keep each category within 27 games, including fallback games.

`MCZJUGameCore.getConfigManager().getGameCategories()` returns the ordered, read-only category map (`GameCategory(name, lore, icon)`, with `icon()` returning `Material`; the two-argument constructor keeps the default chest icon). `getGameCategory(gameId)` returns the effective category ID or null when no categories exist. `setGameCategory(gameId, categoryId)` must run on the server thread and returns false if the game/category is invalid or persistence fails; failed saves keep the previous assignment. `/mgcop reload` reloads categories and saved assignments; reopen menus after config changes.

### Category lobbies

`/mgcop lobby` configures the main lobby, all configured category lobbies, and all registered game lobbies in one chest menu (45 entries per page). Category entries reuse their MiniMessage name/lore; left click selects a location, right click removes it. Permissions and category existence are rechecked before saving selected locations.

`/lobby <category_id>` teleports to that category's lobby. Game IDs take priority when names collide; `/lobby category:<category_id>` explicitly selects a category, and `category:` is reserved for category destinations. Suggestions include game and category IDs. The no-argument form targets the main lobby and keeps `mgc.lobby` permission. All forms allow players outside games or in `WAITING` only; `STATING`, `RUNNING`, and `END` are rejected. Waiting players retain game membership and their current profile when teleported. Successful teleports send feedback.

Use `MCZJUGameCore.getLobbyManager().getCategoryLobby(categoryId)` (cloned location or null), `setCategoryLobby(categoryId, location)` (existing category, loaded world, server thread; boolean save result), and `removeCategoryLobby(categoryId)` (boolean indicating successful removal and save). Failed saves retain previous category locations and do not report success in the menu. Category IDs are case-sensitive as configured. Category locations persist separately in `category-lobbies.json`; existing main/game locations stay in `lobbies.json`. Deleted categories are not selectable or teleportable after config reload; retained locations become usable if the same ID is configured again. Reopen menus after reload. Category lobbies do not replace individual game lobby locations.

### Lobby persistence and world availability

Lobby files retain world names and coordinates without resolving worlds at startup. `getLobby`, `getMainLobby`, `getGameLobby`, and `getCategoryLobby` return a fresh location or null when the destination world is unloaded; records survive and become usable when the world loads. `hasLobby` and `getConfiguredLobbyIds` describe configured records, including unloaded worlds.

If either lobby JSON file fails to load (including empty files, malformed JSON, or invalid locations), writes to that file are blocked for the plugin lifetime; repair it and restart before changing its lobbies. The other file remains independently writable. Main/game `setLobby` retains its void signature and throws `IllegalStateException` on persistence failure; removal and category setters return false on failure. Failed writes retain previous in-memory settings and menus report failure. Saves use a completed temporary file followed by an atomic replacement where supported, with a normal replacement fallback. Legacy `config.yml` `lobby-spawn` migrates only when `lobbies.json` does not exist, never for an existing empty object or unreadable file.

Lobby setters/removers persist immediately; shutdown does not save lobby snapshots. Public `save()` skips unchanged data, so manual file edits made while the server runs survive a restart. Before an actual mutation is written, disk contents must match the last loaded/saved contents; external modification, creation, or deletion causes the operation to fail without overwriting the file. Restart to load those edits before configuring more lobbies. Startup logs report the absolute storage path and loaded record count. Missing files are not automatically created as empty configurations.

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

## Items

Extend `MGCItem`, implement a stable namespaced `getId()`, and build the raw item in `createRawItem()` using `ItemBuilder`. Register one item instance with `ItemManager`. Give players `getItem()` or an ID through supported `PlayerExt` APIs—do not call `createRawItem()` as the public delivery path.

MGC identifies items through persistent data. In interaction listeners, ask `ItemManager` for the registered item or use `isThis`; then run plugin-specific behavior. Check event hand/action, cancellation policy, item consumption, cooldowns, and players' current game instance.

## Parties and utilities

`/party warp` (also `/p warp`, existing `mgc.party` permission) is leader-only. Before teleporting anyone, it checks every party player, including the leader: each must have no game or be in `GameState.WAITING`. Any `STATING`, `RUNNING`, or `END` game rejects the whole operation. Members teleport to the leader's captured location; the leader stays in place. This changes position only, preserving game membership and profiles. Offline members or cancelled teleports report failure to the leader, while other members are still attempted; partial failure does not report whole-party success. Verify help/aliases, console/non-party/non-leader rejection, outside/waiting/mixed membership, cross-world teleport, blocked states for both leader and members, and cancelled teleports on Paper.

The default game manager lets a party leader bring the party into a game and attempts rollback if the wait strategy rejects it. Design capacity and team allocation around whole parties. Non-leaders should not initiate a party join.

Useful helpers include:

- `Sender` implementations for MiniMessage output to players, games, parties, or console.
- `ItemBuilder` for item/menu construction.
- `CountDown` for tick-based countdown callbacks; retain/cancel ownership on game cleanup.
- `LocationSelector` via `PlayerExt` for administrative point selection.
- `TextParser` for parsing MiniMessage when required by an API; prefer MiniMessage String overloads when available.
- `TimeFormat` for duration display.
- `CommandUtils` for Brigadier completion helpers.
- `DialogBuilder` for Paper dialogs; verify against the target Paper version.

Inspect utility Javadocs/source before using overloads, because these APIs are more likely to change than the architectural contracts.

## Optional voice-chat groups

Default to per-team voice groups only when a game explicitly has exactly two opposing teams. Multi-team games, free-for-all games, cooperative games, and unclear team structures use no groups unless requested. After implementing the plugin, always report whether groups are created, including an explicit statement when none are created.

MGC soft-depends on Simple Voice Chat (`voicechat`). Use `utils.VoiceGroupUtil` without importing voice-chat API types. All group mutations must run on the server thread:

```java
var groupId = VoiceGroupUtil.createGroup("example:red", playerExtList);
var customGroupId = VoiceGroupUtil.createGroup("example:blue", playerExtList,
        VoiceGroupUtil.Options.defaults()
                .withPassword("team-password")
                .withHidden(true)
                .withPersistent(true)
                .withType(VoiceGroupUtil.GroupType.ISOLATED));
groupId.ifPresent(VoiceGroupUtil::removeGroup);
customGroupId.ifPresent(VoiceGroupUtil::removeGroup);
```

`playerExtList` is a `Collection<PlayerExt>` (usually `List<PlayerExt>`); there is no dedicated PlayerExtList class. Creation returns `Optional<UUID>`, empty when the integration is unavailable/not ready or no online voice-connected players can join. Offline/unconnected players are skipped, duplicate UUIDs join once, and late connections are not automatically added. Defaults are no password, visible, non-persistent, OPEN; each invocation creates a fresh UUID even with the same name. Joining replaces the player's old group; cleanup does not restore it. NORMAL hears nearby non-group players, OPEN also lets nearby players hear the group, ISOLATED hears only group members.

`Options` is immutable; use its returned `withPassword`, `withHidden`, `withPersistent`, and `withType` values. Persistent means keeping an empty group, not saving across restarts. Hidden only hides the client list; passwords restrict manual joining, not utility-assigned membership. Blank names throw IllegalArgumentException, null arguments/members throw NullPointerException, off-thread calls, names rejected by voicechat, or creation/join cancellation by another plugin throw IllegalStateException; failed creation attempts cleanup before rethrowing.

Store returned UUIDs and call `removeGroup` on end, cancellation, abort, and child-plugin disable; clear stored IDs afterward. Removal only touches utility-created groups and disconnects their current members, including manual late joiners. It returns true on deletion or prior automatic disappearance, false when unavailable, unowned, or deletion fails. Non-persistent groups disappear when empty; MGC shutdown attempts cleanup of all remaining utility-created groups. Verify absence of voicechat, unavailable connections, group modes/options, replacement of old memberships, and cleanup on a real Paper server. The voicechat API dependency is optional/provided and must not be shaded into MGC or child plugins.

`/party voice` (also `/p voice`, existing `mgc.party` permission) lets only the party leader move the current leader and members into one new default voice group. Use `Party.getAllPlayer()` because `getMembers()` excludes the leader. The command reports unavailable voice service, no connected players, and creation failures, and broadcasts success to the party. Names use `Party-` plus the leader's Minecraft name. Each invocation creates a new group; offline/unconnected members are skipped. This is a snapshot action: new members/connections need another invocation by the leader; leaving/disbanding the party does not automatically leave voice chat. Verify leader success and non-leader rejection, help, aliases, console/non-party rejection, and voice-service/connection failures on Paper.
