---
name: mczju-game-plugin
description: Design, implement, review, debug, and test Minecraft Paper minigame plugins built on MCZJUGameCore (MGC). Use when working on an MGC child plugin involving game lifecycle, game/room classes, wait/death/quit strategies, mid-game joining, registration, PlayerExt, persistent PlayerData, menus, leaderboards, parties, MGCItem, or related MGC managers and utilities.
---

# Develop MCZJUGameCore plugins

Treat the target project's resolved MGC dependency and source as authoritative. The bundled references summarize the framework version from the source repository used to create this skill; verify signatures when the target uses another revision.

## Check versions and update the skill

Check for updates only when the user asks to update MGC or this skill, check for updates, or determine whether either is up to date. Ordinary development and use of bundled API examples do not trigger a release check; verify API compatibility against the target project's resolved dependency and source instead.

When requested, use the official [GitHub Releases](https://github.com/mczju-ops/MCZJUGameCore/releases) to check whether a release with a newer version number exists before performing the update. Report the local version, the compared release tag, and whether an update is available. If GitHub cannot be reached or the local version cannot be identified, report that the check is inconclusive.

### Check whether MGC is current

1. Identify the child project's resolved `com.github.mczju-ops:MCZJUGameCore` dependency, including versions supplied by properties or dependency management. For Maven, use `mvn dependency:tree -Dincludes=com.github.mczju-ops:MCZJUGameCore` when needed. Separately check the server's installed MGC version using `/version MCZJUGameCore` or the packaged JAR's `plugin.yml`; compilation and runtime versions can differ. When working on MGC itself, read its project version from `pom.xml`.
2. Read the release tags and notes on GitHub. Compare numeric version components, ignoring an optional leading `v`: for example, `1.0.10` is newer than `1.0.9`. Do not compare version strings lexicographically or infer the newest version solely from publication dates. Use stable releases by default; consider prereleases only when the target intentionally uses them. Branch names, commit hashes, snapshots, and custom builds require checking their source revision and cannot be ranked by version number alone.
3. If a newer applicable release exists, report it and read its compatibility and migration notes. Checking does not itself update the project or server. When an MGC update is requested, align the dependency and server JAR with the chosen compatible release, rebuild the child plugin, and verify affected behavior on Paper.

### Check whether the skill is current

This skill is distributed in the MGC repository under `skills/mczju-game-plugin/` and currently has no independent version field. Track the release tag and, when available, commit from which the installed skill was copied; do not assume its version equals the project's dependency or server version.

1. Compare the installed skill's recorded source release with GitHub Releases. A higher release version means a newer release should be checked for skill updates.
2. Inspect `skills/mczju-game-plugin/` at that release tag and compare the complete directory with the installed copy, including `SKILL.md`, `references/`, and `agents/`. A newer MGC release may leave the skill unchanged; file comparison confirms whether the skill itself needs updating. If the tag has no skill directory, report that it does not supply this skill.
3. If the installed source tag is unknown, compare its files with the chosen release's skill directory before claiming it is current. A copy from the default branch may include unreleased APIs; identify it by commit and verify against the project's actual MGC dependency.

### Update the installed skill

1. Select a release from GitHub Releases and read its notes. Obtain the repository's **Source code (zip)** or **Source code (tar.gz)** for that tag, or check out the tag in a separate checkout. The plugin JAR is not the skill package. Use the skill directory from the selected tag rather than silently downloading the default branch.
2. Locate the installed `mczju-game-plugin` directory from the active skill entry or the user's skill installation configuration. For a standalone Codex installation, it is commonly `${CODEX_HOME:-$HOME/.codex}/skills/mczju-game-plugin`; do not mistake the repository's source copy for the active installed copy.
3. Preserve local customizations in a backup, then replace the installed directory with the complete `skills/mczju-game-plugin/` directory from the selected release. Copy all included resources together and remove obsolete upstream files so old references do not remain mixed with new instructions. Record the source release tag and commit in installation notes for future checks.
4. Reload skills or start a new session in the host application, then confirm the active skill path and compare its files with the selected release. Recheck the target's MGC API compatibility: updating the skill does not update the Maven dependency or server plugin, and examples for a newer API may require adaptation.

## Workflow

1. Inspect the target project before editing: read `pom.xml` or other build files, `plugin.yml`, the plugin main class, existing game/room/data classes, and tests. Search for `MCZJUGameCore`, `AbstractGame`, and manager registrations. Verify bundled API examples against the target project's resolved dependency and source; follow the release checks above only when the user requests an update or version check.
2. Clarify the game model from the request and existing code:
   - Use `SinglePlayerGame` for one-player rounds.
   - Use `OpenSessionGame` for a persistent shared session without round-level ending.
   - Otherwise use `AbstractGame` and explicitly choose wait, death, and quit behavior.
   - Decide whether rooms are selectable, whether mid-game joining is valid, and which data must persist.
   - Decide voice-group usage: only games with explicitly defined two opposing teams default to a separate voice group for each team. Multi-team games, free-for-all games, cooperative games, and games with unclear team structure default to no voice groups unless the user requests them. Always report this decision after implementing the plugin, including when no groups are created.
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
- Even when the user does not explicitly request audiovisual effects, consider both suitable sound effects and particle effects for game items, abilities, and important interactions or events, such as game start, item use, objective completion, and results. Add effects where they help communicate activation, targets, range, impact, or outcomes. Match sounds and particles to the event and intended recipients, with sensible volume, frequency, particle count, and duration.
- Put user-requested configurable options in the game's `GameRoom` class wherever practical, using serializable fields, useful defaults, and field descriptions consistent with the existing room settings system.
- When the game has especially many numerical tuning values, put those values in the child plugin's `config.yml` and provide a reload command. Validate reloaded values and document whether they apply immediately or from the next round; keep room-specific settings in `GameRoom` wherever practical.
- Only for games with explicitly defined two opposing teams, default to implementing per-team voice groups using `VoiceGroupUtil` and read the voice-chat section of [references/features.md](references/features.md). Keep opposing teams in separate groups, track group IDs, and clean up on all terminal paths. For all other games, including multi-team games and unclear team structures, add voice groups only when requested. Handle unavailable voice service or unconnected players with appropriate feedback. After implementing the plugin, always report whether voice groups are created; if not, state that explicitly.

## Design and review checklist

- Verify every terminal path: normal end, waiting cancellation, runtime abort, player death, command leave, disconnect, plugin disable, and initialization failure.
- Verify party joins are atomic from the game's perspective and fit the wait strategy's capacity.
- Verify room state and modified fields are restored/saved appropriately.
- Verify player inventory/profile isolation is not bypassed by custom teleport or membership logic.
- Verify listeners ignore players outside this game and, for multi-room games, outside the relevant game instance.
- Verify scheduled tasks stop when their owning game ends or aborts.
- Verify persistent fields are serializable by the chosen MGC data/room implementation; keep transient fields private where the JSON reflection convention requires it.
- Verify MiniMessage strings and permissions, and exercise menu close/click behavior.
- Verify every game phase provides appropriate player guidance, and that items, abilities, and important interactions have been considered for both suitable sound effects and particle effects, even when the user did not request them.
- Verify requested settings are exposed in `GameRoom` where practical; if many numerical values use `config.yml`, verify the reload command and state when changes take effect.
- Verify explicitly defined two-team opposing games default to separate team voice groups and cleanup; all other games use none unless requested. Always report whether voice groups are created, how players are grouped when used, and the important implemented features at delivery.
- Report server-only verification separately from compilation/unit tests.
