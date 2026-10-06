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
- `Menu` 还带有方法 `clearMenu()`（清除所有菜单内的物品，并清除所有回调）、`refresh()`（刷新菜单，默认行为是调用 `clearMenu()` 后调用一次 `setup()` 并让客户端为玩家刷新物品栏）和 `handleClose()`（玩家关闭菜单时做什么，自动调用）。
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
    new AlertMenu(player.player(), "确认删除？", () -> {
        player.sender().info("<gold>你点击了确认，这下真的删除了！");
        // 执行删除逻辑
    }).open();
}
```

管理员执行 `/mgcop lobby`，可以在分页箱子 GUI 中配置主大厅、`config.yml` 中所有游戏类别的大厅及所有已注册小游戏的等待大厅位置。菜单按主大厅、配置顺序的类别、按 ID 排序的游戏排列，每页 45 项。类别物品使用配置中的 `icon` 图标（默认 `CHEST`）、MiniMessage 名称和描述。左键选择位置并保存，右键移除位置，成功后均有提示。类别数为 0 或 1 时也可设置已有类别的大厅。

玩家执行 `/lobby` 传送到主大厅，`/lobby <game_id>` 传送到小游戏大厅，`/lobby <category_id>`（例如 `/lobby casual`）传送到类别大厅。Tab 补全同时提供游戏和类别 ID。游戏与类别同名时优先游戏，使用 `/lobby category:<category_id>` 可明确指定类别（该前缀保留用于类别）。`/l` 是别名，权限仍为 `mgc.lobby`；游戏中禁止传送，未配置位置或世界未加载时提示失败，传送成功后提示成功。

主大厅与游戏大厅继续保存在 `lobbies.json`，类别大厅独立保存在 `category-lobbies.json`，同名类别与游戏互不覆盖。重启后恢复位置。删除类别并 `/mgcop reload` 后，该类别不再显示于新打开的菜单、补全或传送目标中；保留其位置记录，同 ID 恢复配置后可继续使用。已打开菜单不会自动刷新，位置选择完成前会重新校验权限和类别是否存在。

子插件可通过 `MCZJUGameCore.getLobbyManager()` 调用 `getCategoryLobby(categoryId)`（返回位置副本或 null）、`setCategoryLobby(categoryId, location)`（要求当前存在的类别和已加载世界，返回保存是否成功）及 `removeCategoryLobby(categoryId)`（返回移除并保存是否成功）。保存失败时保留原类别大厅设置。类别 ID 区分大小写，与配置一致；设置和移除应在服务器线程调用。类别大厅不会自动替代某个游戏的大厅。

大厅持久化不依赖启动时世界是否已加载：文件保留世界名称和坐标，每次获取位置时再查找世界。世界未加载时 `getLobby`、`getMainLobby`、`getGameLobby` 和 `getCategoryLobby` 返回 `null`，但记录仍保留；世界加载后无需重启即可获取位置。`hasLobby` 和 `getConfiguredLobbyIds` 反映已配置记录，不代表世界当前可用。

`lobbies.json` 或 `category-lobbies.json` 读取失败（包括空文件、损坏 JSON 或无效位置）时，整个对应文件禁止写入，设置、移除和停服保存都不能覆盖原文件；修复文件后重启插件才能恢复写入。另一份正常文件仍可独立保存。`setLobby` 保持 `void` API，保存失败抛出 `IllegalStateException`；移除大厅或设置类别大厅失败返回 `false`，内存中保留原设置，菜单提示失败。保存先完成同目录临时文件写入，再优先原子替换；文件系统不支持原子移动时退回普通替换。旧 `config.yml` 的 `lobby-spawn` 仅在 `lobbies.json` 不存在时迁移，已有文件（包括 `{}`）不会触发迁移。

设置和移除大厅已经即时保存，停服不再写回大厅配置；公开的 `save()` 对未变化的数据也不重复写入。因此，在服务器运行时手动修改文件后重启，不会被旧内存快照覆盖。实际设置或移除前会核对磁盘内容是否仍与上次读取或保存时一致；如果被外部修改、创建或删除，则拒绝写入并提示重启加载新配置。启动日志显示大厅配置的绝对路径和成功加载的记录数量；不存在的文件不会自动生成空配置。

补充手动验收：服务器启动后手动写入或修改 `lobbies.json`，直接重启，确认文件保留且加载记录数正确；运行中修改文件后在菜单设置另一个大厅，确认操作失败、文件原文不变；未配置大厅且文件不存在时重启，确认没有生成 `{}`；仅打开菜单或传送后重启，确认大厅文件内容和修改时间不变。

重启持久化手动验收：设置主大厅、游戏和类别大厅，替换 JAR 后重启并核对全部记录；将大厅世界延迟加载或暂时卸载，保存其他大厅并停服，确认原世界名称和坐标保留，重新加载世界后可传送；分别准备空文件、损坏 JSON、无效位置，确认启动日志报错、设置失败且停服后文件原文不变，另一份正常文件仍能保存；已有 `{}` 且旧 `lobby-spawn` 存在时确认不迁移，删除 `lobbies.json` 后确认正常迁移。未启动兼容服务器时不视为完成这些验收。

手动验证需在兼容 Paper 服务器进行：设置两个类别大厅并分别使用 `/lobby <category_id>` 传送；验证与同 ID 游戏大厅互不覆盖及 `category:` 指定方式；检查游戏中禁止传送、权限、未配置提示；验证分页、删除类别后 reload 的目标校验和重启后位置恢复。

---

## 二、`PlayerExt` 类

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

## 三、玩家数据 `PlayerData`

如果你需要持久化数据（比如玩家的游玩次数、游玩进度、最高分数等），你可以使用 `MGC` 内置的玩家数据持久化工具， 使用方法类似于游戏房间。

首先创建一个 `JsonPlayerData` 的子类，作为针对一个玩家的数据存储容器:

```java
public class ExamplePlayerData extends JsonPlayerData {
   public Integer wins = 0; // 可以设置默认值
   private Material m; // 设置为private，就不会被MGC保存
   public List<String> strList; // 可以用复杂类型，但ItemStack等暂时无法保存，如果有需求，可以提issue
}
```

> 如果有数据需要临时挂在玩家这里，但是不希望被 `MGC` 持久化记录，可以将其作用域设为 `private`。

类似于游戏，你需要在主类的 `onEnable()` 中注册这个数据类。第一个参数可以直接填游戏 ID（其实任意能起到 ID 作用的字符串均可，不能和其他小游戏的重复）：

```java
@Override
public void onEnable() {
    MCZJUGameCore.getPlayerDataManager().registerPlayerData("example", ExamplePlayerData.class);
    // 其他初始化逻辑
}
```

在任何地方，都可以用 `PlayerExt` 实例来获取已注册的玩家数据。下面是一个例子，一场游戏结束时，需要获取数据并更新：

```java
private void onEnd() {
    ExamplePlayerData data = player.getData(ExamplePlayerData.class);
    data.wins += 1;
    data.setModified(true); // 将其标记为已修改
}
```

> 重要：如果修改了数据，别忘了通过 `data.setModified(true);` 将其设为已修改，否则不会保存到文件。

---

## 四、排行榜

你可以注册排行榜，并为特定排行榜创建文本展示实体，展示玩家排名。

排行榜系统与游戏完全独立，即使不注册游戏，也可以注册排行榜。你可以按需注册多个排行榜（例如挑战次数榜、分数榜等）。

排行榜系统的设计思路与小游戏和房间系统比较类似，定义和注册模式都差不多。

需要进行一次排名时，你需要创建一个 `AbstractLeaderboard` 的子类。

如果排行榜的数据来源是 `PlayerData`，那么你可以直接继承 `PlayerDataLeaderboard`。示例如下：

```java
public class ExampleGameWinsLeaderboard extends PlayerDataLeaderboard {
    
