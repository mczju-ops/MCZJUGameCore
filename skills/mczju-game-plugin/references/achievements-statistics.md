# Statistics and achievements (MGC 1.5.0)

Use core managers on the server thread. Statistics/achievements reuse `AchievementPlayerData extends JsonPlayerData`, registered under reserved namespace `mgc_achievements`, separate from child data. Public non-final nested DTO fields are required by the existing serializer; do not persist records directly. Changes mark modified and use existing PlayerDataManager loading/autosave/quit/shutdown. There is no dedicated JSON writer, game UUID, settlement ID, or persistent result ledger.

## Event boundaries

Core lifecycle publishes `GameStartEvent` (successful RUNNING entry, member snapshot), `PlayerGameJoinEvent` (accepted mid-join only), `PlayerGameDeathEvent` (before death strategy). These notifications do not cancel gameplay. Statistics listens independently; it emits `GameStatisticChangedEvent`, which achievements listens to. Do not wire statistic calls into lifecycle hooks or use statistic-specific AbstractGame overrides.

`GameStatistic`: PLAY_TIME_SECONDS, KILLS, DEATHS, GAMES_PLAYED, WINS, LOSSES, DRAWS. Time is 20 active ticks per second, RUNNING/online/non-spectator only; subsecond remainder is lost on leaving/changing instances. Successful participation counts once per player per runtime instance, including reconnect dedup; open-session subround counts are not inferred.

Filter `GameStatisticCollectEvent` for practice phases/custom observers; cancel only that sample. Direct set/add is not a collection sample. `GameKillEvent`/`GameDeathEvent` separately filter kills/deaths without affecting gameplay; kill attribution may be corrected with setKillerId, but final killer must be a different eligible member of the same instance. Environment death offers a nullable killer for attribution.

Custom elimination: call `statistics.reportDeath(game, victimUUID, nullableKillerUUID)` before removing/spectating the victim. Same instance/victim/tick is deduplicated; do not retry across ticks. If the same custom elimination later produces vanilla death, cancel AUTOMATIC KILLS/DEATHS in GameStatisticCollectEvent and retain REPORTED_DEATH. No game-base override or UUID is needed.

```java
Bukkit.getPluginManager().callEvent(new GameWinEvent(game, winnerUUID));
Bukkit.getPluginManager().callEvent(new GameResultEvent(game, loserUUID, GameResultEvent.Outcome.LOSS));
// Also supports DRAW and constructors taking gameId instead of game.
```

Core consumes uncancelled results at MONITOR; **each event counts once, child plugin must emit once per settlement**. No cross-event/restart dedup is promised. Offline or departed logged-in players are supported. Results do not infer others' losses or increment participation. GameWinEvent shares GameResultEvent handlers.

`statistics.get(uuid,gameId,stat)` reads without creating data; `add(...,signedDelta)`/`set(...,nonnegativeValue)` support known offline players, reject negative totals/overflow, and emit changes. Avoid recursive same-stat changes in listeners.

## Definitions and triggers

Register games first, then immutable achievement definitions:

```java
var first = new AchievementKey("myplugin_game", "kills_100");
var next = new AchievementKey("myplugin_game", "kills_200");
manager.register(plugin, List.of(
    AchievementDefinition.statistic(first, "100 kills", "100 valid kills", Material.IRON_SWORD, GameStatistic.KILLS, 100),
    AchievementDefinition.statistic(next, "200 kills", "200 valid kills", Material.DIAMOND_SWORD, GameStatistic.KILLS, 200).withParent(first)
));
```

Types are in `achievement`, `statistics`, `event` packages. MiniMessage name/description (menus, chat and native UI), positive targets, stable achievement IDs `[a-z0-9_-]+`. Parent must be same game/owner, registered or in batch; missing/cyclic/duplicate definitions are rejected before registration. Parent controls native layout only; custom prerequisite logic belongs to child code. Statistics alone do not create achievements. Registration backfills qualified offline statistics without chat spam.

