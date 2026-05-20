# 小游戏插件开发进阶文档

本部分介绍 `MGC` 为你提供的便捷功能。它们和游戏生命周期本身没有关系，但是可以简化你的开发流程。

---

## 一、基于虚拟箱子的菜单

当你要添加一种新菜单时，通常需要做这三件事情：
- 创建一个 `Menu` 的子类
- 在插件启动时注册
- 每当需要为玩家开启时，新建这个子类的实例并调用 `open()`

> 如果菜单不复杂，还可以直接用 MGC 注册的 `/menu` 来打开，见下方说明

下面是一个简单的例子。你也可以阅读示例插件 `MGC-example-plugin` 的源码，看看其中的例子、在本地改一改。

首先创建 `Menu` 的子类，并添加构造方法。下面展示的是固定写法。

```java
// ExampleMenu
public ExampleMenu(Player player, Object... args) {
    super(player, args); // 这一步是固定的
    // 初始化其他内容
}
```

> 当菜单需要更多上下文信息时，可以修改构造器的参数，比如 `ExampleMenu(Player player, ItemStack item, Object... args)`，
> 额外传入数据 item，并初始化。

> ExampleMenu 的实例，在 new 它的时候创建，在玩家关闭物品栏时就会被回收。

然后你需要定义几种基本信息：

```java
@Override
protected String getTitle() {
    return "示例菜单";
}

@Override
protected @Range(from = 1L, to = 6L) int getRows() {
    return 3;
}

@Override
protected String getPermission() {
    return "mgc.mgc";
}
```
它们分别是菜单左上角显示的标题、菜单的行数（取值为 [1, 6]），打开菜单需要的权限节点（如果玩家没有这个权限，直接不会打开这个菜单）。

然后，你需要重写 `setup()` 方法来定义，当系统为玩家打开此菜单时，插件需要在菜单里填充哪些“物品”，以及这个物品在被点击时是否需要触发回调逻辑。

当你想在某个槽位（从 0 开始，到 size - 1，不允许在这个范围外）放一个物品，可以使用 `Menu` 中定义的 `setSlot`，它有两个版本：

- 版本一：`setSlot(int slot, ItemStack display)` 用于在这个槽位填充一个物品，无回调，也就是纯展示信息用的
- 版本二：`setSlot(int slot, ItemStack display, SlotAction action)` 在版本一的基础上，还能设置，当玩家点击后会触发什么逻辑

下面是一个简单的示例，会在菜单中的第 1 格放一个纯展示物品，在第 9 格放一个点了会播放声音的按钮

```java
// ExampleMenu
@Override
public void setup() {
    inventory.clear(); // 如果这个菜单支持打开期间刷新，需要先清空。这个操作不影响玩家自己的物品栏

    setSlot(
            0,
            ItemBuilder.of(Material.CLOCK)
                    .customName("<green>这是菜单的第 1 格！")
                    .lore(List.of(
                            "<gray>这个格子没有设置回调，因此点击不会发生任何事情！"
                    ))
                    .build()
    );

    setSlot(
            8,
            ItemBuilder.of(Material.CLOCK)
                    .customName("<green>这是菜单的第 9 格！")
                    .lore(List.of(
                            "<yellow><b>点击左键</b> 播放升级音效",
                            "<yellow><b>点击右键</b> 播放僵尸叫声",
                            "<yellow><b>按下丢弃键</b> 播放玻璃破碎声"
                    ))
                    .glint(true)
                    .build(),
            (player, event) -> {
                Player p = player.player();
                switch (event.getClick()) {
                    case LEFT -> p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    case RIGHT -> p.playSound(p, Sound.ENTITY_ZOMBIE_HURT, 1.0f, 1.0f);
                    case DROP -> p.playSound(p, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
                }
            }
    );
}
```

为一个玩家打开这个菜单的例子（最简例子，有需要时可以传入除 `player` 外的其他参数）：

> 构造器中有参数 `Object... args`，实际上无任何 `Object` 也是可以的。

```java
private void openMenuFor(Player player) {
    new ExampleMenu(player).open();
}
```
MGC 还提供了 `/menu` 这个命令，在部分情况下由服务器（例如命令方块）直接打开菜单。如果你不想自己写一个打开菜单的逻辑，你可以使用它。

为此，你需要向 `MGC` 注册这个菜单，让 `/menu` 能够识别到这个菜单。
注册方式如下，由主类的 `onEnable()` 调用 `registerMenu()`，其中第一个参数是这个菜单的唯一 ID（命令的参数之一）。

```java
@Override
public void onEnable() {
    MenuFacade.registerMenu("example_menu", ExampleMenu.class);
    // 其他启用时逻辑
}
```

`/menu` 的用法为：

```
/menu <menuId> <player> [args]
```

这个命令默认为只有管理员能使用，比如可以设计成玩家右键一个 NPC 时由控制台执行，进而为玩家打开菜单。
后面的 `args` 是一个可以被解析的字符串。可以在 `Menu` 子类的构造器中解析它。

另外，MGC 还内置了一个好用的确认菜单，可以加一个确认操作，需要玩家额外点击一次“确认”，
使用示例（比如执行某个删除操作时，需要玩家确认一下）：

```java
private void confirmDelete(Player player) {
    var gui = new AlertMenu(player.player(), () -> {
        player.sender().info("<gold>你点击了确认，这下真的删除了！");
        // 执行删除逻辑
    });
    gui.open();
}
```
---

## 二、排行榜

你可以注册排行榜，并为特定排行榜创建文本展示实体，展示玩家排名。

排行榜系统与游戏完全独立，即使不注册游戏，也可以注册排行榜。你可以按需注册多个排行榜。

