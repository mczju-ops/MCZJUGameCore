# Team and spectator kits (`game.kit.team`, `game.kit.spectator`)

Reusable mechanisms extracted from the CoreWar and HungerGame child plugins. Verify the resolved MGC dependency actually contains these packages before using them.

Both kits are per-round session objects keyed by the exact `AbstractGame` instance, so two rooms of the same game never share state. All methods must run on the server thread. Neither kit changes membership, profiles, inventories, scoring, or lifecycle transitions — keep those in the child plugin and its death strategy.

## Team kit

Packages: `game.kit.team`. `GameTeam` is an immutable definition (`id`, MiniMessage `displayName`, `NamedTextColor`, `Material`). `TeamSession` only stores who is on which team; there is no auto-balance and no team-pick menu unless the game implements one.

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
- Tab prefixes go through `MCZJUGameCore.getTabManager()`; never set player list names directly. Prefix failures after the game ended are swallowed and logged at debug level, so team data stays valid.
- Voice groups use `VoiceGroupUtil`. Group name is the team's plain display name, suffixed with the room name when the room is known. Call `syncVoiceGroups()` after joins, quits, assignments and at start; `cleanupVoiceGroups()` runs from `reset()`.
- `onPlayerQuit(PlayerExt)` clears that player's assignment and Tab prefix; the core-registered listener calls it for every live session.
- Call `reset()` from `onGameEnd`, `onGameAbort` and `onGameCancel`. It unassigns Tab prefixes, removes voice groups, clears assignments and unregisters the session.

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
- Call `reset()` from `onGameEnd`, `onGameAbort` and `onGameCancel`. It cancels countdowns and returns players who are still in this round to `SURVIVAL`, so nobody carries spectator mode into the lobby. Profile capture does not store gamemode, so this cleanup is what prevents the leak.

### Mode switching safety

Never call `player.setGameMode(...)` for death or revive transitions. Use `utils.PlayerModeSwitch` (the kit does this internally):

- `toSpectator/toSurvival/toMode(player, mode, after)` closes the open inventory, leaves vehicles, clears `setSpectatorTarget(null)`, applies the mode, and — when the player is still on the death screen — sends `spigot().respawn()` on the next tick and re-applies the mode. Only applying the mode without the respawn packet leaves the client on the death screen while the server already sees a spectator.
- Leaving spectator without clearing the spectator target leaves the camera following the previous target, which looks like a freeze.
- `isOnDeathScreen(player)` detects `isDead()`/health `<= 0`; `forceRespawn(player, after)` drains a pending death screen without changing mode.

### Core-registered guard listener

`SpectatorGuardListener` is registered by MGC itself; do not register it again.

- `PlayerRespawnEvent` (HIGHEST): the respawn point of a tracked spectator is pinned to the anchor, and the spectator mode is re-applied one tick later.
- `PlayerDeathEvent` (MONITOR): a tracked permanent spectator killed by another plugin is pulled back out of the death screen one tick later. Players waiting for respawn are skipped.
- `PlayerQuitEvent` (MONITOR): countdown stops, registration is kept.
- `PlayerProfileLoadedEvent` (MONITOR): after reconnect (and potential auto-rejoin) the spectator mode, anchor and remaining countdown are restored.

## Checklist

- Create the session after the game profile has been applied (normally `onGameStart`); `reset()` on every terminal path, including cancel and abort.
- Keep elimination broadcasts, drops, win checks and score changes in the game; the kits only own mode, position, countdown and team metadata.
- Confirm the game still validates round ownership before applying damage results or delayed callbacks: both kits resolve state by the exact game instance and return null/no-op for players outside it.
- Server verification needed for: same-team melee/projectile/TNT damage, Tab prefix cleanup after end, voice group creation/removal, death screen without freeze, camera release after revive, spectator respawn point, and reconnect during a respawn countdown.
