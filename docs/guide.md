# 基于`MCZJU-Game-Core`的小游戏开发指南

下面简称本插件为`MGC`，一些说明以起床战争为示例。

## 快速开始

### 1. 定义一个游戏类


```java
public class ExampleGame extends SinglePlayerGame{}
```

然后alt+enter快速继承所有的方法，完成游戏类的开发，后面的步骤介绍游戏类中各方法的作用

#### 1.1 定义游戏名 

这个游戏名可以是中文。它是游戏的唯一标识符，也是展示给玩家的游戏名。

```java
// ExampleGame
@Override
public String getName() {
    return "example";
}
```

#### 1.2 完成游戏初始化

当第一个玩家加入等待时，游戏对象将被创建，触发init方法。此时游戏可能需要做一些准备，比如生成游戏地图、创建游戏npc。

```java
// ExampleGame
@Override
protected boolean onGameInit() {
    // 初始化房间，啥都不做
    return true;
}
```

#### 1.3 写游戏核心逻辑

当游戏到达开始条件时，执行游戏开始逻辑。比如，为玩家分队、tp玩家到出生点、为玩家发装备。

```java
// exampleGame
@Override
protected void onGameStart() {
    getPlayers().forEach(it -> it.sender().success("游戏开始"));
    AbstractGame game = this;
    Bukkit.getScheduler().runTaskLater(MGCExamplePlugin.getInstance(), new Runnable() {
        @Override
        public void run() {
            // 过5秒结束游戏
            ExampleGameRoom gameRoom = (ExampleGameRoom) getGameRoom();
            getPlayers().forEach(it -> it.sender().info(STR."str value: \{gameRoom.valueStr}"));
            getPlayers().forEach(it -> it.sender().info(STR."int value: \{gameRoom.valueInt}"));
            MCZJUGameCore.getGameManager().endGame(game);
        }
    }, 100);
}
```

#### 1.4 处理游戏过程中的事件

这里和原生的事件处理一样，但你得检查玩家是否正在你的游戏中

```java
// SomeListener
@EventHandler
public void onPlayerInteract(PlayerInteractEvent event) {
    // 原生的事件处理方法，当然，你需要检查玩家是否在你的游戏中
    PlayerExt player = new PlayerExt(event.getPlayer()); // 创建PlayerExt类对玩家进行包装
    if (player.isInGame(ExampleGame.class)) {
        // 如果玩家正在你的游戏中
        ExampleGame game = (ExampleGame) player.getGame();
        if (game.getState() != GameState.RUNNING) return;   // 确保这个游戏正在进行
        
        // ok，可以处理你的游戏逻辑了，别忘了向Bukkit注册你的Listener
    }
}
```

#### 1.5 游戏结束处理

cancel代表游戏在等待阶段，由于人数不足，并且最后一个人退出，导致取消
abort代表游戏运行中由于意外事件导致游戏终止（玩家退出、用指令强制结束）
end代表游戏正常结束

```java
// ExampleGame
@Override
protected void onGameCancel() {
    getPlayers().forEach(it -> it.sender().success("游戏cancel"));

}

@Override
protected void onGameAbort() {
    logger.info("游戏意外终止");
    getPlayers().forEach(it -> it.sender().success("游戏开abort"));

}

@Override
protected void onGameEnd() {
    getPlayers().forEach(it -> it.sender().success("游戏end"));

}
```

`cancel`和`abort`由`MGC`自动调用，`endgame`由具体插件调用。

当你的游戏逻辑判断游戏应该结束了，就在那里调用下面的代码：

`MCZJUGameCore.getGameManager().endGame(game);`

`game`为游戏实例，你可以通过玩家对象获取`playerExt.getGame()`。如果你觉得应该有其它需要的获取方式，也可以在issue里提。

#### 1.6定义一个游戏房间数据类

游戏房间用于存放游戏房间相关数据。比如这个起床战争地图4个队伍的出生点坐标、资源刷新点等。这些数据对于不同地图不一致，所以可以创建一个类，动态设置这些数据。

```java
public class ExampleGameRoom extends JsonGameRoom {
    public String valueStr;
    public Integer valueInt;

    public Location location;
}
```
> 注意，数据类属性应全部采用包装类(如Integer、Boolean，而非int、boolean)
> 目前只支持基本类型和Location

然后，你就可以进mc，用交互式的方法，创建房间实例和编辑房间了(当然在你完成代码后才能编辑):

进入游戏后，输入指令`/menu room example room1`

- example为游戏名，和1.1中一样，可以用中文，但别用空格
- room1代表房间名，也是最终保存的json文件名字，也可以用中文，但别用空格

然后，就会自动打开地图编辑菜单，点击对应物品就能编辑对应的值了！

> 注意，请把想存储的值，设置为public。
> 目前只测试过string和int，还有Location可以，其它的类型还没试过。只支持基本类型，暂不支持复杂的嵌套关系。
> 如果只在游戏中创1个游戏房间，那么当一局游戏开始后，用`mgc join example`将会提示无空闲房间