    @Override
    public String getTitle() {
        return "   <gold>示例小游戏<yellow>排行榜   ";
    }

    @Override
    public String getSubtitle() {
        return "<gray>胜利次数榜";
    }

    @Override
    public @NotNull Class<? extends AbstractPlayerData> getPlayerDataClass() {
        return ExamplePlayerData.class; // 数据源为 ExamplePlayerData
    }

    @Override
    public @NotNull String getFieldName() {
        return "wins"; // 数据源为字段 wins
    }
    
    // 可选重写：每行的文本形式
    @Override
    public String renderLine(int rank, String playerName, double value) {
        return "<yellow>%d.</yellow> <green>%s</green> <gray>-</gray> <yellow>%.0f场</yellow>"
                .formatted(rank, playerName, value);
    }

    // 可选重写：排序方向（默认降序，只有需要改为升序时需要重写）
    @Override
    public SortOrder getSortOrder() {
        return SortOrder.DESCENDING;
    }

    // 可选重写：排序方向（默认降序，只有需要改为升序时需要重写）
    @Override
    public int getDisplayCount() {
        return 12;
    }
}
```

补充说明：

- 如果不重写方法 `renderLine()`，默认会直接把分数放在排行榜上。分数数值为 `double`，会显示最近的整数。
- 有时候，`renderLine()` 肯定需要重写，比如如果排序的是“用时”，数据源是毫秒数，你需要将值格式化为 "mm:ss" 的形式。
- 一般来说，排行榜是降序的，但是有时候也可能需要升序排列，比如排序的是“最短用时”的情况。

如果你不是用 `PlayerData` 存储的玩家数据，你需要直接继承 `AbstractLeaderboard`。

此时，最关键的需要重写的方法是 `fetchEntries()`。
非常好理解，你只需要提供一个 `LeaderboardEntry` 的列表。`LeaderboardEntry` 是对玩家数据的一个简单包装，
字段包括玩家名（字符串）、原始分数（`double`）。

而且，你**不需要**自行进行排名，`MGC` 会使用你提供的原始分数自动进行排名。其他同上。

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
            int wins = storageManager.getWins(uuid);
            if (wins > 0) {
                entries.add(new LeaderboardEntry(playerData.getName(), wins));
            }
        });
        return entries;
    }
}
```

