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

`/party voice` (also `/p voice`, existing `mgc.party` permission) lets any party member move the current leader and members into one new default voice group. Use `Party.getAllPlayer()` because `getMembers()` excludes the leader. The command reports unavailable voice service, no connected players, and creation failures, and broadcasts success to the party. Names use `Party-` plus the leader's Minecraft name. Each invocation creates a new group; offline/unconnected members are skipped. This is a snapshot action: new members/connections need another invocation; leaving/disbanding the party does not automatically leave voice chat. Verify leader/member invocation, help, aliases, console/non-party rejection, and voice-service/connection failures on Paper.
