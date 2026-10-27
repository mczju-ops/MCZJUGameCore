---
name: mczju-game-plugin
description: Design, implement, review, debug, and test Minecraft Paper minigame plugins built on MCZJUGameCore (MGC). Use when working on an MGC child plugin involving game lifecycle, game/room classes, wait/death/quit strategies, mid-game joining, registration, PlayerExt, persistent PlayerData, menus, leaderboards, parties, MGCItem, reusable game.kit mechanisms, or related MGC managers and utilities.
---

# Develop MCZJUGameCore plugins

Treat the target project's resolved MGC dependency and source as authoritative. The bundled references summarize the framework version from the source repository used to create this skill; verify signatures when the target uses another revision.

## Read by task

Read only the matching references; do not preload every optional feature. Use [references/features.md](references/features.md) as a fallback index when the feature is unclear.

| Task | Read |
| --- | --- |
| Lifecycle, rooms, registration, reconnect and shutdown | [core-workflow.md](references/core-workflow.md) |
| PlayerExt, managers, persistent data | [player-data.md](references/player-data.md) |
| Tab names, prefixes and suffixes | [tab-display.md](references/tab-display.md) |
| Menus, categories and lobbies | [menus-lobbies.md](references/menus-lobbies.md) |
| Leaderboards | [leaderboards.md](references/leaderboards.md) |
| Items, parties, general utilities | [items-parties-utilities.md](references/items-parties-utilities.md) |
| TurnTimer or PlayerTargetSelector | [timers-selection.md](references/timers-selection.md) |
| Voice groups or /party voice | [voice-chat.md](references/voice-chat.md) |
| Wiki links; core reload commands when requested | [wiki-reload.md](references/wiki-reload.md) |
| Reusable game.kit mechanisms: seats, prompts, clocks, hands, HUD | [kit.md](references/kit.md), then only its relevant detail files |
| Explicit MGC/skill update or version check | [version-updates.md](references/version-updates.md) |

For future optional features, add a focused reference and a task row here. Keep shared lifecycle and safety constraints in this entrypoint; keep feature-specific APIs, examples, and edge cases in references. Split a feature further only when it has independently useful subtopics, and link those from its overview. Update the corresponding `docs/` guide and verify examples against source without copying the entire guide into the skill.

## Workflow

1. Inspect the target project before editing: read `pom.xml` or other build files, `plugin.yml`, the plugin main class, existing game/room/data classes, and tests. Search for `MCZJUGameCore`, `AbstractGame`, and manager registrations. Verify bundled API examples against the target project's resolved dependency and source; read references/version-updates.md only when the user requests an update or version check.
2. Clarify the game model from the request and existing code:
   - Use `SinglePlayerGame` for one-player rounds.
   - Use `OpenSessionGame` for a persistent shared session without round-level ending.
   - Otherwise use `AbstractGame` and explicitly choose wait, death, and quit behavior.
   - Decide whether rooms are selectable, whether mid-game joining is valid, and which data must persist.
   - Decide voice-group usage: only games with explicitly defined two opposing teams default to a separate voice group for each team. Multi-team games, free-for-all games, cooperative games, and games with unclear team structure default to no voice groups unless the user requests them. Always report this decision after implementing the plugin, including when no groups are created.
3. Read [references/core-workflow.md](references/core-workflow.md) before creating or changing game lifecycle, room, registration, or shutdown behavior.
4. Use the task table above for optional features. For reusable turn/card/board-game mechanisms, read [references/kit.md](references/kit.md) before designing new prompts, clocks, hands, or HUD code; use only the parts the game needs.
5. Implement the smallest coherent vertical slice. Keep framework hooks thin; move substantial gameplay logic into focused services/listeners/tasks owned by the child plugin.
6. Register all required framework components in the child plugin's `onEnable()`. Register Bukkit listeners and commands through the child plugin normally.
7. Make shutdown and end paths idempotent. Cancel plugin-owned schedulers, remove spawned entities, restore/reset maps as required, and avoid retaining stale `PlayerExt`, `Player`, game, or room references.
8. When adding features, update the relevant development documentation, document public classes/functions with comments or Javadoc, and synchronize this project skill and its references.
9. Build and run available tests, respecting repository instructions about tests. For behavior that requires a server, state the exact Paper test steps and lifecycle transitions to exercise.
10. Deliver the completion report described below, including rule assumptions, setup steps, verification limits, and any required user-run safety tests. Always report voice-group usage.

## Non-negotiable constraints

