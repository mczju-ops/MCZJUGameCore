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
   - Decide voice-group usage: team-versus-team games must provide a separate voice group for each team; other games default to no voice groups unless the user requests them.
3. Read [references/core-workflow.md](references/core-workflow.md) before creating or changing game lifecycle, room, registration, or shutdown behavior.
4. Read only the relevant sections of [references/features.md](references/features.md) when implementing PlayerData, menus, leaderboards, items, parties, or utilities (including optional voice-chat groups and `/party voice`).
5. Implement the smallest coherent vertical slice. Keep framework hooks thin; move substantial gameplay logic into focused services/listeners/tasks owned by the child plugin.
6. Register all required framework components in the child plugin's `onEnable()`. Register Bukkit listeners and commands through the child plugin normally.
7. Make shutdown and end paths idempotent. Cancel plugin-owned schedulers, remove spawned entities, restore/reset maps as required, and avoid retaining stale `PlayerExt`, `Player`, game, or room references.
8. When adding features, update the relevant development documentation, document public classes/functions with comments or Javadoc, and synchronize this project skill and its references.
9. Build and run available tests, respecting repository instructions about tests. For behavior that requires a server, state the exact Paper test steps and lifecycle transitions to exercise.
10. In the final report, summarize the important features implemented and explicitly state whether voice groups are used. If used, describe how players are grouped; distinguish implemented integration from server verification and report any unavailable voice-service limitation.

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

## Gameplay experience and configuration

- Give players appropriate guidance at every game phase, including waiting, countdown, start, active objectives or phase changes, and end/results. Explain what to do next and communicate relevant timing or outcomes through chat, titles, action bars, boss bars, or other suitable displays.
- Play suitable sound effects for important player interactions and game events, such as game start, item use, objective completion, and results. Match the sound and recipients to the event, with sensible volume and frequency.
- Put user-requested configurable options in the game's `GameRoom` class wherever practical, using serializable fields, useful defaults, and field descriptions consistent with the existing room settings system.
- When the game has especially many numerical tuning values, put those values in the child plugin's `config.yml` and provide a reload command. Validate reloaded values and document whether they apply immediately or from the next round; keep room-specific settings in `GameRoom` wherever practical.
- For team-versus-team games, implement per-team voice groups using `VoiceGroupUtil` and read the voice-chat section of [references/features.md](references/features.md). Keep opposing teams in separate groups, track group IDs, and clean up on all terminal paths. For other games, add voice groups only when requested. Handle unavailable voice service or unconnected players with appropriate feedback.

## Design and review checklist

- Verify every terminal path: normal end, waiting cancellation, runtime abort, player death, command leave, disconnect, plugin disable, and initialization failure.
- Verify party joins are atomic from the game's perspective and fit the wait strategy's capacity.
- Verify room state and modified fields are restored/saved appropriately.
- Verify player inventory/profile isolation is not bypassed by custom teleport or membership logic.
- Verify listeners ignore players outside this game and, for multi-room games, outside the relevant game instance.
- Verify scheduled tasks stop when their owning game ends or aborts.
- Verify persistent fields are serializable by the chosen MGC data/room implementation; keep transient fields private where the JSON reflection convention requires it.
- Verify MiniMessage strings and permissions, and exercise menu close/click behavior.
- Verify every game phase provides appropriate player guidance and important interactions have suitable sound effects.
- Verify requested settings are exposed in `GameRoom` where practical; if many numerical values use `config.yml`, verify the reload command and state when changes take effect.
- Verify team-versus-team games have separate team voice groups and cleanup; other games use none unless requested. Explicitly report voice-group usage and the important implemented features at delivery.
- Report server-only verification separately from compilation/unit tests.
