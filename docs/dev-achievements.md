# 游戏统计与成就（MGC 1.5.0）

MGC 自动记录参赛、游玩时间、击杀和死亡。子插件负责报告胜负结果、注册成就，以及判断特殊成就的达成条件。数据由核心保存，所有接口和事件都在服务器主线程使用。

## 玩家如何查看

| 入口 | 内容 |
| --- | --- |
| `/mgc` → “我的统计与成就” | 选择游戏，查看统计和成就 |
| `/mgc stats [游戏ID]` | 查看自己的累计统计 |
| `/mgc achievements [游戏ID]` | 查看成就进度、首次解锁时间和全服达成比例 |
| 原版进度界面（默认 L 键） | 在“进入小游戏世界”标签页中查看各游戏的成就树 |

查询指令沿用 `mgc.mgc` 权限。不填游戏 ID 时先选择游戏；菜单支持分页、筛选和刷新。全服达成仅显示百分比（两位小数），颜色随达成率降低而变得稀有：大于 25% 为灰色，≤25% 为绿色，≤10% 为蓝色，≤5% 为紫色，≤1% 为金色；解锁时间显示到分钟。首次在线解锁成就时会收到聊天提示和右上角弹窗，重复解锁不会再次提示。

同一玩家的同一成就在整个小游戏服只解锁一次，按玩家 UUID、游戏 ID 和成就 ID 保存，不按世界、地图或房间拆分；切换世界或重新登录不会重复计数，也不会更新首次解锁时间。统一入口成就按首次真实登录记录，不要求玩家位于 lobby。

全服达成比例 = 解锁人数 ÷ 登录过本小游戏服务器的总人数，包含离线玩家和只在大厅或旁观的玩家。核心会导入服务器保留的历史登录记录；已经删除且核心从未记录的玩家无法补回。

## 读取和修改统计

通过 `MCZJUGameCore.getGameStatisticsManager()` 获取统计管理器。

| `GameStatistic` | 计数规则 |
| --- | --- |
| `PLAY_TIME_SECONDS` | 玩家在线、游戏运行中且非旁观模式时，每 20 个有效 tick 计一秒 |
| `KILLS` / `DEATHS` | 根据游戏死亡通知统计，可通过事件过滤 |
| `GAMES_PLAYED` | 正式开局或成功中途加入时计一次，同一游戏实例内重连不重复计数 |
| `WINS` / `LOSSES` / `DRAWS` | 子插件每报告一次结果，累计一次 |

```java
var stats = MCZJUGameCore.getGameStatisticsManager();
long kills = stats.get(playerId, "myplugin_duel", GameStatistic.KILLS);
stats.add(playerId, "myplugin_duel", GameStatistic.KILLS, 3);
stats.set(playerId, "myplugin_duel", GameStatistic.KILLS, 120);
```

游戏必须已经注册。查询无记录时返回 0；修改支持有登录记录的离线玩家。`add` 可以扣减，结果不能为负或溢出；`set` 只能设置非负值。修改统计会检查相关成就，降低统计不会撤销已解锁的成就。

游玩时间按 tick 累计，服务器卡顿时可能少于实际经过时间。离线、离局、换局或切换为旁观时，不足一秒的部分不保留。开放式游戏的参赛次数按游戏实例计算，内部小回合不会额外增加参赛次数。

## 报告胜负

核心不会推断玩家输赢。子插件应在每次结算时，为相关玩家各发送一次结果事件：

```java
Bukkit.getPluginManager().callEvent(new GameWinEvent(this, winnerId));
Bukkit.getPluginManager().callEvent(new GameResultEvent(
        this, loserId, GameResultEvent.Outcome.LOSS));
// 平局为各玩家分别报告 Outcome.DRAW。
```

这里的 `this` 是游戏实例，也可以改传游戏 ID，例如 `new GameWinEvent("myplugin_duel", winnerId)`。

结果事件可取消；每个未取消的事件都会累计一次，请避免重复发送。可以为离线或已离局的玩家结算。报告结果不会增加参赛次数，开放式游戏可以在每个小回合结束时分别报告。

## 注册成就

先注册游戏，再在子插件的 `onEnable()` 中注册成就：

```java
var kills100 = new AchievementKey("myplugin_duel", "kills_100");
var kills200 = new AchievementKey("myplugin_duel", "kills_200");
var flawless = new AchievementKey("myplugin_duel", "flawless");
var rescue100 = new AchievementKey("myplugin_duel", "rescue_100");
MCZJUGameCore.getAchievementManager().register(this, List.of(
        AchievementDefinition.statistic(kills100, "百人斩", "累计 100 次有效击杀",
                Material.IRON_SWORD, GameStatistic.KILLS, 100),
        AchievementDefinition.statistic(kills200, "更进一步", "累计 200 次有效击杀",
                Material.DIAMOND_SWORD, GameStatistic.KILLS, 200).withParent(kills100),
        AchievementDefinition.challenge(flawless, "<gradient:gold:yellow>毫发无伤</gradient>", "<gray>未受伤并赢得一局", Material.SHIELD),
        AchievementDefinition.counter(rescue100, "救援专家", "累计救援 100 次", Material.TOTEM_OF_UNDYING, 100)
));
```

三种定义分别适用于：

