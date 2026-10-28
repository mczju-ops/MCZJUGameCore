# 游戏统计与成就（MGC 1.5.0）

核心复用 `JsonPlayerData` 保存通用统计及成就。子插件声明成就，复杂业务条件由子插件判断；生命周期只发布事件，统计和成就分别监听，不在游戏基类中引入统计字段或游戏 UUID。所有 API 和事件均在服务器主线程使用。

## 玩家查询

- `/mgc` → “我的统计与成就”。
- `/mgc stats [游戏ID]`：自己的累计统计。
- `/mgc achievements [游戏ID]`：全部、已达成、未达成成就；显示进度、首次解锁时间、完成数量及全服达成比例。
- 不指定游戏时打开分页游戏列表；复用主游戏菜单的 `GameMeta` 名称、图标和介绍。箱子菜单支持分页、筛选和刷新，沿用 `mgc.mgc` 权限。
- 统计项各自声明图标，数值为 0 时显示“暂无统计信息”。成就使用子插件声明的图标，已达成条目显示附魔光效，未达成条目不显示光效。
- 首次在线解锁发送带悬停条件说明的彩色聊天提示，并由原版进度系统弹出右上角提示；游戏分组节点不弹窗。重复解锁不重新授予原生条件。
- Minecraft 原生进度界面（默认 L 键）只显示一个小游戏 TAB：入口“进入小游戏世界”（描述“啪啪啪啪啪”）后，按有成就定义的小游戏分支展开。实时数值和全服完成比例在箱子菜单查询。

**比例分母为登录过本小游戏服务器的所有去重玩家 UUID，包含离线玩家、仅在大厅或旁观的玩家。** 启动时导入服务器保留的真实历史玩家记录，以后由登录事件记录；查询不会增加分母。服务器已删除且核心从未记录的历史玩家无法追溯。个人主页暂不接入。

## 事件边界

| 事件 | 发布位置及语义 | 消费者 |
| --- | --- | --- |
| `GameStartEvent` | GameManager 成功进入 RUNNING 后；附当时成员快照，不可取消 | 统计或其他子插件 |
| `PlayerGameJoinEvent` | 运行中的游戏确认接受中途加入后；失败加入不发布 | 统计或其他子插件 |
| `PlayerGameDeathEvent` | 核心执行游戏死亡策略之前的通知；不改变死亡行为 | 统计或其他子插件 |
| `GameStatisticCollectEvent` | 一次自动/主动死亡采集计数前，可取消 | 子插件排除练习阶段、自定义观众等 |
| `GameKillEvent` / `GameDeathEvent` | 击杀归因及死亡计数前，各自可取消 | 子插件过滤有效击杀/死亡 |
| `GameResultEvent` / `GameWinEvent` | 子插件自行报告胜负平局，可取消 | 统计模块 |
| `GameStatisticChangedEvent` | 统计值变化后的只读通知 | 成就模块检查绑定条件 |
| `PlayerAchievementUnlockedEvent` | 首次解锁后通知，可能为离线或历史补发 | 子插件展示等扩展 |

生命周期不直接调用统计管理器，统计模块也不直接调用成就检查。等待玩家在正式开局时通过 `GameStartEvent` 通知，不把等待入队等同于参赛。

## 通用统计及修改

通过 `MCZJUGameCore.getGameStatisticsManager()` 访问：

| `GameStatistic` | 规则 |
| --- | --- |
| `PLAY_TIME_SECONDS` | RUNNING、在线且非 Bukkit 旁观模式时，每 20 个有效 tick 计一秒；不是卡顿时的墙钟时长 |
| `KILLS` / `DEATHS` | 监听游戏死亡通知，经过过滤事件后计数 |
| `GAMES_PLAYED` | 成功开局/中途加入，每个游戏实例每人一次，重连不重复；只用运行时实例记录，不保存游戏 UUID |
| `WINS` / `LOSSES` / `DRAWS` | 子插件每次发布未取消的结果事件累计一次 |

开放会话的参赛次数按游戏实例计，不推断内部子回合数。离线、离开、换局、旁观时丢弃不足一秒的计时余量。

```java
var stats = MCZJUGameCore.getGameStatisticsManager();
long kills = stats.get(playerId, "myplugin_duel", GameStatistic.KILLS);
stats.add(playerId, "myplugin_duel", GameStatistic.KILLS, 3);
stats.set(playerId, "myplugin_duel", GameStatistic.KILLS, 120);
```