类似于游戏，你也需要在主类的 `onEnable` 中注册这个排行榜，且需要为排行榜设定一个 ID。
这个 ID 不能与任何其他插件的排行榜重复。示例：

```java
@Override
public void onEnable() {
    MCZJUGameCore.getLeaderboardManager().registerLeaderboard("example_wins", ExampleGameWinsLeaderboard.class);
    // 其他初始化逻辑
}
```

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
private void onGameEnd() {
    MCZJUGameCore.getLeaderboardManager().refresh(ExampleGameWinsLeaderboard.class); // 所有展示实体均会刷新
}
```

如果你的游戏完全找不到合适的时机手动刷新，你可以为排行榜类添加一个注解 `@AutoRefresh`。这样 `MGC` 会每 10 分钟刷新一次。

> 这意味着，这个排行榜的信息是稍微滞后的。如有需要，你可以在副标题等位置向玩家说明。

---

## 五、队伍系统

`MGC` 内置了一个队伍（`Party`）系统，类似 `hypixel` 中的队伍。

玩家之间可以互相组队，并由队长带领所有人加入某个游戏。队伍支持队内发送信息。

任意队员可执行 `/party voice`（也支持 `/p voice`），将当前队伍的队长与成员加入同一个
Simple Voice Chat 群组，沿用 `mgc.party` 权限。命令使用 `VoiceGroupUtil` 默认的开放（`OPEN`）配置，
名称为 `Party-` 加队长玩家名（可能被语音插件截短）。每次执行都会创建新组并切换当前可用成员。
离线或未连接语音的成员会跳过，语音插件不可用、没有有效玩家或创建失败时会提示执行者。
新入队或刚连接语音的成员需再次执行命令；这是执行时的成员快照，离队或解散不会自动退出语音群组，
可在语音客户端手动退出，非持久群组在空组时自动删除。

手动验证：用队长和普通队员分别执行命令，确认两者均能带全队进入同一个群组；检查 `/party help`
与 `/p voice`，以及控制台、未组队、语音服务缺失、无有效语音连接和混合连接状态下的提示。

仅队长可执行 `/party warp`（也支持 `/p warp`），将全体队员传送到队长执行命令时的位置，沿用 `mgc.party` 权限。
执行前检查全部队伍玩家（包含队长）：每个人必须未加入游戏或所处游戏为 `WAITING`；
任何人处于 `STATING`、`RUNNING` 或 `END` 都会拒绝整次操作，不开始传送。
队长保持原位，队员只改变位置，不自动退出、加入游戏或切换档案；离线或被事件取消的传送会向队长提示失败，其他队员仍尝试传送。

手动验证（需兼容 Paper 服务器，以下为验证步骤）：检查 `/party help` 和 `/p warp`，确认控制台、未组队玩家和普通队员无法使用；
全员未入游戏、全员等待、未入游戏与等待混合时，确认队员全部传送到队长处（包括跨世界）；
分别让队长或队员处于启动中、运行中、结束状态，确认全队无人被传送；取消一名队员的传送事件，确认不会误报全队成功。

如果你开发的游戏是多人游戏，尤其是需要多人合作完成任务或多个多人队伍之间竞争的队伍，
你可以直接使用队伍系统来辅助游戏设计。

调用 `PartyManager` 中的 `splitParty()` 方法可以将一个队伍分成多个队伍。

---

## 六、工具类

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

## 七、游戏道具(物品)

`MGC`提供了一个规范的物品类`MGCItem`，和一套物品注册、发放方法。如果你的游戏中，有很多游戏道具，推荐使用`MGCItem`。

先声明一个物品类，重写createRawItem和getId方法：

```java
public class ExampleItem extends MGCItem {
    @Override
    protected ItemStack createRawItem() {
        return ItemBuilder.of(Material.FEATHER).
                customName("加速羽毛").
                lore(List.of("点击获得5秒加速")).
                build();
    }