- Add `depend: [MCZJUGameCore]` to `plugin.yml`; keep Paper and MGC dependencies `provided` unless the target project deliberately uses another packaging model.
- Give registered game, room, leaderboard, player-data, and menu classes accessible constructors matching the framework's reflection usage. Game and room classes require an accessible no-argument constructor.
- Give every game, menu, item, leaderboard, and data namespace a stable, globally collision-resistant ID. Follow the existing project's naming scheme, for example `colorblindparty` or `myplugin_game`. The ID becomes a directory name under `plugins/MCZJUGameCore/`, so it must not contain `\ / : * ? " < > |` or control characters; a namespaced form such as `plugin:game` fails on Windows with an invalid-filename error, and game registration rejects it.
- Never call lifecycle hooks such as `onGameInit`, `onGameStart`, `onGameCancel`, `onGameAbort`, or `onGameEnd` directly. Request transitions through `MCZJUGameCore.getGameManager()`.
- Do not mutate game/room states or framework-owned membership collections to force a transition. Use managers.
- All child-plugin changes to in-game Tab player names, including prefixes and suffixes, **must** go through `MCZJUGameCore.getTabManager()` (`setPrefix`, `setSuffix`, and `resetPlayer` as appropriate). Never bypass it with Paper/Bukkit `playerListName(Component)` or `setPlayerListName(...)`, scoreboard team prefixes/suffixes intended to alter Tab names, or custom display-name packets. The core owns name snapshots, batched refreshes, and lifecycle cleanup; direct writes can be overwritten or conflict with that state. Read [references/tab-display.md](references/tab-display.md) before implementing these changes.
- Treat `onGameInit()` as room preparation triggered when a game instance is created, often when the first player begins waiting—not as the start signal.
- Return `false` from `onGameInit()` when required room settings are missing or preparation fails cleanly.
- Mark modified persistent player data with `setModified(true)` after changes.
- Whenever a child plugin stores configuration in its own `config.yml`, it **must** provide its own reload command (for example, `/<plugin-command> reload`) to hot-reload that configuration without restarting the server. This applies regardless of the number or type of settings.
- Use Bukkit/Paper APIs on the server thread unless their API explicitly permits async access. Keep filesystem/database work off-thread, then marshal world/entity/player changes back to the server thread.
- Do not blindly copy version numbers from this skill. Preserve or verify the target project's Paper and MGC versions.

Explain MGC reload commands only when the user asks how to reload. These are user-facing operations; do not add child-plugin adaptation instructions or proactively include reload guidance in development work.

## Gameplay experience and configuration

- Give players appropriate guidance at every game phase, including waiting, countdown, start, active objectives or phase changes, and end/results. Explain what to do next and communicate relevant timing or outcomes through chat, titles, action bars, boss bars, or other suitable displays.
- Even when the user does not explicitly request audiovisual effects, consider both suitable sound effects and particle effects for game items, abilities, and important interactions or events, such as game start, item use, objective completion, and results. Add effects where they help communicate activation, targets, range, impact, or outcomes. Match sounds and particles to the event and intended recipients, with sensible volume, frequency, particle count, and duration.
- Put user-requested configurable options in the game's `GameRoom` class wherever practical, using serializable fields, useful defaults, and field descriptions consistent with the existing room settings system.
- When the game has especially many numerical tuning values, put those values in the child plugin's `config.yml`; keep room-specific settings in `GameRoom` wherever practical.
- For any child-plugin `config.yml`, implement the required reload command with an administrative permission and success/failure feedback. Reload values from disk, validate them before applying, and refresh runtime settings and caches that depend on them; merely calling `reloadConfig()` while gameplay still uses stale cached values is insufficient. Keep the previous effective settings if validation fails. Document which changes apply immediately and which are safely deferred until the next round.
- Only for games with explicitly defined two opposing teams, default to implementing per-team voice groups using `VoiceGroupUtil` and read [references/voice-chat.md](references/voice-chat.md). Keep opposing teams in separate groups, track group IDs, and clean up on all terminal paths. For all other games, including multi-team games and unclear team structures, add voice groups only when requested. Handle unavailable voice service or unconnected players with appropriate feedback. After implementing the plugin, always report whether voice groups are created; if not, state that explicitly.

## Design and review checklist

- Verify every terminal path: normal end, waiting cancellation, runtime abort, player death, command leave, disconnect, plugin disable, and initialization failure.
- Verify party joins are atomic from the game's perspective and fit the wait strategy's capacity.
- Verify room state and modified fields are restored/saved appropriately.
- Verify player inventory/profile isolation is not bypassed by custom teleport or membership logic.
- Verify gameplay listeners, commands, items, and delayed callbacks enforce the current game-instance, phase, participant, and target ownership checks below, including after a player leaves or joins a different round.
- If maps are reset or NPCs/entities are removed, verify cleanup ownership and boundaries below and include the user-run safety test request in the completion report.
- Verify scheduled tasks stop when their owning game ends or aborts.
- Verify persistent fields are serializable by the chosen MGC data/room implementation; keep transient fields private where the JSON reflection convention requires it.
- Verify MiniMessage strings and permissions, and exercise menu close/click behavior.
- Verify every game phase provides appropriate player guidance, and that items, abilities, and important interactions have been considered for both suitable sound effects and particle effects, even when the user did not request them.
- Verify requested settings are exposed in `GameRoom` where practical. Whenever the child plugin uses `config.yml`, verify its own reload command, permission and feedback, updated runtime values/caches, and retention of previous effective settings on invalid input; state when changes take effect.
- Verify explicitly defined two-team opposing games default to separate team voice groups and cleanup; all other games use none unless requested. Always report whether voice groups are created, how players are grouped when used, and the important implemented features at delivery.
- Report server-only verification separately from compilation/unit tests.