游戏须已注册；修改允许有真实登录记录的离线玩家。查询无记录返回 0 且不创建记录。`add` 支持扣减，但结果不能为负或溢出；`set` 接受非负值。直接修改统计发布变更通知，触发关联成就检查，不伪造击杀/死亡采集事件。降低统计不会撤销已解锁成就。

## 过滤击杀及其他采集

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

`GameKillEvent.setKillerId(UUID)` 可修正推落虚空等归因，最后击杀者必须是同实例的有效参赛者且不能是受害者本人。环境死亡也提供归因事件，没有有效归因不计击杀。`GameDeathEvent` 独立控制死亡计数，取消其中一个不影响另一个或实际游戏死亡。

原版死亡通知在执行死亡策略前发布，因此死亡策略取消原版死亡并实施淘汰/复活仍可统计；核心接手前已取消的原版死亡不会通知。自定义观众和练习阶段通过 `GameStatisticCollectEvent` 排除，不需要修改游戏基类。

自定义致命伤害拦截可能不产生原版死亡事件，此时在移除玩家或切旁观之前主动报告：

```java
MCZJUGameCore.getGameStatisticsManager().reportDeath(this, victimId, killerId);
```

自动和手动报告共用同实例/受害者/tick 去重，不要跨 tick 重试同一次死亡。如果自定义逻辑同时会引发稍后的原版死亡，在 `GameStatisticCollectEvent` 中取消来源为 `AUTOMATIC` 的 `KILLS`/`DEATHS`，保留 `REPORTED_DEATH` 来源即可。`GameStatisticChangedEvent.Source` 定义这些来源。

## 胜负和平局：一次结算发一次事件

```java
Bukkit.getPluginManager().callEvent(new GameWinEvent(this, winnerId));
Bukkit.getPluginManager().callEvent(new GameResultEvent(
        this, loserId, GameResultEvent.Outcome.LOSS));
// 平局为各玩家分别报告 Outcome.DRAW。
```

`this` 是游戏实例；也可直接传游戏 ID：

```java
Bukkit.getPluginManager().callEvent(new GameWinEvent("myplugin_duel", winnerId));
```

核心在 MONITOR 监听未取消事件。**每次事件累计一次，子插件负责在自己的结算流程中只发一次。** 不使用游戏 UUID、结算 ID 或持久化去重流水。开放会话每个子回合结束时各报一次即可。支持离线或已经离局的玩家，不会推断其他玩家输赢，也不增加参赛次数。`GameWinEvent` 为 `GameResultEvent` 的便捷子类，共用处理器列表。

## 注册成就及父子节点

在子插件 `onEnable()` 注册游戏之后执行：

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

类型位于 `achievement` 包，统计枚举位于 `statistics` 包，事件位于 `event` 包。名称和说明支持 MiniMessage（颜色、渐变、加粗等），统一用于菜单、聊天提示和原生进度；`icon` 同时用于箱子菜单和原生弹窗。成就 ID 使用 `[a-z0-9_-]+`。目标须为正数；重复 ID、缺失父节点或循环会在注册前拒绝。父节点须属于同游戏同插件，且已经注册或在同一批中；批次顺序不限。

父节点只控制原生树展示，不强制前置解锁。100/200 击杀由同一统计的阈值自然递进，复杂先决条件交给子插件。统计本身不会自动生成成就。绑定统计的成就直接读取统计进度；注册时也为历史达标玩家补发（包含离线，不刷历史聊天提示）。

## 手动复杂成就

```java
var achievements = MCZJUGameCore.getAchievementManager();
if (won && damageTaken == 0) achievements.unlock(playerId, flawless);
achievements.increment(playerId, rescue100, 1);
// 或同步已有的单调累计值：
achievements.updateProgress(playerId, rescue100, totalRescues);
```

`unlock` 幂等；重复达成不重复提示、计人数或修改首次解锁时间。`increment` 每次累加；`updateProgress` 取 max(old,value)，适合不会回退的累计数据。独立成就进度达到目标后解锁并封顶；统计绑定成就拒绝这三个修改 API，应修改对应统计。复杂集合条件仍用子插件自己的 `JsonPlayerData` 保存，满足后调用 `unlock`。