- `statistic`：达到某项通用统计的阈值后自动解锁，也会为历史达标玩家补发。
- `challenge`：由子插件判断条件，再调用 `unlock` 解锁。
- `counter`：由子插件累加进度，达到目标后自动解锁。

名称和说明支持 MiniMessage，图标用于菜单和原版弹窗。成就 ID 使用小写字母、数字、下划线或短横线，目标值必须大于 0。

`withParent` 用于排列原版成就树，不要求先解锁父成就。父节点必须属于同一个游戏和插件，可以提前注册，也可以放在同一批中。重复 ID、缺失父节点和循环关系会导致注册失败。

相关类型位于 `achievement` 包，统计枚举位于 `statistics` 包，事件位于 `event` 包。

## 更新和查询成就

```java
var achievements = MCZJUGameCore.getAchievementManager();
if (won && damageTaken == 0) achievements.unlock(playerId, flawless);
achievements.increment(playerId, rescue100, 1);
// 或同步已有的单调累计值：
achievements.updateProgress(playerId, rescue100, totalRescues);
```

`unlock` 重复调用不会重复解锁，也不会改变首次解锁时间。`increment` 累加进度；`updateProgress` 保留新旧值中较高的进度，达到目标后封顶。绑定通用统计的成就应通过统计管理器更新，不能使用这三个方法。

如果条件涉及收集物品种类等复杂状态，可用子插件自己的 `JsonPlayerData` 保存，达成后调用 `unlock`。以后改变成就条件或目标时，建议使用新 ID。

| 接口 | 返回内容 |
| --- | --- |
| `getDefinitions(gameId)` | 游戏已注册的成就定义 |
| `getProgress(playerId, key)` | 玩家进度与首次解锁时间 |
| `getStats(key)` | 全服解锁人数与达成比例 |

更新接口返回 `AchievementUpdateResult`，可判断进度是否变化、是否首次解锁。首次解锁事件适合做提示；发放奖励时，子插件仍需自行处理重复发放问题。

## 调整击杀和死亡统计

可取消击杀事件来排除队友击杀，或取消采集事件来排除练习阶段：

```java
@EventHandler
public void onKill(GameKillEvent event) {
    if (!(event.getGame() instanceof MyGame game)) return;
    UUID killer = event.getKillerId(); // 环境死亡时可为 null
    if (killer == null || game.isSameTeam(killer, event.getVictimId())) {
        event.setCancelled(true);
    }
}

@EventHandler
public void onCollect(GameStatisticCollectEvent event) {
    if (event.getGame() instanceof MyGame game && game.isPracticePhase()) {
        event.setCancelled(true);
    }
}
```

`GameKillEvent.setKillerId(UUID)` 可修正推落虚空等击杀归属。击杀者必须是同一游戏实例的有效参赛者，且不能是死者本人；没有有效击杀者时只计死亡。`GameDeathEvent` 可以独立取消死亡计数。取消统计事件不会改变实际死亡行为。

核心会在执行死亡策略前通知统计模块，因此由死亡策略实现的淘汰、复活仍可计数。如果原版死亡在核心接手前就被取消，或自定义淘汰根本不触发原版死亡，请在移除玩家或切换旁观前主动报告：

```java
MCZJUGameCore.getGameStatisticsManager().reportDeath(this, victimId, killerId);
```

同一游戏、同一玩家在同一个 tick 内的自动和手动报告只统计一次。如果手动报告后还会在后续 tick 触发原版死亡，应在 `GameStatisticCollectEvent` 中排除来源为 `AUTOMATIC` 的击杀、死亡采集，避免重复计数。

## 可监听的事件

| 事件 | 触发时机与用途 |
| --- | --- |
| `GameStartEvent` | 游戏成功开始，包含当时的成员快照 |
| `PlayerGameJoinEvent` | 玩家成功中途加入运行中的游戏 |
| `PlayerGameDeathEvent` | 执行游戏死亡策略之前 |
| `GameStatisticCollectEvent` | 自动或主动死亡统计之前，可取消以排除练习、特殊观众等情况 |
| `GameKillEvent` / `GameDeathEvent` | 击杀、死亡计数之前，可分别取消 |
| `GameResultEvent` / `GameWinEvent` | 子插件报告胜负或平局，可取消 |
| `GameStatisticChangedEvent` | 统计值变化后 |
| `PlayerAchievementUnlockedEvent` | 首次解锁后，也可能来自离线玩家的历史补发 |

等待入队不算参赛，只有正式开局或成功中途加入才会计数。

## 保存与维护

统计和成就保存在 `plugins/MCZJUGameCore/player_data/mgc_achievements/<uuid>.json`，复用玩家数据的自动保存、退出保存和停服保存。子插件无需另外写文件；异常停服可能丢失尚未保存的更新。

原版进度界面展示核心保存的解锁结果。子插件停用后会隐藏其成就，数据仍保留。更改原版成就标题或父子关系后，需重载数据或重启服务器；箱子菜单会立即读取新定义。

管理员或控制台可执行 `/mgcop achievements`，查看在线玩家的原版进度状态并重新同步展示，此操作不会修改核心成就记录。MGC 自定义成就以核心解锁记录为准，在所有世界恢复合法投影的授予与右上角弹窗，包括 Multiverse-Core 设置 `allow-advancement-grant: false` 的世界；普通原版成就仍遵循世界设置。切换世界后下一 tick 补同步全服共享进度，已完成的投影不会重复授予；无需返回大厅。