Factories: challenge(key,name,description,icon), counter(...,target), statistic(...,stat,target). Manual `unlock` is idempotent; `increment` adds every call; `updateProgress` uses max(old,value). Independent progress caps at target; statistic-bound definitions read statistics directly and reject manual achievement mutation. Decreasing statistics does not revoke existing unlocks. Complex business conditions may use child JsonPlayerData then call unlock. First unlock emits PlayerAchievementUnlockedEvent, possibly historical/offline; it is not a transactional reward guarantee.

## UI and verification

`/mgc stats [game]`, `/mgc achievements [game]`, and main-menu button use mgc.mgc permission. Chest menus paginate/filter and show personal progress, times, completion totals and ratio. **Denominator is every unique player who logged into this minigame server, including lobby-only, spectators and offline players.** Startup imports retained actual server player records; future joins mark loggedIn. Reads do not fabricate logins.

Native advancement UI (default L) uses one shared minigame tab with per-game branches/unlocks; numeric progress/ratios remain in chest menus. Core state is authoritative. Transient native definitions rebuild on restart/resource reload; metadata changes to existing native IDs need reload/restart. Child disable unregisters active definitions but retains data.

Build and existing checks only where repository prohibits new tests. Manually verify lifecycle notification points; filtering; custom deaths; once-only child settlement; direct updates and idempotent unlock; JsonPlayerData save/restart and empty data directory; native tree, pagination, filters and login-based denominator. Report Paper validation separately from compilation. No personal-homepage integration or voice groups.

Paper 26.2-62 lacks batch/nonpersistent advancement registration. Core registers parent-first with the single-key API, then removes generated persistent definitions while retaining runtime nodes. Do not require a Paper upgrade for native achievement display.

Menus reuse GameMeta displayName/icon/description for game selection; do not show raw IDs as names. Statistic entries use GameStatistic.icon(), with zero displayed as “暂无统计信息”. AchievementDefinition.icon() is used by both chest entries and native advancement displays. Completed entries have explicit enchantment glint; incomplete entries explicitly disable it. First live unlock uses colored chat with hover description and native show_toast=true; synthetic game grouping nodes have show_toast=false. Native titles/descriptions serialize parsed Adventure components, never literal MiniMessage markup.

Native display troubleshooting: `/mgcop achievements` reports each online player’s definition/root/registered/completed counts before and after re-projecting core state. It does not change core achievement records. Registration summaries and criterion grant outcomes are logged.

Native projections use the `unlock_right_away` impossible criterion, which Multiverse-Core explicitly exempts from `allow-advancement-grant: false`. This keeps core-authorized minigame grants working in lobbies without enabling vanilla advancement grants. Restart after upgrading from the old `unlocked` criterion to rebuild transient definitions.

The single native tab starts at `mgc_achievements:root`, titled “进入小游戏世界” with description “啪啪啪啪啪” and a firework rocket icon. Actual server login (including lobby) immediately records `AchievementPlayerData.minigamesEnteredAt` and projects the entry after 20 ticks, allowing its first toast after the initial client update. Historical offline imports do not unlock it. Old data defaults to zero and unlocks on the next actual visit. Game grouping keys and leaf IDs stay stable; groups unlock with the entry, indicate layout only, and do not claim gameplay participation. Only games with registered achievements get branches. The shared entry exists even without child definitions; it is separate from per-game completion counts/ratios. Restart when upgrading the old per-game tab layout.

World-independent native notifications: the core handles criterion grants at HIGHEST priority even when cancelled. Only the `mgc_achievements` namespace is managed: persisted unlocked projections are allowed, and unrecorded grants are rejected. This keeps native toasts available in worlds that disable advancement grants without changing vanilla or other plugins' advancements. After a world change, the core resynchronizes next tick; completed projections are not awarded again. Do not revoke/re-award achievements to replay toasts or change world settings for this purpose.