### 2. 注册游戏到`MGC`

在主程序中注册就可以了。

```java
// ExamplePlugin
public void onEnable() {
    MCZJUGameCore.getGameManager().registerGame(ExampleGame.class, ExampleGameRoom.class);
}
```

然后，打包插件，到游戏中开始游戏试试！

输入下面的指令，`example`替换为你的游戏名！

`/mgc join example`

> 别忘了前面提到的创建游戏房间步骤，否则会提示无空闲房间

---

## 进阶文档

### 基于虚拟箱子的菜单

当你要添加一种新菜单时，通常需要做这三件事情：
- 创建一个 `Menu` 的子类
- 在插件启动时注册
- 每当需要为玩家开启时，新建这个子类的实例并调用 `open()`

> 如果菜单不复杂，还可以直接用 MGC 注册的 `/menu` 来打开，见下方说明

下面是一个简单的例子。你也可以阅读示例插件 `MGC-example-plugin` 的源码，看看其中的例子、在本地改一改。

首先创建 `Menu` 的子类，并添加构造方法。下面展示的是固定写法，构造方法除了固定的 `Player`，
有需要时还可以传入更多（字段注入）。

```java
// ExampleMenu
public class ExampleMenu extends Menu {
    
    public ExampleMenu(Player player) {
        super(ExampleMenu.class, player);
    }
}
```

然后，通过重写 `setup()` 来决定，
当系统为玩家打开此菜单时，插件需要在菜单里填充哪些“物品”，以及这个物品在被点击时是否需要触发回调逻辑。

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

菜单一定要先向 MGC 注册，否则是无法打开的。注册的时候会告诉 MGC，这个菜单的标题是什么，有几行，打开它需要什么权限节点。

注册方式如下，由主类的 `onEnable()` 调用 `registerMenu()`。这四个参数分别是这个菜单的类、显示在左上角的标题（支持 `MiniMessage`，不过给它设置颜色似乎不好看）、行数（在 1 ~ 6 之间）、权限节点。

```java
@Override
public void onEnable() {
    MenuFacade.registerMenu(ExampleMenu.class, "示例菜单", 4, "mgc.mgc");
    // 其他启用时逻辑
}
```

为一个玩家打开这个菜单的例子（最简例子，有需要时可以传入除 `player` 外的其他参数）：

```java
private void openMenuFor(Player player) {
    new ExampleMenu(player).open();
}
```
MGC 还提供了 `/menu` 这个命令，可以直接打开菜单。 （相关信息待补充，先等 `/menu` 写好）

另外，MGC 还内置了一个好用的 confirm 菜单，可以加一个确认操作，需要玩家额外点击一次“确认”，
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

### 玩家死亡、退出事件处理和游戏等待等策略

这几个处理方式类似，以玩家死亡策略说明：

默认的处理策略是直接取消死亡事件。如果你的游戏中默认的死亡处理方法不好用，可以自己实现一个死亡处理策略：

1. 新建一个类`ExamplePlayerDeathStrategy`，继承`AbstractPlayerDeathStrategy`。
2. 重写`onPlayerDeath(PlayerExt player, PlayerDeathEvent event)`方法
3. 在你的游戏类中重写下面的方法，声明用这个策略：
```java
// ExampleGame
public @NotNull AbstractPlayerDeathStrategy getPlayerDeathStrategy(){
    return new ExamplePlayerDeathStrategy(this);
}
```

玩家退出策略、游戏等待策略也是类似，都是继承对应的抽象类，然后去游戏类中声明用哪个策略。

> 如果你觉得自己的策略别人也可能用到，可以提PR到这个仓库，把代码直接放在这个框架里。
> 其它人使用时，只需要在步骤3中，改成`new YourPlayerDeathStrategy(this)`

### PlayerExt扩展函数

为了方便小游戏的开发，player对象必须拥有`getParty`，`isInGame`等方法，但java中没有扩展函数的写法。

所以，新增了一个`PlayerExt`类，只需要`new PlayerExt(player)`就可以把mc的`player`包装成功能更丰富的`player`

`PlayerExt`本身不包含任何成员变量，只是负责调用各种`Manager`或各种`Util`，所以只要new时传入的player相同，他们就没任何区别。

本框架大部分地方的`Player`都包装成了`PlayerExt`，一般直接用就可以。

> 如果你觉得可以加一个好用的扩展函数，也可以写在这个框架里

### 一些工具类说明

详细说明见对应工具类的文档，这里仅列简介

- `LocationSelector`: 可以调用它来选取坐标。用`PlayerExt`中的`selectLocation`方法调用。
- `TextParser`: 用于搞彩色字符串
- `Sender`: 它和它的实现类用于给各种对象发消息：包含队伍、游戏内所有玩家、日志等。Game类中已经集成了一个sender
