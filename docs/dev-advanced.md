# 小游戏插件开发进阶文档

本部分介绍 `MCZJUGameCore` 为你提供的便捷功能。它们和游戏生命周期本身没有关系，但是可以简化你的开发流程，拓展小游戏插件的功能。

> 技巧：通过源码及源码中的注释（文档）有助于了解相关功能和用法。
> 
> 你不需要将 `MGC` 的仓库克隆到本地，只需要直接打开对应的 `.class` 文件（可以通过 `Ctrl + 点击` 跳转到类来打开所在文件），
> 点击“下载源码”，就可以阅读源码、查看源码中的文档和注释。
> 
> 对 `MGC` 和 `Paper` 均适用。

---

## 一、基于虚拟箱子的菜单

### 1. 什么是菜单

插件服中经常出现这类菜单，看上去像为玩家打开了一个箱子，其中的物品会显示信息，点击时可能触发一些事件。

`MGC` 为你封装了一个用起来非常简便的菜单功能。

> 请理解：菜单是“虚拟箱子”，不存在实际的容器来存储物品数据。玩家关闭菜单后，所有箱子中的“物品”就消失了。

> 虚拟箱子能作为菜单，是因为点击事件（`InventoryClickEvent`，例如玩家左键拿取一个物品）被默认取消了，所以玩家无法存取物品。
> 
> 虽然点击事件被取消了，这次点击仍然是可以识别的，所以就能触发回调。

### 2. 在 `MGC` 的子插件中创建菜单

当你要实现一种菜单功能时，你只需要：

- 创建一个 `Menu` 的子类。
- 每当需要为玩家开启时，新建这个子类的实例，并调用 `open()`。

在子类中，必须做这几件事情：

- 添加构造器，调用 `Menu` 的构造器。
- 重写菜单基本信息，包括标题（显示在左上角）、行数（不同于箱子，行数可以是 1 ~ 6 范围内的任意值）、打开所需的权限节点。
- 重写 `setup()` 方法。这个方法为菜单中的指定槽位设置物品和回调。

下面是一个例子：

```java
public class ExampleMenu extends Menu {
    
    public ExampleMenu(Player player, Object... args) {
        super(player, args); // 调用 `Menu` 的构造器，不可省略
        // 初始化其他内容，比如设置你想注入的字段
    }
    
    // 显示在左上角的标题
    @Override
    protected String getTitle() {
        return "示例菜单";
    }
    
    // 菜单的行数
    @Override
    protected @Range(from = 1L, to = 6L) int getRows() {
        return 3;
    }
    
    // 打开菜单需要的权限节点。如果玩家没有该权限，则不会打开菜单
    @Override
    protected String getPermission() {
        return "mgc.mgc";
    }

    @Override
    public void setup() {
        inventory.clear(); // 如果这个菜单支持打开期间刷新，一般需要先清空。这个操作不影响玩家自己的物品栏

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
}
```

注意 `setup()` 中调用的 `setSlot()` 方法，它有两个版本，可按需选用：

- 版本一：`setSlot(int slot, ItemStack display)` 用于在这个槽位填充一个物品，无回调，也就是纯展示信息用的
- 版本二：`setSlot(int slot, ItemStack display, SlotAction action)` 在版本一的基础上，还能设置，当玩家点击后会触发什么逻辑

补充说明：

- `Menu` 的成员变量包含一个 `PlayerExt` 实例，也就是正在查看这个菜单的玩家。
- `Menu` 的成员变量 `inventory` 代表虚拟箱子这个区域的物品栏，不包括玩家自己的物品栏。
- `Menu` 还带有方法 `refresh()`（刷新菜单，默认行为是再调用一次 `setup()` 并让客户端为玩家刷新物品栏）和 `handleClose()`（玩家关闭菜单时做什么，自动调用）。
  有需要时可以重写。

> `Menu` 的实例，在 new 它的时候创建，在玩家关闭物品栏时就会被回收。

### 3. 为玩家打开你创建的菜单

为一个玩家打开这个菜单的例子（最简例子，有需要时可以传入除 `player` 外的其他参数）：

```java
private void openMenuFor(Player player) {
    new ExampleMenu(player).open();
}
```

> 构造器中有参数 `Object... args`，实际上无任何 `Object` 也是可以的。

MGC 还提供了 `/menu` 这个命令，在部分情况下由服务器（例如命令方块）直接打开菜单。
如果你不想自己写一个打开菜单的逻辑，你可以使用它。（这也是构造器中添加 `args` 的意图）

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

### 4. `MGC` 已实现的菜单

