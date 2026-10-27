# Inventory isolation and safe item timing

## Core owns profiles

**Child plugins must never call profile switching, capture/apply, cache mutation, load/save, or shutdown APIs.** This includes `PlayerExt.switchProfile(...)`, the core's profile-manager/capture/storage getters, and classes under `profile/`. Do not copy their implementations, restore an old inventory snapshot, edit profile files, or add your own inventory switch on join/leave/end. Use MGC game-manager transitions; the core owns profile isolation.

Profiles cover the main inventory, armor, offhand, ender chest, XP level and XP progress. Position, effects, health and arbitrary game state are not a full player snapshot. Each game ID has a persistent inventory profile, shared across that game's rooms and rounds; joining is not guaranteed to give an empty inventory. Reset round equipment at the safe entry point if the rules require a fresh loadout. Keep persistent scores in PlayerData and bot hands in game-owned memory.

Normal flow: the core saves the current profile and applies the game profile **before** invoking the wait-admission callback or `onPlayerMidJoin`. At normal leave it invokes the quit/wait strategy and then restores the lobby profile. End/cancel/abort hooks run before the manager removes remaining members and restores their lobby profiles. These orders describe the current implementation, not permission to bypass managers. Admission rejection (`JOIN_FAIL`) is a special rollback path; it is not a child-plugin cleanup hook or a guarantee that lobby restoration has happened.

## When to give or clear items

| Timing | Safe child-plugin behavior |
| --- | --- |
| Constructor / `onGameInit()` | Prepare room and runtime state only. Init precedes the first member's profile switch; never give/clear items using the prospective joiner or cached players here. |
| Accepted wait-admission callback | The profile is already switched. After validating admission and exact instance membership, initialize only the newly accepted players. For party admission, validate the whole party before item changes. Do not grant equipment on a branch that will reject admission. |
| `onGameStart()` | Preferred place to clear old **game** equipment and give the round loadout to current `getPlayers()`. The state is `STATING` inside this hook and becomes `RUNNING` after it returns; a RUNNING-only helper would skip start initialization. |
| Accepted `onPlayerMidJoin()` / OpenSession `onPlayerJoin()` | Initialize the admitted player's game equipment synchronously, after acceptance/ownership checks. Rejoining can restore old game equipment, so choose explicitly whether to resume or reset it. |
| Gameplay event, command, timer or prompt result | On the server thread, require the exact current game instance, `RUNNING`, a permitted participant role, and valid action/target before changing inventory. Recheck when a scheduled/async result actually executes. |
| `onGameEnd()` / `onGameAbort()` / `onGameCancel()` | First stop game-owned item callbacks/tasks. Lobby restoration is automatic; clearing is optional. If round items must be discarded, clear synchronously only for players still owned by this instance, before the hook returns and before any nested leave/end transition. Init failure or cancellation may have no members. |
| Player quit/leave callback | Invalidate that player's tasks, menus and runtime handles; let the core restore their lobby inventory. Membership is already removed before the quit strategy runs, and it can re-enter whole-game termination. Do not sweep cached participants' inventories or schedule an inventory clear from here. |
| After a leave/end manager call returns, next tick, delayed cleanup, or child-plugin disable | Assume a player may already have lobby or another game's inventory. Never clear/give through a stale handle. Disable should request MGC termination of owned rounds while the core is available, then dispose of non-inventory resources; do not clear every remembered player's inventory. |

For a fresh round, execute this inside `onGameStart()` on the server thread; `giveRoundLoadout` is child-plugin code and must not switch profiles:

```java
for (PlayerExt player : getPlayers()) {
    if (player.getGame() != this) continue;
    player.player().getInventory().clear();
    giveRoundLoadout(player);
}
```

Only clear armor/offhand/ender chest or reset XP when those parts belong to this game's rules. A Bukkit inventory clear is not equivalent to clearing every profile field. At end, leaving round items in the game profile does not carry them into the lobby; reset them at the next safe entry if they must not persist across rounds. Clearing after lobby restoration destroys lobby items instead.

A raw `PlayerJoinEvent` is too early for game equipment: profile loading is asynchronous and can overwrite those items or capture them into the lobby profile. `PlayerProfileLoadedEvent` indicates login restoration is complete, not game admission. AutoReconnect still uses normal admission; restore game equipment in the admitted game hook, without manual profile calls.

## Keep items inside the round

Profile switching isolates inventory contents, not dropped items, external chests, transfers between players, or late rewards. Guard game-item drop/pickup/transfer/use and actor/target ownership so game equipment cannot escape through the world. For `Hand<T>`, see [kit-state-display.md](kit-state-display.md); `forget` discards runtime records, while `clear` changes a live inventory and obeys the timing rules above.

On Paper, start with distinctive lobby items (including armor/offhand/ender chest and XP), enter a game with a different loadout, then exercise normal end, abort, waiting leave/cancel, mid-join/reconnect and disable. Verify lobby contents survive, game items never appear there, and callbacks queued before exit cannot give/clear after exit or after joining another room. Re-enter the same game to check the intended reset/resume rule. Compilation alone does not verify inventory isolation.
