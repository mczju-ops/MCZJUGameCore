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

## 8. 典型接线

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
        .withPromptTimer(prompts)
        .withNotifier((seat, msg) -> /* 发提示 */);
interaction.setBotAnswerer(new MyBotAnswerer(game));
prompts.bindCurrentTurn(game::currentPlayer);
thread.start(() -> game.play());
```

---

## 9. 不该放进 kit 的东西

- 牌型 / 胜负 / 算分（SoulCard 的元老判定、日麻的番符点数）
- 「这里的取消意味着什么」（SoulCard 取消出牌要退牌，日麻取消只是「过」）
- Bot 的估值逻辑
- 任何**只对一款游戏成立**的布局或流程
