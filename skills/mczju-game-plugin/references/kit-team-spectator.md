# Team and spectator kits (`game.kit.team`, `game.kit.spectator`)

Reusable mechanisms extracted from the CoreWar and HungerGame child plugins. Verify the resolved MGC dependency actually contains these packages before using them.

Both kits are per-round session objects keyed by the exact `AbstractGame` instance, so two rooms of the same game never share state. All methods must run on the server thread. Neither kit changes membership, profiles, inventories or scoring; the core invokes their cleanup from its end/abort/cancel and leave-game flows, and the game calls the gameplay entry points from its death strategy.

## Team kit

Packages: `game.kit.team`. `GameTeam` is an immutable definition (`id`, MiniMessage `displayName`, `NamedTextColor`, `Material`). `TeamSession` only stores who is on which team; there is no auto-balance and no team-pick menu unless the game implements one.

This is not `Party`. `Party` is a cross-round lobby group and `splitParty()` really disbands it; `TeamSession` tracks round-local factions only, disappears when the round ends, never touches parties, and can put party members on opposite teams.

```java
// onGameStart(): after profile switching, before giving round loadouts
TeamSession teams = new TeamSession(this, List.of(RED, BLUE))
        .friendlyFire(true)   // cancel same-team damage
        .tabPrefixes(true)    // colored Tab prefix through the core TabManager
        .voiceGroups(true);   // one Simple Voice Chat group per team
teams.assign(playerExt, RED);          // manual assignment
teams.assignAll(game.getPlayers(), BLUE);
teams.syncVoiceGroups();               // rebuild groups after membership changes
```

- `assign` rejects players that are offline, not in this exact game instance, or a team that was not declared in this session. Calling it again moves the player.
- Queries: `teamOf(Player/PlayerExt/UUID)`, `isTeammate(a, b)`, `hasTeam`, `membersOf(team)`, `onlineMembersOf(team)`, `size()`.
- Same-team damage cancellation is handled by the core-registered `TeamGuardListener` for melee, projectiles, primed TNT and area effect clouds. Unassigned players (referees, spectators) keep vanilla damage rules.
- Tab prefixes go through `MCZJUGameCore.getTabManager()`; never set player list names directly. Clearing a team prefix (`unassign`, `tabPrefixes(false)`, `reset()`) only calls `setPrefix(game, player, (Component) null)`; it deliberately leaves suffixes, hidden state and spectator styling alone, because full round cleanup is owned by the core's leave/end flow. Prefix failures after the game ended are swallowed and logged at debug level, so team data stays valid.
- Voice groups use `VoiceGroupUtil`. Group name is the team's plain display name, suffixed with the room name when the room is known. Call `syncVoiceGroups()` after joins, quits, assignments and at start; `cleanupVoiceGroups()` runs from `reset()`.
- `onPlayerQuit(PlayerExt)` clears that player's assignment and Tab prefix; the core-registered listener calls it, and the core calls `TeamSession.forgetPlayer(player)` from its own leave-game path.
- The core calls `reset()` from its end/abort/cancel flow, so Tab prefixes, voice groups and session registration are cleaned even if the child plugin forgets. Calling it again from the game's terminal hooks is idempotent and recommended, but never rely on it alone for voice-group refreshes during play.

## Spectator kit

Packages: `game.kit.spectator`. One `SpectatorSession` supports both common death rules at once:

| Rule | Call | Reference game |
| --- | --- | --- |
| Permanent spectator (elimination) | `eliminate(player)`, `eliminate(player, anchor, message)` | HungerGame |
| Spectate while waiting for respawn | `awaitRespawn(player, watchLocation)` | CoreWar |

```java
// onGameInit()/onGameStart()
spectators = new SpectatorSession(this)
        .respawnSeconds(5)          // default wait before revive
        .reviveInvulnerableTicks(40) // no-damage ticks after revive
        .restoreHealthOnRevive(true) // resetState() on revive
        .defaultAnchor(room.getSpectatorSpawn());

// death strategy, inside PlayerDeathEvent (before the core sends a respawn packet)
@Override
public void onPlayerDeath(PlayerExt player, PlayerDeathEvent event) {
    spectators.awaitRespawn(player, null);            // 5 s, revive at anchor
    // or: spectators.eliminate(player, null, "<red>你被淘汰了");
}
```

- Extended form: `awaitRespawn(player, seconds, watchLocation, Function<Player, Location> respawnLocation, Consumer<PlayerExt> onRevived)`. A null respawn result falls back to the watch location; `onRevived` runs after the player is back in survival at the destination.
- `revive(player)` forces an early revive; `cancelAwaitRespawn(player)` stops the countdown but leaves the player spectating.
- Queries: `isSpectating`, `isWaitingRespawn`, `isEliminated`, `anchorFor`, `secondsLeft`, `spectators()`, `waitingRespawn()`.

