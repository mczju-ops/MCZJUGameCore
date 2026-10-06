# Optional MGC features

## Contents

- PlayerExt and managers
- Persistent player data
- Menus
- Leaderboards
- Items
- Parties and utilities
- Optional voice-chat groups

## PlayerExt and managers

Wrap Bukkit players with `new PlayerExt(player)` when an MGC API expects framework behavior. `PlayerExt` delegates to managers and provides conveniences such as game/party lookup, display name, reset state, data lookup, and safe item giving. Do not treat it as an independent durable player model.

Access framework services through `MCZJUGameCore` getters, including game, player, room, party, item, menu, player-data, profile, and leaderboard managers. Note the historical accessor spelling `getPartymanager()` in the inspected source; verify the target version rather than correcting calls speculatively.

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

`/lobby <category_id>` teleports to that category's lobby. Game IDs take priority when names collide; `/lobby category:<category_id>` explicitly selects a category, and `category:` is reserved for category destinations. Suggestions include game and category IDs. Existing no-argument main-lobby behavior, `mgc.lobby` permission and in-game restriction remain. Successful teleports send feedback.

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
- `TextParser` for MiniMessage components.
- `TimeFormat` for duration display.
- `CommandUtils` for Brigadier completion helpers.
- `DialogBuilder` for Paper dialogs; verify against the target Paper version.

Inspect utility Javadocs/source before using overloads, because these APIs are more likely to change than the architectural contracts.

## Optional voice-chat groups

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
