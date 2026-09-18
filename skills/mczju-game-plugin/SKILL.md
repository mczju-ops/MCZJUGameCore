---
name: mczju-game-plugin
description: Design, implement, review, debug, and test Minecraft Paper minigame plugins built on MCZJUGameCore (MGC). Use when working on an MGC child plugin involving game lifecycle, game/room classes, wait/death/quit strategies, mid-game joining, registration, PlayerExt, persistent PlayerData, menus, leaderboards, parties, MGCItem, or related MGC managers and utilities.
---

# Develop MCZJUGameCore plugins

Treat the target project's resolved MGC dependency and source as authoritative. The bundled references summarize the framework version from the source repository used to create this skill; verify signatures when the target uses another revision.

## Workflow

1. Inspect the target project before editing: read `pom.xml` or other build files, `plugin.yml`, the plugin main class, existing game/room/data classes, and tests. Search for `MCZJUGameCore`, `AbstractGame`, and manager registrations.
2. Clarify the game model from the request and existing code:
   - Use `SinglePlayerGame` for one-player rounds.
   - Use `OpenSessionGame` for a persistent shared session without round-level ending.
   - Otherwise use `AbstractGame` and explicitly choose wait, death, and quit behavior.
   - Decide whether rooms are selectable, whether mid-game joining is valid, and which data must persist.
3. Read [references/core-workflow.md](references/core-workflow.md) before creating or changing game lifecycle, room, registration, or shutdown behavior.
4. Read only the relevant sections of [references/features.md](references/features.md) when implementing PlayerData, menus, leaderboards, items, parties, or utilities.
5. Implement the smallest coherent vertical slice. Keep framework hooks thin; move substantial gameplay logic into focused services/listeners/tasks owned by the child plugin.
6. Register all required framework components in the child plugin's `onEnable()`. Register Bukkit listeners and commands through the child plugin normally.
7. Make shutdown and end paths idempotent. Cancel plugin-owned schedulers, remove spawned entities, restore/reset maps as required, and avoid retaining stale `PlayerExt`, `Player`, game, or room references.
8. Build and run available tests. For behavior that requires a server, state the exact Paper test steps and lifecycle transitions to exercise.

## Non-negotiable constraints

- Add `depend: [MCZJUGameCore]` to `plugin.yml`; keep Paper and MGC dependencies `provided` unless the target project deliberately uses another packaging model.
- Give registered game, room, leaderboard, player-data, and menu classes accessible constructors matching the framework's reflection usage. Game and room classes require an accessible no-argument constructor.
- Give every game, menu, item, leaderboard, and data namespace a stable, globally collision-resistant ID. Follow the existing project's naming scheme; prefer plugin-prefixed IDs for new components.
- Never call lifecycle hooks such as `onGameInit`, `onGameStart`, `onGameCancel`, `onGameAbort`, or `onGameEnd` directly. Request transitions through `MCZJUGameCore.getGameManager()`.
- Do not mutate game/room states or framework-owned membership collections to force a transition. Use managers.
- Treat `onGameInit()` as room preparation triggered when a game instance is created, often when the first player begins waiting—not as the start signal.
- Return `false` from `onGameInit()` when required room settings are missing or preparation fails cleanly.
- Mark modified persistent player data with `setModified(true)` after changes.
- Use Bukkit/Paper APIs on the server thread unless their API explicitly permits async access. Keep filesystem/database work off-thread, then marshal world/entity/player changes back to the server thread.
- Do not blindly copy version numbers from this skill. Preserve or verify the target project's Paper and MGC versions.

## Design and review checklist

- Verify every terminal path: normal end, waiting cancellation, runtime abort, player death, command leave, disconnect, plugin disable, and initialization failure.
- Verify party joins are atomic from the game's perspective and fit the wait strategy's capacity.
- Verify room state and modified fields are restored/saved appropriately.
- Verify player inventory/profile isolation is not bypassed by custom teleport or membership logic.
- Verify listeners ignore players outside this game and, for multi-room games, outside the relevant game instance.
- Verify scheduled tasks stop when their owning game ends or aborts.
- Verify persistent fields are serializable by the chosen MGC data/room implementation; keep transient fields private where the JSON reflection convention requires it.
- Verify MiniMessage strings and permissions, and exercise menu close/click behavior.
- Report server-only verification separately from compilation/unit tests.