## Gameplay scope and cleanup safety

- Before canceling an event or changing damage, inventory, score, movement, effects, or other gameplay state, resolve the player's current MGC game and verify it is the exact owning game instance, is still `RUNNING`, and permits that player's role to perform the action. A matching game class, room ID, world, cached player list, or previous participation is insufficient. Ignore players who have left, are in a lobby, or belong to another round; spectators and eliminated players must follow the game's explicit rules.
- Waiting-room, join/quit, result, and cleanup logic may intentionally run outside `RUNNING`. Give each such path an explicit allowed phase and ownership boundary consistent with the actual framework hook ordering; do not apply an unconditional `RUNNING` guard that breaks admission or restoration.
- Recheck current membership, owning round, and allowed phase when delayed tasks or async results actually apply changes on the server thread. Cancel obsolete work on leave/end and prevent callbacks from a previous round from affecting a new round in the same room. For damage, projectiles, and indirect interactions, verify both the actor/source and affected player/entity belong to the intended interaction before applying game rules.
- Track NPCs and other spawned entities by handles/UUIDs owned by this plugin and game instance. Use plugin-specific ownership metadata with a room/round identity where persistent identification is needed. Remove only confirmed owned entities; never use name, entity type, proximity, or a world-wide entity sweep as sole deletion criteria. Do not delete another game's or any lobby's NPCs, including MGC-managed entry NPCs.
- Limit map reset, block restoration, and template/world operations to the validated room region or a world exclusively owned by this game. Validate world identity and bounds and prevent overlapping active-room resets. Being inside a reset region does not establish ownership of an entity. If ownership or boundaries cannot be established, skip the destructive operation and report the unresolved requirement instead of broadening its scope.
- Apply these limits to normal end, abort, cancellation, initialization failure, plugin disable, and repeated cleanup. Code review and compilation do not establish runtime isolation. Whenever implementation includes map reset or NPC/entity removal, explicitly ask the user in the completion report to perform the safety test below; this is a handoff recommendation for testing by the user, not a requirement for the agent to run a server or a request for permission to finish the code.

## Completion report

After implementing code, use the following compact structure in the user's language. Scale detail to the change; mark a category as not applicable when appropriate rather than inventing configuration or commands. Never hide rule assumptions or a required cleanup safety test merely to shorten the report.

1. **Implemented behavior**: Summarize player-visible features and lifecycle behavior. Always state whether voice groups are created, how players are grouped if used, and any unavailable voice-service limitation.
2. **Rules to confirm**: Identify gameplay rules left unspecified or ambiguous by the request, the default actually implemented, and the concrete player-visible consequence that may differ from the user's intent. Cover relevant win/tie rules, elimination/respawn, disconnect/leave/rejoin, mid-game joins, teams, scoring/rewards, or timeouts. Distinguish implemented assumptions from missing features; explicitly say when no unresolved rule assumptions were identified. Proceed with reasonable reversible defaults when possible, but ask before implementing dependent behavior if the ambiguity prevents a correct or safe implementation.
3. **Setup and operation**: Give the actual artifact location, required runtime dependencies/permissions, and remaining administrator steps in execution order. Where applicable, include exact commands and IDs to create/edit/save rooms, required spawn/map/NPC parameters, game-category configuration and assignment, lobby/entry setup, joining/starting the game, and the child plugin's reload command with when changes take effect. Verify syntax against the target source/version; do not guess commands or claim a room/category is already configured merely because code exists. Call out relevant configuration migrations, backups, or restart requirements.
4. **Verification and remaining work**: Separate successful builds and checks actually performed from untested Paper behavior. List concrete user-run scenarios for the changed behavior, including exit-to-lobby isolation and concurrent rooms where relevant. State known limitations or unfinished work and the next action; do not present compilation as gameplay verification.
5. **Cleanup safety test — required when applicable**: If map reset or NPC/entity removal exists in the implementation, describe the ownership/bounds used and explicitly recommend that the user test on a backed-up test server with another active game/room and NPCs in other games and lobbies (including entry NPCs and nearby entities). Exercise normal end, abort/cancel, initialization failure and plugin disable/repeated cleanup where applicable. Ask them to confirm only this round's intended map/entities change, while other rooms, maps, NPCs, and players remain intact. State whether this runtime safety test has been performed; if not, leave it explicitly pending user verification.