    @Override
    public String getId() {
        return "example:speed_feather";
    }
    
    // 如果点击这个物品有效果，可以加一个使用方法，但需要自己注册Listener来实现这个效果
    public void use(PlayerExt player){
      player.player().addPotionEffect(
              new PotionEffect(PotionEffectType.SPEED, 5 * 20, 8, false));
    }
}
```

再注册物品: 
```java
MCZJUGameCore.getItemManager().register(new ExampleItem());
```

然后你可以调用`PlayerExt.giveItem`来给玩家这个物品
```java
//player.giveItem("example:speed_feather");     // 也可以通过ID给
player.giveItem(new ExampleItem().getItem());   // 注意要用getItem，不是createRawItem
```

注册Listener来实现道具的效果：
```java
    @EventHandler
    public void onUseFeather(PlayerInteractEvent event){
        PlayerExt player = new PlayerExt(event.getPlayer());
        ItemStack itemInMainHand = player.player().getInventory().getItemInMainHand();
        if (new ExampleItem().isThis(itemInMainHand)){
            // 如果是你的物品
            itemInMainHand.setAmount(itemInMainHand.getAmount() - 1);
            new ExampleItem().use(player);
        }
    }
```

如果你的物品比较多，更推荐的用法是声明一个`AbstractExampleItem`抽象类，声明`use`抽象方法，然后用下面的方式使用物品
```java
MGCItem item = MCZJUGameCore.getItemManager().get(itemInMainHand);
if (item instanceof AbstractExampleItem){
  ((AbstractExampleItem) item).use(player);
}
```

> 还有更多的给玩家物品的方法，详见`PlayerExt`
> 还有更多的比较物品是否是`ExampleItem`的方法，详见`MGCItem`和`ItemManager`

---

## 八、旁观模式传送事件

玩家使用原版旁观模式快捷栏菜单传送时，Paper 提供的 `PlayerTeleportEvent` 不会直接给出目标玩家。
`MGC` 会根据传送目的地，在 2 格范围内寻找最近的非旁观模式玩家，并广播
`PlayerSpectatorTeleportEvent`。

`getEstimatedTarget()` 返回估算出的目标玩家。附近没有符合条件的玩家时会返回 `null`，
因此监听器需要处理无法估算目标的情况。取消该事件会同时取消原版旁观传送。

`MGC` 默认采用以下限制：

- 未处于任何游戏的玩家可以传送到任意可用目标。
- 处于游戏中的玩家只能传送到同一局游戏中的玩家。
- 目标属于其他游戏、未处于游戏，或无法估算目标时，都会阻止传送并向动作栏发送提示。

这里比较的是具体的游戏实例，因此同一种小游戏的不同房间也会相互隔离。具体插件仍然可以监听
`PlayerSpectatorTeleportEvent`，在默认限制之外添加自己的旁观传送规则。

目标玩家是根据位置估算的结果，并非原版提供的严格目标信息。如果需要判断估算结果的接近程度，
可以通过 `getEstimatedTargetDistanceSquared()` 获取目标玩家与原生传送目的地之间的距离平方。

## 九、Simple Voice Chat 语音群组

MGC 对 [Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat) 声明 `softdepend: [voicechat]`，
API 依赖为 `provided`、`optional`，不会打包进 MGC JAR。未安装语音插件时 MGC 仍可正常启用，
调用者不需要自行依赖语音 API。集成使用官方的
[BukkitVoicechatService 注册方式](https://modrepo.de/minecraft/voicechat/api/getting_started)。

所有创建、移除操作都在服务器主线程调用。项目没有专门的 `PlayerExtList` 类，接口接受
`Collection<PlayerExt>`，例如已有的 `List<PlayerExt>`：

```java
import com.github.mczjuops.mczjugamecore.utils.VoiceGroupUtil;

