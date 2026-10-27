# 游戏机制工具包（`game.kit`）

`game.kit` 提供**与具体游戏规则无关**的那一层：把「问玩家一句话」「回合计时」「背包里的手牌」「侧边栏」这些
每款游戏都要重写一遍的东西收拢成一套 API。规则本身（牌型、胜负、算分）永远留在你的插件里。

> 设计原则：**机制进 kit，规则留游戏**。判断标准很简单 ——
> 「换个游戏还需要同样的代码吗？」需要，才进 kit。

包路径：`com.github.mczjuops.mczjugamecore.game.kit`

---

## 0. 三个泛型点

kit 不认识「牌」是什么，也不认识你的座位状态类，所以有三处需要你接入：

| 泛型点 | 接口 | 你要提供什么 |
|---|---|---|
| 座位 | [`Seat`](#1-seat座位) | 一个实现 `Seat` 的类（通常是你的玩家状态类） |
| 物品 | `HandItemCodec<T>` | 物品 → `ItemStack` 的渲染与反解、聚合键、显示名 |
| 选项 | `ChoiceOption` | 直接构造即可，无需实现 |

---

## 1. `seat` 座位

```java
public interface Seat {
    String name();
    boolean isBot();
    boolean isOnline();
    @Nullable PlayerExt ext();     // 人机返回 null
    default @Nullable Player player();
    default boolean isActive();
}
```

**人机必须是合法座位。** kit 的所有询问、计时、手牌读写都对 `ext() == null` 做了判空，
所以你的游戏逻辑里不需要到处写「这是不是人机」——除非决策本身不同。

`SeatOrder<P>` 管座次：`next` / `previous` / `distance`（环形距离）/ `after`。
日麻的荣和优先级（头跳）就是 `distance` 最小的赢家优先。

---

## 2. `thread` 逻辑线程

```java
GameThread thread = new GameThread(plugin, "soulcard");
thread.start(() -> { /* 一局游戏的顺序逻辑 */ });
```

一局游戏的逻辑写成「顺序几段式」最省心，但 Bukkit 主线程不能阻塞，所以：

- 逻辑跑在**虚拟线程**上；
- 任何 Bukkit API 通过 `thread.call(...)` / `thread.run(...)` 编组回主线程；
- `thread.runLater(task, ticks)` 延迟执行；
- `GameAbortedException` 表示整局不该继续（玩家全退、插件停用、关服），在最外层捕获即可。

> `call` **不能在主线程调用**（会死锁），kit 会直接抛错。

---

## 3. `clock` 计时

两个计时器分工明确，不要混用：

| 类 | 用途 | 典型值 |
|---|---|---|
| `ActionClock` | **回合主时钟**：这个人这一手还有多少时间 | 基础 20 秒 + 每回合刷新 5 秒 |
| `PromptTimer` | **提示倒计时**：提醒某个被询问的人还有几秒回话 | 反应窗口 5 秒 |

`ActionClock` 的扣时顺序是 **先扣额外时间、再扣基础时间**，额外时间可用 `refreshExtra` 每回合重置，
基础时间一旦消耗不再恢复（日麻的「持ち時間 + 秒読み」就是这个模型）。

`PromptTimer` 挂在 `Pending` 上，作答 / 超时 / 取消时自动取消，不会在屏幕上留下残留倒计时；
对局结束时调用 `interaction.cancelPromptTimers()` 统一清理。

---

## 4. `prompt` 询问（最常用）

### 4.1 一行搞定一次询问

```java
String target = interaction.askChoiceSync(player, List.of(
        new ChoiceOption("荣和", "ron", "和这张牌", Material.LIME_CONCRETE, true, 10),
        new ChoiceOption("碰",   "pon", "用两张同牌", Material.ORANGE_CONCRETE, true, 20),
        new ChoiceOption("过",   "pass")),
    AskOptions.standard("要怎么做？", 5).build());
```

`askXxxSync` 会依次处理：**人机自动回答 → 弹窗等待 → 超时保底 → 玩家取消**。

### 4.2 `AskOptions` 决定保底行为

```java
AskOptions.standard("选择目标", 5)
        .onTimeout(AskOptions.TimeoutFallback.RANDOM)   // 超时随机挑一个
        .onCancel(() -> { throw new PlayCancelledException(false); })  // 玩家按 Esc 怎么办
        .groupMode(AskOptions.GroupMode.BY_KEY)         // 同类物品合并成一个按钮
        .showPromptTimer(false)                         // 不显示提示倒计时
        .build();
```

| `onTimeout` | 行为 |
|---|---|
| `RANDOM` | 随机取一个候选（默认） |
| `FIRST` | 取第一个候选（声明窗口取「过」时用） |
| `MIN` / `MAX` | 数字询问取下界 / 上界 |
| `REJECT` | 不给保底，返回 `null` |

**是 / 否询问超时一律按「否」** —— 随机选「是」等于替玩家做了不可撤销的决定。

### 4.3 人机：`BotAnswerer`

```java
interaction.setBotAnswerer(new BotAnswerer() {
    @Override
    public <T> T answer(AskRequest<T> request) {
        return switch (request.kind()) {
            case TARGET -> /* 从 request.candidates() 里挑 */ null;
            case ITEM   -> /* 从 request.pool() 里挑 */ null;
            case CHOICE -> /* 从 request.choices() 里挑 */ null;
            case CONFIRM -> null;
            case NUMBER -> null;
        };
    }
});
```

返回 `null` 表示「拿不定主意」，交给 `onTimeout` 的保底策略。人机的询问不会弹任何界面，
`askXxx` 会立刻返回一个已完成的 `Pending`。

### 4.4 并发询问：`MultiAsk`

多个玩家**同时**要作答时用它（日麻的「荣/碰/吃/杠/过」声明窗口）：

```java
Map<Seat, String> answers = MultiAsk.askAll(others,
        seat -> interaction.askChoice(seat, claimOptionsFor(seat), options),
        5);
```

契约（改动前先想清楚）：

- **同时**弹出所有问询，不是问完 A 再问 B —— 否则慢玩家拖死全场，且先答的人等于看到了别人的选择；
- **同一个截止时刻**，不是每人各自计时；
- 返回的 `Map` **只包含真的回答了的座位**，没作答的由你当成「过」；
- **优先级不在这里算**。荣 > 碰/杠 > 吃、头跳还是双响，都是你的规则。

---

## 5. `menu` 菜单

| 类 | 用途 |
|---|---|
| `KitMenu` | 基类：跳过权限检查、行数通过构造参数传、`filler()` 占位 |
| `ChoiceMenu` | 按钮式选项；按 `ChoiceOption.order` 升序；`enabled=false` 的仍然显示但点不动 |
| `ConfirmMenu` | 是 / 否 |
| `NumberMenu` | 数字选择 |
| `ItemPickMenu<T>` | 从一组物品里选一个；`BY_KEY` 时同类合并并标注张数 |
| `SeatPickMenu` | 选人（玩家头颅） |

`ChoiceOption.enabled = false` 的用途：日麻振听时「荣和」必须**看得见但点不了**，
玩家得知道自己为什么不能荣。直接不列入选项是不对的。

关闭菜单视为「取消」（`Pending#cancelNow`）；超时由 `Pending` 自己的计时器负责，
菜单本身不带第二套超时，避免「谁先超时」的歧义。

---

## 6. `hand` 背包即手牌

```java
HandItemCodec<Tile> codec = new TileCodec();
HandLayout layout = HandLayout.builder().autoSlots(14, 0).reserve(26).build();
Hand<Tile> hand = new Hand<>(codec, layout, Comparator.comparing(Tile::kind));   // 自动理牌
```

`Hand<T>` 负责：扫描、覆盖、塞入、取走、溢出、理牌、写保留槽与副手占位物。
人机的手牌存在内存里，真人的手牌就是背包里的物品 —— 同一个方法分流，调用方不用判断。

**所有方法都必须在主线程调用**（逻辑线程请用 `thread.call(...)`）。

### `HandItemCodec.key(T)` 的硬性要求

`key` 必须与「能不能凑成一组」一致：

- 日麻的**赤 5 万与普通 5 万是同一个 key** —— 它们可以一起碰、一起暗杠，赤只是个体属性；
- 做成不同的 key 会导致「手里 2 张普通 5 万 + 1 张赤 5 万凑不出碰」这种只在开了赤ドラ时才出现的错判。

### 布局差异

| | 手牌槽 | 保留槽 | 翻页 | 排序 |
|---|---|---|---|---|
| SoulCard | 0~35 去掉 17/26/35 | 上一页 / 出牌 / 下一页 | 需要 | 玩家自己拖 |
| 日麻 | 0~13 | 打出按钮 | 不需要 | 每次摸牌按牌种重排 |

副手（槽位 40）默认焊死：手牌扫描只覆盖主背包，牌放进副手会导致「手牌数少一张、界面里选不到」。

---

## 7. `hud` 侧边栏

`Sidebar` 每秒刷一次即可；`attach` / `detach` 负责在开局 / 结束时接管与归还玩家的计分板。

---

## 8. `team` 队伍

`team` 只解决「谁在哪一支队」和队伍相关的通用表现，**不替你分队**：没有自动平衡、没有选队菜单，
也不假设「红蓝两队」。谁进哪支队由游戏规则或管理员指令决定。

**与 `Party` 的区别：** `Party` 是跨局的玩家组队（大厅组队、`splitParty()` 会真的把它拆散），
`GameTeam` / `TeamSession` 是**局内阵营**，只在本局有效、结束后消失，也不影响 `Party`。
所以不能拿 `splitParty()` 当局内分队用，两者可以同时存在（同一 `Party` 的人被分到不同阵营）。

```java
public static final GameTeam RED  = new GameTeam("red",  "<red>红队",  NamedTextColor.RED,  Material.RED_GLAZED_TERRACOTTA);
public static final GameTeam BLUE = new GameTeam("blue", "<blue>蓝队", NamedTextColor.BLUE, Material.BLUE_GLAZED_TERRACOTTA);

// onGameStart()
TeamSession teams = new TeamSession(this, List.of(RED, BLUE));
teams.friendlyFire(true).tabPrefixes(true).voiceGroups(true);
teams.assign(player, RED);                    // 单个玩家手动分配
teams.assignAll(getPlayers(), BLUE);          // 批量分配
teams.syncVoiceGroups();                      // 成员变化后重建语音组
```

| 能力 | 默认 | 说明 |
|---|---|---|
| 同队免伤 | 开启 | 核心监听器取消同队之间的近战、弹射物、点燃的 TNT 与区域效果云伤害 |
| Tab 队伍前缀 | 开启 | 走核心 `TabManager`，离线 / 结束自动清理 |
| 队伍语音组 | 开启 | 每队一个 Simple Voice Chat 群组，组名为队伍显示名（多房间时带房间名） |

查询：`teamOf(player)` / `isTeammate(a, b)` / `membersOf(team)` / `onlineMembersOf(team)` / `hasTeam(uuid)`，
换队直接再 `assign` 一次即可。**清理由核心接管**：游戏结束/中止/取消时核心会自动 `reset()`
（撤销 Tab 前缀、移除语音组、注销会话），玩家离局时核心会清除其登记；
在自己的终局钩子里再调一次是安全的（幂等）。

**边界：** 队友关系只认本局登记，未登记的玩家（裁判、旁观者）保持原版伤害规则；
队伍显示名同时用作语音组名，同一房间不能出现同名队伍。

---

## 9. `spectator` 旁观

同一套流程支持两种常见规则，由游戏自己选：

| 规则 | 调用 | 典型游戏 |
|---|---|---|
| 永久旁观（淘汰） | `eliminate(player)` | 饥饿游戏 |
| 等复活 | `awaitRespawn(player, watchLocation)` | 核心战争 |

```java
private SpectatorSession spectators;

// onGameStart()
spectators = new SpectatorSession(this)
        .respawnSeconds(5)
        .reviveInvulnerableTicks(40)
        .defaultAnchor(room.getSpectatorSpawn())
        .onSpectate(t -> { /* 游戏自己的淘汰播报、侧边栏刷新 */ })
        .onRevive(t -> { /* 游戏自己的复活播报、发装备 */ });

// 死亡策略里（此时服务端还没发重生包）
@Override
public void onPlayerDeath(PlayerExt player, PlayerDeathEvent event) {
    spectators.awaitRespawn(player, null);   // 5 秒后回到观战锚点复活
}
```

- `eliminate(player, anchor, message)`：永久旁观，可指定观战锚点与淘汰文案；
- `awaitRespawn(player, seconds, watch, respawnLocation, onRevived)`：倒计时期间旁观，到点复活；
  `respawnLocation` 是 `Function<Player, Location>`，返回 null 时用观战位置；`onRevived` 在玩家已复活后执行；
- `revive(player)` 立即复活；`cancelAwaitRespawn(player)` 取消倒计时（人仍留在旁观）；
- 查询：`isSpectating` / `isWaitingRespawn` / `isEliminated` / `anchorFor` / `secondsLeft` / `spectators()` / `waitingRespawn()`。

### 展示与机制分开

标题、聊天、动作栏倒计时与音效都在 `notices()` 里配置，默认给一套通用展示：

```java
spectators.notices().sounds(false);                                  // 只要机制、不要音效
spectators.notices().chat(false).titles(false);                       // 完全交给游戏自己播报
spectators.notices(SpectatorNotices.silent());                        // 关闭全部内置展示
spectators.notices().eliminateText("<red>你出局了", "<red>出局", "<gray>观战中");
spectators.notices().respawnText("<yellow>%d 秒后复活", "<yellow>%d 秒");
```

含 `%d` 的文案会用剩余秒数填充；`eliminate(player, anchor, message)` 显式传入的文案会无视 `chat()` 开关强制发送。
需要完全自定义时关掉内置展示，改用 `onSpectate` / `onRevive` 回调渲染。

### 清理、离局与自动重连

- **结束/中止/取消**：核心的结束流程会自动调用 `reset()`，把仍在本局的旁观者从旁观模式里放出来
  （切到与大厅一致的冒险模式），不会把旁观模式带回大厅。子插件在自己的终局钩子里再调一次是幂等的。
- **离局**（`/quit`、被移出、加入失败回滚）：核心在离局流程中会撤销该玩家的登记、停掉倒计时与在途的
  延迟任务，并把玩家从旁观切到冒险模式。所以不要在退出策略里再手动切模式。
- **延迟任务失效**：所有模式切换排队时都会领一个代次标记，玩家离局、游戏结束或被新的切换操作取代后，
  旧任务自动作废，不会在玩家已经回到大厅或进入别的房间之后又把模式改回去。
- **自动重连不由本 kit 提供**：重连依赖核心的 `AutoReconnect` 接口与 `PlayerReconnectListener`。
  前提是游戏实例实现 `AutoReconnect`，并且退出策略在 `DISCONNECT` 时不把玩家移出本局；
  满足后核心会在档案加载完成时把玩家重新加入原局，守卫监听器随后调用 `reapply` 恢复旁观与剩余倒计时。
  仅创建一个 `SpectatorSession` 不会让掉线玩家自动回到游戏。

**不要自己 `player.setGameMode(...)`。** 死亡界面里只改模式不发重生包，会让服务端已是旁观、客户端还躺在死亡页面；
从旁观切回生存不解除旁观相机，画面会黏在上一个目标身上。kit 统一走 `PlayerModeSwitch`：
先让模式在服务端生效、下一 tick 发重生包、重生点钉在观战锚点、再补切一次。

守卫监听器由核心自动注册，负责：重生点修正、旁观者被强杀时拉出死亡界面、掉线时保留登记（重连后接着倒计时）、
重连后重新施加旁观。因此掉线重连的旁观者不需要游戏额外处理。

**边界：** kit 不管背包、掉落物、淘汰广播和胜负判定，这些仍写在你的死亡策略里。

---

## 10. 典型接线

```java
public final class MyPlugin extends JavaPlugin {
    @Override public void onEnable() {
        HandLayout layout = ...;
        Hand<Card> hand = new Hand<>(codec, layout);
        getServer().getPluginManager().registerEvents(
                new HandGuardListener(layout, hand::owns, MyGame::isPlaying), this);
    }
}

// 一局开始时
GameThread thread = new GameThread(this, "mygame");
PromptTimer prompts = new PromptTimer(this, thread);
MenuInteraction<Card> interaction = new MenuInteraction<>(this, thread, codec)
        .withPromptTimer(prompts);
interaction.setBotAnswerer(new MyBotAnswerer(game));
prompts.bindCurrentTurn(game::currentPlayer);
thread.start(() -> game.play());
```

---

## 11. 不该放进 kit 的东西

- 牌型 / 胜负 / 算分（SoulCard 的元老判定、日麻的番符点数）
- 「这里的取消意味着什么」（SoulCard 取消出牌要退牌，日麻取消只是「过」）
- Bot 的估值逻辑
- 怎么分队、什么时候换队、胜负是否按队伍算
- 淘汰后能不能复活、复活在哪、复活给什么
- 任何**只对一款游戏成立**的布局或流程