查询使用 `getProgress(UUID,key)`、`getStats(key)`、`getDefinitions(gameId)`，返回只读数据。`AchievementUpdateResult` 表示是否变化和首次解锁。首次通知不等同于保证恰好一次执行的奖励事务。修改成就条件含义/目标时优先使用新 ID。

## 持久化与原生展示

`AchievementPlayerData extends JsonPlayerData` 注册在核心保留命名空间 `mgc_achievements`，路径为 `plugins/MCZJUGameCore/player_data/mgc_achievements/<uuid>.json`。每人一份，包含真实登录标记及各游戏统计/成就。它不覆盖子插件原有的数据类。

修改只调用 `setModified(true)`；加载、现有自动保存、玩家退出保存和停服保存均复用 `PlayerDataManager`。没有独立 `achievements.json`、写入线程、快照队列或结算流水。可靠性及保存间隔沿用现有玩家数据机制，崩溃可能丢失未保存更新。此版本替代未发布的独立存储草案，不自动导入草案文件。

持久化嵌套 DTO 使用公开非 final 字段；现有序列化器会排除 private/final 字段，因此对外返回的 record 不直接用于落盘。`AchievementStorage` 仅为访问这些玩家数据的薄封装，不实现文件读写。

原生树兼容 Paper 26.2-62：按父节点顺序调用单个注册接口，再移除该 API 生成的持久定义文件（运行时节点保留），每次启动/资源重载重建。核心记录是唯一解锁依据，原生 grant 不能绕过核心。子插件停用后注销有效定义，保留数据并撤销展示进度；原生注册表中的旧定义到资源重载/重启才消失。同进程更改原生标题或父节点后需重载数据/重启刷新原生定义，箱子菜单即时读取新定义。

## 验证

运行 Maven package 和已有检查，不新增测试文件。Paper 手动验证：

1. 成功开局/中途加入发布通知，失败加入/取消等待不增加参赛；重连同实例不重复。
2. 有效击杀、同队过滤、死亡独立取消、自动/手动淘汰及练习阶段采集过滤。
3. 每个未取消胜负事件计一次，离线结算正常；子插件自身只结算一次。
4. 统计修改触发绑定成就；手动重复解锁幂等，降低统计不撤销解锁。
5. JSON 保存后重启恢复统计、进度、时间及登录分母；空数据目录仍可正常启动。
6. 原生父子树、箱子菜单分页筛选、历史补发、资源重载及全服历史登录比例。

编译和已有测试通过不等于这些服务器行为已经实测。

### 原生展示诊断

管理员或控制台可执行 `/mgcop achievements`，查看在线玩家的定义数、根节点注册数和完成数，并重新同步原生投影；不会修改核心成就记录。注册完成以及新授予条件的结果会写入服务器日志，授予失败时检查对应的 criteria、awarded 和 done。

原生投影使用 `unlock_right_away` 条件（`minecraft:impossible` 触发器），兼容 Multiverse-Core 的 `allow-advancement-grant: false` 世界设置：该条件是 Multiverse 显式允许的程序授予例外。核心仍按已解锁记录校验，不能用原生命令绕过。旧版本使用 `unlocked` 条件，更新后需正常重启重建原生定义；无需开启世界的原版进度授予。

### 统一小游戏 TAB

玩家登录小游戏服务器（含大厅）后约一秒解锁入口“进入小游戏世界”，描述为“啪啪啪啪啪”，图标为烟花火箭；此延迟让客户端先接收初始进度包，再显示首次入口弹窗。首次时间保存在 `AchievementPlayerData.minigamesEnteredAt`，旧数据缺省为 0，下次实际登录解锁；离线历史玩家导入不会提前解锁。重复登录保留首次时间且不重复授予已完成的条件。核心启用时已在线玩家同样补记入口。

统一根节点 key 为 `mgc_achievements:root`，即使子插件尚无成就定义也会注册。各游戏原来的根节点 key 保留为根节点下面的分组，不再单独生成 TAB；分组随入口点亮、不弹窗，也不代表参与过对应游戏。具体成就仍保留原 key、父子关系与核心解锁记录，箱子菜单的游戏完成数量/比例仍仅统计子插件注册的定义。切换旧树结构后需正常重启重建原生定义。

手动验证：首次进服约一秒出现入口弹窗；L 仅新增一个 TAB，入口后接小游戏分组和原有成就；重复登录和重启后入口时间不变，具体游戏成就记录不变；没有子插件成就时仍能看到入口；Multiverse 禁用原版授予的大厅仍能解锁。