// playerExtList 是你的 List<PlayerExt>。
VoiceGroupUtil.createGroup("红队", playerExtList);

// 如果需要在本局结束时清理，请保存返回的 UUID（不要保存 Player 对象）。
var groupId = VoiceGroupUtil.createGroup("红队", playerExtList,
        VoiceGroupUtil.Options.defaults()
                .withPassword("team-password")
                .withHidden(true)
                .withPersistent(true)
                .withType(VoiceGroupUtil.GroupType.ISOLATED));

// 在游戏结束、取消、异常中止等路径中调用，并在清理后清空所保存的 UUID。
groupId.ifPresent(VoiceGroupUtil::removeGroup);
```

默认配置：无密码、客户端列表可见、非持久、`OPEN` 模式，随机生成 UUID。
每次调用都会创建新群组，同名群组不会复用。自定义配置为不可变对象，`with...` 返回新配置。
各模式与 [官方群组 API](https://voicechat.modrepo.de/de/maxhenkel/voicechat/api/Group.Type.html) 一致：

| 模式 | 行为 |
| --- | --- |
| `NORMAL` | 组员能听到组内语音，也能听到附近未入组玩家 |
| `OPEN` | 组员能听到附近玩家，附近玩家也能听到组员 |
| `ISOLATED` | 组员仅能听到组内其他玩家 |

`persistent` 仅表示最后一名成员离开后保留空群组，不表示跨服务器重启保存。
`hidden` 仅隐藏列表显示，不是访问控制；密码限制手动加入，本工具会直接将指定玩家加入。

`createGroup` 返回 `Optional<UUID>`。语音插件缺失、服务尚未启动、没有在线且已连接语音的玩家时
返回 `Optional.empty()`，不创建空组。可用性也可通过 `VoiceGroupUtil.isAvailable()` 查询。
离线或未连接语音的玩家会被跳过，重复玩家只加入一次；工具不会安排重连后自动加入。
空白名称抛出 `IllegalArgumentException`，集合和成员为 null 时抛出 `NullPointerException`，
异步操作、语音插件不接受名称，或第三方事件阻止创建/加入时抛出 `IllegalStateException`。语音插件可能处理名称中的空白和特殊字符。

加入会让玩家离开旧语音群组，移除新群组时不会恢复旧群组。非持久组最后一人离开后自动删除。
`removeGroup(UUID)` 只操作本工具创建的群组，让当前成员（包括后来手动加入者）退出，再删除群组；
成功或该组已经自动消失时返回 true，不可用、非本工具群组或删除失败时返回 false。
MGC 停用时会尝试清理所有本工具创建的群组。子插件仍应在自己的游戏结束、取消、异常中止和停用时清理，
尤其是 `persistent` 群组。若第三方插件在加入过程中抛出异常，工具会尝试删除半创建群组后重新抛出异常，
已离开的旧群组不会恢复。

手动验证（需兼容 Paper 服务器及已配置的 Simple Voice Chat）：

1. 不安装 voicechat 启动 MGC，确认正常启用，创建返回 empty。
2. 安装 voicechat，等待语音服务器就绪，用两个已连接语音的玩家创建默认组，确认进入同一组；
   加入离线、未连接语音或重复玩家，确认跳过和去重；没有有效玩家时确认不创建群组。
3. 验证密码、隐藏、空组保留以及三种语音模式；同名连续创建应返回不同 UUID。
4. 游戏结束时移除群组，确认成员退出、群组消失；非持久组已自动删除时清理仍应成功。
5. 重复退出、取消、异常中止及服务器停用，确认持久空组被清理，其他插件创建的群组不受影响。