排行榜系统的设计与小游戏系统高度对称，定义、注册模式和展示实体的管理方式，均与游戏或房间的管理模式类似。

需要进行一次排名时，你需要定义一个 `AbstractLeaderboard` 的子类，例如

```java
public class ExampleGameWinsLeaderboard extends AbstractLeaderboard {}
```

必须重写的方法包括排行榜标题、副标题（均支持 `MiniMessage` 格式），以及最关键的 `fetchEntries`。

其中，`fetchEntries` 需要你提供一个 `LeaderboardEntry` 的列表。`LeaderboardEntry` 是对玩家数据的一个简单包装，
字段包括玩家名（字符串）、原始分数（`double`）、格式化分数（字符串）。

而且，你**不需要**自行进行排名，`MGC` 会使用你提供的原始分数自动进行排名。

> 原始分数可能是得分、用时等，而格式化分数是最终展示时显示的字符串。
> 例如，你可以将毫秒数格式化为 `<aqua>xx:xx` 的形式，或是将关卡数格式化为 `<yellow>第x关` 的形式。

此外，还可以可选地重写：
- 升序或降序（默认降序，如果是最短用时这样的排名，则需要设置为升序）
- 最多显示前几名（默认 12）
- 是否自动刷新（默认否），若设置为是，每 10 分钟 `MGC` 将强制刷新该排行榜
- 每行的渲染样式，默认是 `<yellow>1. <green>Steve <gray>- <yellow>14:10` 这样的形式
- 没有任何记录时显示的内容

最终，排行榜在文本展示实体上渲染为这样的形式（每行均居中）：

```
你设置的标题
你设置的副标题
1. xxx - xx（可设置样式）
2. xxx - xx
3. xxx - xx
...（可设置最多排到第几名，默认 12）
```

然后，类似于游戏，你也需要在主类的 `onEnable` 中注册这个排行榜，且需要为排行榜设定一个 ID。
这个 ID 不能与任何其他插件的排行榜重复。

这个 ID 会在命令或代码中手动刷新时用到。

完成注册后，仍然没有可以看到的排行榜，因为你还需要为这个排行榜添加文本展示实体。（这也是当前展示排行榜的唯一形式）

每个排行榜都可以添加不止 1 个展示实体。比如说，你可以在你的小游戏场景内，和小游戏大厅中放置两个相同的排行榜，它们会同时刷新。

管理展示实体的模式和管理房间的模式非常类似，使用如下命令：

```
/mgcop leaderboard list|create|edit|delete <leaderboardId> [entityId]
```

在游戏中通过 `create` 子命令创建展示实体后（需要指定一个展示实体的 ID，例如 `default`、`lobby` 等），通过 `edit` 子命令打开菜单编辑。

你可以设置实体的位置、渲染模式、是否有半透明背景。设置完成后，点击左下角的按钮就可以生成或刷新展示实体。

> 每次刷新时，如果找不到展示实体（会主动加载对应区块），就会视为实体被误杀，会重新生成。

当你需要在代码中主动刷新一个排行榜时（例如一局游戏结束时），可以通过访问 `MGC` 的排行榜管理器刷新：

```java
private void onGameEnd() {
    // 当游戏结束时（或其他合适时机），刷新注册的 ID 为 example_leaderboard 的排行榜
    MCZJUGameCore.getLeaderboardManager().refresh("example_leaderboard"); // 所有展示实体均会刷新
}
```

> 如果你的游戏找不到合适的时机手动刷新，就在这个排行榜子类中，设置为自动刷新。

---

## 三、`PlayerExt` 类

为了方便小游戏的开发，player 对象必须拥有 `getParty`，`isInGame` 等方法，但 java 中没有扩展函数的写法。

所以，新增了一个 `PlayerExt` 类，只需要 `new PlayerExt(player)` 就可以把 Bukkit 的 `player` 包装成功能更丰富的 `playerExt`。

`PlayerExt` 本身不包含任何成员变量，只是负责调用各管理器或工具，所以只要 new 时传入的 player 相同，他们就没任何区别。

> 比如说，`playerExt.getParty()` 其实就是立即去 `partyManager` 持有的队伍信息中去找这个玩家在哪个队伍中。

本框架大部分地方的 `Player` 都包装成了 `PlayerExt`，一般直接用就可以。

> 如果你觉得可以加更多方法，也可以提出来！

---

## 四、工具类

详细说明见对应工具类的文档（源码中的 javadoc），这里简单介绍：

- `LocationSelector`：可以调用它来选取坐标。用 `PlayerExt` 中的 `selectLocation` 方法调用。
- `TextParser`：用于将 `MiniMessage` 格式的字符串解析成 `Component`，其能力详见[官方文档](https://docs.papermc.io/adventure/minimessage/format/)。
- `Sender`：它和它的实现类用于给各种对象发消息：包含队伍、游戏内所有玩家、日志等。例如 `AbstractGame` 基类中有一个专门 sender。
- `ItemBuilder`：便捷构造一个 `ItemStack`，用于生成给玩家的道具或是菜单中的图标都很方便，详见对应文档。
- `DialogBuilder`：交互非常友好，可以作为虚拟箱子菜单的辅助，其能力详见 [wiki](https://zh.minecraft.wiki/w/%E5%AF%B9%E8%AF%9D%E6%A1%86%E5%AE%9A%E4%B9%89%E6%A0%BC%E5%BC%8F)。
  Paper 原生 API 非常复杂，这个工具封装了部分功能，详见对应文档。
- `CommandUtils`：当前的工具通常用于 `Brigadier` 命令系统的自动补全。
- `TimeFormat`：时间格式化工具，用于将毫秒数格式化为字符串。
- `CountDown`：倒计时工具，便捷地创建一个倒计时，并设定每秒、结束时、取消时的回调，详见对应文档。