### Display is configurable, not hard-coded

Titles, chat/action-bar text and sounds live in `SpectatorNotices`, reachable through `session.notices()`. Templates containing `%d` receive the remaining seconds. `SpectatorNotices.silent()`, `notices().sounds(false)`, `notices().chat(false)` and `notices().titles(false)` switch the built-in presentation off while the mechanism keeps working; `eliminateText`, `reviveText`, `respawnText` and `sounds(eliminate, countdown, revive)` replace it. For full control turn the built-in display off and use `onSpectate(Consumer<SpectatorTransition>)` / `onRevive(...)`, which run after the mode and position changes are applied. An explicit message passed to `eliminate(player, anchor, message)` is sent even when `chat(false)`.

### Cleanup, leaving the round and auto-reconnect

- The core calls `reset()` from its end/abort/cancel flow (before members are removed) and `SpectatorSession.releaseFor(player)` from its leave-game flow. Both drop the registration, cancel the countdown and queued mode switches, and put the player into `ADVENTURE` so spectator mode never leaks into the lobby. Calling `reset()` again from the game's terminal hooks is idempotent.
- Do not re-apply modes in the game's quit strategy: membership removal already handles it.
- Spectator state is only valid while the player still belongs to the exact game instance: every countdown tick, revive callback, `reapply` and callback firing re-checks ownership. Leaving mid-countdown (command quit, joining another game) drops the registration and stops the timer instead of switching mode, teleporting back or running the old `onRevived`.
- `DefaultGameManager.solveGameEnd()` releases both kit sessions (end, abort, cancel, forced stop, plugin disable), catching per-session failures so the remaining core cleanup still runs.
- **Auto-reconnect is not provided by this kit.** It depends on the game implementing `AutoReconnect` and its quit strategy keeping the player in the round on `DISCONNECT`. Only then does the core re-add the player on profile load and the guard listener restore spectator mode, anchor and remaining countdown through `reapply`. Creating a `SpectatorSession` alone never reconnects anyone.

### Mode switching safety

Never call `player.setGameMode(...)` for death or revive transitions. Use `utils.PlayerModeSwitch` (the kit does this internally):

- `toSpectator/toSurvival/toMode(player, mode, after)` closes the open inventory, leaves vehicles, clears `setSpectatorTarget(null)` and applies the mode. When the player is still on the death screen it sends `spigot().respawn()` on the next tick, and it does **not** re-apply the mode after that respawn — the lobby profile restore decides the final mode. Only applying the mode without the respawn packet leaves the client on the death screen while the server already sees a spectator.
- A rejected `setGameMode` (another plugin cancelling `PlayerGameModeChangeEvent`) triggers a single token-checked retry; successful switches leave no delayed task behind, so nothing can overwrite the mode the core restores later.
- Leaving spectator without clearing the spectator target leaves the camera following the previous target, which looks like a freeze.
- `isOnDeathScreen(player)` detects `isDead()`/health `<= 0`; `forceRespawn(player, after)` drains a pending death screen without changing mode.
- Every switch takes a generation token; a queued task only runs if it is still the newest switch for that player. `PlayerModeSwitch.invalidate(player)` (called by the core on leave-game) and any newer switch make pending work obsolete, so a late task cannot rewrite the mode after the player returned to the lobby or joined another room.

### Core-registered guard listener

`SpectatorGuardListener` is registered by MGC itself; do not register it again.

- `PlayerRespawnEvent` (HIGHEST): the respawn point of a tracked spectator is pinned to the anchor, and the spectator mode is re-applied one tick later.
- `PlayerDeathEvent` (MONITOR): a tracked permanent spectator killed by another plugin is pulled back out of the death screen one tick later. Players waiting for respawn are skipped.
- `PlayerQuitEvent` (MONITOR): countdown stops, registration is kept.
- `PlayerProfileLoadedEvent` (MONITOR): after reconnect (and potential auto-rejoin) the spectator mode, anchor and remaining countdown are restored.

## Checklist

- Create the session after the game profile has been applied (normally `onGameStart`). The core cleans up on end/abort/cancel and on leave-game; explicit `reset()` in the game's terminal hooks is idempotent.
- Keep elimination broadcasts, drops, win checks and score changes in the game (or in `onSpectate`/`onRevive` callbacks); the kits only own mode, position, countdown, team metadata and the default notices.
- Confirm the game still validates round ownership before applying damage results or delayed callbacks: both kits resolve state by the exact game instance and return null/no-op for players outside it.
- Server verification needed for: same-team melee/projectile/TNT damage, Tab prefix cleanup after end, voice group creation/removal, death screen without freeze, camera release after revive, spectator respawn point, reconnect during a respawn countdown with `AutoReconnect` enabled, and a player leaving mid-countdown not keeping spectator mode.
