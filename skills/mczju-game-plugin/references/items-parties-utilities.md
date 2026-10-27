# Items, parties and utilities

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
- `TurnTimer` for pausable per-turn/action countdowns with per-player display (see [timers-selection.md](timers-selection.md)).
- `PlayerTargetSelector` for GUI-based player/target selection (see [timers-selection.md](timers-selection.md)).
- `LocationSelector` via `PlayerExt` for administrative point selection.
- `TextParser` for parsing MiniMessage when required by an API; prefer MiniMessage String overloads when available.
- `TimeFormat` for duration display.
- `CommandUtils` for Brigadier completion helpers.
- `DialogBuilder` for Paper dialogs; verify against the target Paper version.

Inspect utility Javadocs/source before using overloads, because these APIs are more likely to change than the architectural contracts.