`MGC` 内置了一个好用的确认菜单，可以加一个确认操作，需要玩家额外点击一次“确认”。
比如执行某个删除操作时，需要玩家确认一下。

使用示例：

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

需要进行一次排名时，你需要创建一个 `AbstractLeaderboard` 的子类。
必须重写的方法包括排行榜标题、副标题（均支持 `MiniMessage` 格式），以及最关键的 `fetchEntries`。

其中，`fetchEntries` 需要你提供一个 `LeaderboardEntry` 的列表。`LeaderboardEntry` 是对玩家数据的一个简单包装，
字段包括玩家名（字符串）、原始分数（`double`）、格式化分数（字符串）。

而且，你**不需要**自行进行排名，`MGC` 会使用你提供的原始分数自动进行排名。

> 原始分数可能是得分、用时等，而格式化分数是最终展示时显示的字符串。
> 例如，你可以将毫秒数格式化为 `<aqua>xx:xx` 的形式，或是将关卡数格式化为 `<yellow>第x关` 的形式。

示例如下：

```java
public class ExampleGameWinsLeaderboard extends AbstractLeaderboard {
    
    @Override
    public String getTitle() {
        return "   <gold>示例小游戏<yellow>排行榜   ";
    }

    @Override
    public String getSubtitle() {
        return "<gray>最高分数榜";
    }

    @Override
    public List<LeaderboardEntry> fetchEntries() {
        List<LeaderboardEntry> entries = new ArrayList<>(); // 这是最终要返回的数据源
        
        // 示例：从小游戏插件自己管理的数据中，拿到所有玩家的玩家名、分数，以及分数格式化后的字符串
        var cache = storageManager.getCache();
        cache.forEach((uuid, playerData) -> {
            int highest = storageManager.getHighestScore(uuid);
            if (highest > 0) {
                entries.add(new LeaderboardEntry(playerData.getName(), highest, "<yellow>%d分".formatted(highest)));
            }
        });
        return entries;
    }
}
```

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
// 示例：游戏结束时主动让 MGC 刷新排行榜
@Override
private void onGameEnd() {
    // 刷新注册的 ID 为 example_leaderboard 的排行榜
    MCZJUGameCore.getLeaderboardManager().refresh("example_leaderboard"); // 所有展示实体均会刷新
}
```

> 如果你的游戏完全找不到合适的时机手动刷新，就在这个排行榜子类中，设置为自动刷新。这样 `MGC` 会每 10 分钟刷新一次。
> 这也意味着，这个排行榜的信息是稍微滞后的，你可以在副标题说明。

---

## 三、`PlayerExt` 类

为了方便小游戏的开发，你可能希望通过玩家获取他正在游玩的游戏、他的队伍等信息。为此，你需要向 `MGC` 的游戏管理器、队伍管理器等管理器查询。

为了简化这个过程，`MGC` 添加了一个 `PlayerExt` 类，只需要 `new PlayerExt(player)` 就可以把 Bukkit 的 `Player` 实例包装成功能更丰富的 `playerExt`。

`PlayerExt` 本身不包含任何成员变量，只是负责调用各管理器或工具，所以只要创建实例时传入的 `Player` 实例相同，他们就没任何区别。

> 比如说，`playerExt.getParty()` 其实就是立即去 `partyManager` 持有的队伍信息中去找这个玩家在哪个队伍中。

本框架大部分地方的 `Player` 都包装成了 `PlayerExt`，一般直接用就可以。

除了上面说的查询所在游戏、所在队伍，还有许多方法，下面是一些常用的：

- `giveItem(ItemStack item)`：给予玩家一个物品，如果物品栏已满就扔在他的脚下。
- `getDisplayName()`：获取玩家显示名（`MiniMessage` 格式），即按 `EssentialsX` 风格添加颜色的玩家名（管理员为深红色，其他玩家为绿色）。
- `resetState()`：恢复玩家的生命值、饥饿值、饱和度、着火或冻结状态为默认，清除所有状态效果。
- `getData()`：获取存储的该玩家的数据（详见 `PlayerData` 部分）。

更多方法详见源码。

> 如果你觉得可以加更多方法，也可以提出来！

---

## 四、队伍系统

`MGC` 内置了一个队伍（`Party`）系统，类似 `hypixel` 中的队伍。

玩家之间可以互相组队，并由队长带领所有人加入某个游戏。队伍支持队内发送信息。

如果你开发的游戏是多人游戏，尤其是需要多人合作完成任务或多个多人队伍之间竞争的队伍，
你可以直接使用队伍系统来辅助游戏设计。

调用 `PartyManager` 中的 `splitParty()` 方法可以将一个队伍分成多个队伍。

---

## 五、工具类

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
