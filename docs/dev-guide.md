# 基于 `MCZJUGameCore` 的小游戏插件开发流程

本文档介绍一个基于 `MCZJUGameCore`（下面简称 `MGC`）的小游戏插件的完整开发流程。

---

## 一、写在前面

### 1. 一些预备知识

为了顺利理解本文档，你需要事先关注以下内容：

- Java 程序设计的基础知识。
- Minecraft Paper 插件开发的相关概念。

> 可参考运维部的[插件开发指南](https://github.com/mczju-ops/mczju-infra-docs/blob/main/plugin/dev/README.md)。

- 关于面向对象：什么是“继承”，什么是“重写”一个方法，以及“抽象类”这一概念。（本文档将频繁提及）

---

### 2. 了解 `MCZJUGameCore` 插件的意义

`MGC` 的意义如下：

- 负责统一管理。有了它，玩家加入一个游戏时，就不允许加入另一个；玩家在不同游戏中，会有独立的物品栏、末影箱、经验值。

- 大幅降低小游戏开发成本。它可以为你省去非常多麻烦，你可以很方便地定义玩家加入时、游戏开始或结束时、玩家中途死亡或退出等情况下的行为。

> 你只需要定义“行为”，不再需要单独设计框架，让这些行为被调用，`MGC` 会自动实施这些行为。

---

### 3. 开发前的准备

为了进行开发和测试工作，你需要做好以下准备：

- 准备开发环境（IDE）：可以选择 `IntelliJ IDEA`（下面简称 `IDEA`）。
- 准备测试环境：在本地开一个 Paper 测试服，用于测试插件。（记得加 `MGC` 插件）。
- 了解重要参考资料：以 [Paper 文档](https://jd.papermc.io/paper/1.21.7/)为主。

---

## 二、搭建插件框架

在决定好要开发什么游戏、想好插件名后，创建这个 Paper 插件项目（建议使用 `Maven` 构建项目）。

> 如果使用 `IDEA` 开发，请安装名为 `Minecraft Dvelopment` 的 IDEA 插件。这样你就可以直接设置一个模板，
> 即 `Plugin`、`Paper`、`Maven`、`Java`、`1.21.7`等预设。

然后就可以开始写功能了。不过，由于这个插件是 `MGC` 的子插件，因此，你需要额外做下面的事情：

- 在 `plugin.yml` 中，添加字段 `depend: [ MCZJUGameCore ]`。
  它的作用是，让服务器在启动的时候先加载 `MGC`，再加载你的插件。（毕竟你的插件依赖 `MGC`）
- 在 `pom.xml` 中，补充 `MGC` 依赖（就像添加 `Paper` 依赖一样），示例如下。（如果你不是使用 `Maven` 构建，也有对应的方法）

```xml
<repositories>
    <repository>
        <id>papermc-repo</id>
        <url>https://repo.papermc.io/repository/maven-public/</url>
    </repository>
    <repository>
        <id>jitpack.io</id>
        <url>https://www.jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>io.papermc.paper</groupId>
        <artifactId>paper-api</artifactId>
        <version>1.21.7-R0.1-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>com.github.mczju-ops</groupId>
        <artifactId>MCZJUGameCore</artifactId>
        <version>1.0.2</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```
> 如果你在创建项目时，开发环境为你提供了模板，那 Paper 很可能已经自动依赖了，不要重复添加。

> 上面的例子中，`MGC` 的版本是 `1.0.2`，未来如果发布了新版本，你需要在这里更新版本号。

---

## 三、开始小游戏功能开发

正式开始写代码之前，请先大致思考下面这些问题，它们会在后面的开发过程中有所体现：

- 这个小游戏插件的**生命周期**是怎么样的，换句话说，玩家开始游戏时、结束游戏时，插件分别需要做什么。
- 这个小游戏插件是否有“一局”这样的概念。比如，起床战争就有明确的一场对局，而炒鸡世界这样的游戏只有游玩状态。
- 这个小游戏插件是否需要支持多**房间**。比如，一局起床战争开始后，场景就被占用了，其他玩家就无法加入该地图了。
  但是，如果有多个房间，其他玩家就可以加入空闲房间，使多场游戏同时进行。
  而炒鸡世界这样的游戏就不需要支持多房间，大家都在公共场景中游玩。

无论这个小游戏是怎么样的，你都需要在项目中创建两个类：**游戏类**和**房间类**。并且，这两个类均有且仅有一个。

### 1. 编写游戏类

游戏类是你的小游戏插件和 `MGC` 之间最主要的桥梁。

游戏类是 `MGC` 中 `AbstractGame` 这个抽象类的子类。

由于它是一个子类，因此你需要重写基类的方法。重写的过程，可以理解为在做两件事：

- 告诉 `MGC` 这个小游戏的基本信息（名称、描述等）。
- 小游戏在玩家加入、游戏结束时等关键生命周期节点需要做的事情。

---

#### 游戏类代码示例

下面给出一个例子。请观察其中的注释，并结合下方的说明来理解。

> 先别着急直接把这段内容移植到你的代码中，因为很可能会很不一样，请看后面的补充。

```java
import com.github.mczjuops.mczjugamecore.game.strategy.wait.DefaultGameWaitStrategy;

public class ExampleGame extends AbstractGame {

    // 小游戏唯一 ID，不同小游戏不能重复
    @Override
    public String getId() {
        return "example_game";
    }

    // 小游戏的基本信息。玩家在访问主菜单时，可以看到每个小游戏的基本信息
    @Override
    public GameMeta getGameMeta() {
        return GameMeta.builder()
                .displayName("<gold>示例小游戏") // 显示名
                .icon(Material.OAK_PLANKS) // 菜单中的图标
                .author("<green>MCZJU") // 你的名字
                .description(List.of(
                        "<aqua>这是一个示例小游戏",
                        "<aqua>单人游玩",
                        "<dark_aqua>开始游戏后，跳跃一次就会结束游戏"
                )) // 呈现在菜单中的描述，逐行显示每个字符串
                .build();
    }

    // 初始化房间时需要做什么。比如可能需要把地图还原成默认状态等。也可能什么都不需要做
    // 注意，对于多人游戏来说，这一步是第一个玩家加入时被调用的（他在等待更多玩家加入），而非玩家正式开始游戏时调用的
    @Override
    protected boolean onGameInit() {
        return true; // 返回 true 表示初始化成功。如果返回 false，玩家将无法进入游戏
    }

    // 游戏真正开始时需要做什么。从这里开始，整个游戏的过程就是由本插件
    @Override
    protected void onGameStart() {
        ExampleGameRoom gameRoom = (ExampleGameRoom) getGameRoom();
        sender.info("你进入了地图%s".formatted(gameRoom.mapName));

        // 示例：将玩家传送到你设置的点位，然后给予初始物资
        getPlayers().forEach(player -> {
            player.player().teleport(gameRoom.spawnAt);
            player.giveItem(item);
        });
        // 更多逻辑。如果希望这个类不要过于臃肿，你可以将主要逻辑写在其他类中，并在此处调用相应方法
    }

    // 游戏取消时做什么
    // 游戏初始化失败或玩家在等待阶段全部退出了，这时游戏会取消
    @Override
    protected void onGameCancel() {
        // 多数情况下，什么都不需要做
    }

    // 游戏意外终止时做什么
    // 游戏进行过程中，所有玩家都退出游戏，会导致游戏终止
    @Override
    protected void onGameAbort() {
        // 比如将地图中残留的怪物、掉落物清除
    }

    // 一局游戏结束时做什么
    // 通常是你的代码决定何时结束，具体请看后面的说明
    @Override
    protected void onGameEnd() {
        // 比如刷新排行榜、将玩家传送到游戏场景外等
    }

    // 游戏的“等待策略”，也就是玩家加入后，什么情况下允许正式开始游戏，或自动正式开始游戏
    // 这个概念主要是针对多人游戏的，玩家有一个“匹配”过程
    @Override
    public GameWaitStrategy getGameWaitStrategy() {
        return new DefaultGameWaitStrategy(this, 4, 2); // 代表至少 2 人、至多 4 人的情况下，可以正式开始游戏
    }

    // 可能还会有更多方法被重写（有些方法有默认行为，可以不重写）
}
```

> 特别注意：上面重写的方法，比如 `onGameInit()`、`onGameEnd()` 等，请**不要**调用。
> 
> 它们都是由 `MGC` 的管理系统调用的，你重写它们的过程相当于是在“声明”。

---

#### 处理“策略”（Strategy）

为了处理游戏过程中发生的特定情况，需要使用特定“策略”。你可以将其理解为一种模式。请看下面的说明。

##### a. 等待策略

阅读了 `getGameWaitStrategy()`，你应该大致能理解，游戏的开始过程其实是 `MGC` 负责的。

上面的例子中的这个写法，它的含义是，当有至少两个玩家在游戏中时，就可以使用 `/mgc start` 正式开始游戏；
当人数到达 4 人时，就会自动开始游戏。

因此，你就不需要手动实现这个功能了。这也是 `MGC` 的重要意义之一。

`GameWaitStrategy` 也是一个抽象类，可以理解为等待策略的一个模型。
如果你想设计一个自定义的等待策略，例如：
- 当等待的玩家数量变为 2 人时，自动开始一个 30 秒的游戏开始倒计时。
- 当等待的玩家数量变为 4 人时，进入一个 5 秒的游戏开始倒计时。

你就可以写一个 `GameWaitStrategy` 的子类，并在 `getGameWaitStrategy()` 中声明它。

阅读 `AbstractGame` 的源码后，你可能会注意到还有一些方法也可以被重写，其中就包括下面这两个策略。

##### b. 玩家死亡处理策略

`AbstractGame` 中有一个方法是 `getPlayerDeathStrategy()`，它定义了当玩家处于你的游戏中时，如果死亡了会发生什么。

默认的行为是会取消死亡事件，也就是本该死亡的时候，玩家会立即恢复所有生命值，留在原地，同时获得 10 tick 的无敌时间。

如果这个策略不适用于你的游戏，比如你希望玩家死后正常复活，或者直接判定失败等，你就可以重写这个方法。
你需要写一个 `AbstractPlayerDeathStrategy` 的子类，在其中定义玩家死亡时的处理逻辑。

##### c. 玩家退出处理策略

类似地，还有 `getPlayerQuitStrategy()` 也可以重写，它定义了玩家退出游戏时（使用命令 `/mgc leave` 退出，或直接退出服务器）时需要做什么。

默认情况下，任意玩家退出会使这局游戏直接中断（流局），所有玩家都被移出。

如果你希望实现自定义逻辑（比如剩下的玩家继续游戏），你就需要写一个 `DefaultPlayerQuitStrategy` 的子类，然后重写 `getPlayerQuitStrategy()`。

> 你也可以理解为，这是 `MGC` 在要求你，必须定义玩家死亡、退出时的处理模式，否则游戏设计是不完整的。

##### d. 特殊情况：允许中途加入

默认情况下，一局游戏开始后，其他玩家就无法中途加入了。但是如果你希望可以中途加入，也很简单。

你需要让这个游戏类，在继承 `AbstractGame` 的情况下，同时实现接口 `MidGameJoinable`，
并重写这个接口声明的唯一方法 `onPlayerMidJoin(PlayerExt player)`，代表一个玩家加入时的处理模式。

示例：

```java
public class ExampleGame extends AbstractGame implements MidGameJoinable {
    
    @Override
    public String getId() {
        return "example_game";
    }
    
    @Override
    public GameMeta getGameMeta() {
        return GameMeta.builder()
                .displayName("<gold>示例小游戏")
                .icon(Material.OAK_PLANKS)
                .author("<green>MCZJU")
                .description(List.of(
                        "<aqua>这是一个示例小游戏",
                        "<aqua>单人游玩",
                        "<dark_aqua>开始游戏后，跳跃一次就会结束游戏"
                ))
                .build();
    }

    @Override protected boolean onGameInit() { return true; }
    @Override protected void onGameStart() {}
    @Override protected void onGameCancel() {}
    @Override protected void onGameAbort() {}
    @Override protected void onGameEnd() {}
    
    @Override
    public GameWaitStrategy getGameWaitStrategy() {
        return new DefaultGameWaitStrategy(this, 4, 2);
    }
    
    // 一个玩家中途加入时
    @Override
    public boolean onPlayerMidJoin(PlayerExt player) {
        // 示例：提醒其他玩家
        sender().info("<gold>%s加入了该游戏！".formatted(player.displayName()));
        // 其他逻辑，与你自己的游戏逻辑对接
    }
}
```

---

#### 特殊类型的小游戏

上面这个例子 `ExampleGame`，它是最通用的写法。但是，部分情况下，你不一定要照着这个例子写。

##### a. 特殊类型：`SinglePlayerGame`

观察 `getGameWaitStrategy()`，不难理解一个事实：如果这个小游戏完全是单人游玩的，
那么大部分情况下，等待策略都是，只要有一个玩家加入，就直接开始游戏，不需要手动设置最少人数、最大人数，因为都是 1。

因此，`MGC` 预设了一个游戏类型 `SinglePlayerGame`，它本身也是一个抽象类，是 `AbstractGame` 的子类。
相比于 `AbstractGame`，它额外实现了适用于单人游戏的等待策略，也就是只要有一个玩家加入，就直接开始。

此外，它还添加了一个适用于单人游戏的方法 `getPlayer()`，可以直接拿到当前游戏中的玩家。

如果你开发的是一个单人游戏，你就可以不继承 `AbstractGame`，而是直接继承 `SinglePlayerGame`。
此时仍然间接继承了 `AbstractGame`，因此仍符合 `MGC` 的要求，也让你的开发更加方便了。

---

##### b. 特殊类型：`OpenSessionGame`

有这样一类游戏，没有“一局”的概念，玩家只有“游玩中”状态。比如下面的例子：

- 各玩家独立的成长型小游戏，玩家进入游戏后打怪收集资源、升级装备。
  比如可以把炒鸡世界理解成一个游戏，玩家随时可以加入或退出，加入游戏后自动恢复为上一次的游玩状态。
- 猜灯谜这样的小游戏，没有明显的生命周期，不同玩家在公共的场景中游玩。
- 一定程度上自行管理生命周期的小游戏，比如跑酷游戏，你自己负责为玩家开始、结束一次跑酷并统计时间。

`MGC` 对于这类小游戏的处理模式是，使这个游戏一直保持运行状态，玩家可以中途加入（和作为首个玩家加入没有区别）。对应上面的策略，就是：

- 等待策略：满 1 人即可自动开始游戏。
- 玩家退出策略：玩家退出，但游戏不会结束。
- 可中途加入：允许玩家中途加入，加入时的逻辑和首个玩家加入时完全一致。
- `onGameEnd()` 等方法：不再允许重写，因为不适用（比如游戏不会结束）。

`MGC` 专门设计了 `AbstractGame` 的子类 `OpenSessionGame`。通过阅读源码，你就会发现，你只需要重写
`onPlayerJoin(PlayerExt player)` 和 `onPlayerQuit(PlayerExt player)`，并在有需要时重写部分其他方法。

许多方法被加上了 `final` 关键字，不允许被进一步重写，你不需要关心它们。

示例：

```java
public class ExampleParkourGame extends OpenSessionGame {
    
    @Override
    public String getId() {
        return "ExampleParkourGame";
    }

    @Override
    public GameMeta getGameMeta() {
        return GameMeta.builder()
                .displayName("<#FF55AA>某跑酷游戏")
                .icon(Material.IRON_BOOTS)
                .description(List.of("<yellow>沉浸式跑酷"))
                .author("<aqua>iceFoil")
                .build();
    }
    
    // 对于 OpenSessionGame 来说，会在首个玩家进入游戏时被调用，此后直到关服都不再会被调用
    @Override
    protected boolean onGameInit() { return true; }

    @Override
    public void onPlayerJoin(PlayerExt player) {
        sender().info("<aqua>%s加入了跑酷".formatted(player.getDisplayName())); // 示例：通知其他正在跑酷中的玩家

        CubeMansionRoom room = (CubeMansionRoom) getGameRoom();
        if (room.spawnAt != null) {
            player.player().teleport(room.spawnAt); // 示例：将玩家传送到跑酷地图内
        }
    }

    @Override
    public void onPlayerQuit(PlayerExt player) {
        plugin.getSessionManager().handleAbort(player); // 示例：如果在跑酷过程中，直接取消计时（假设 plugin 是本小游戏插件的主类实例）

        sender().info("<dark_aqua>%s离开了跑酷".formatted(player.getDisplayName()));

        CubeMansionRoom room = (CubeMansionRoom) getGameRoom();
        if (room.quitAt != null) {
            player.player().teleport(room.quitAt); // 示例：将玩家传送到跑酷地图外面
        }
    }
}
```

##### c. 更多类型

目前 `MGC` 只有上面说的 `SinglePlayerGame` 和 `OpenSessionGame` 这两个特殊的“预设”，
没有其他的诸如 `MultiplyPlayersGame`、`TeamBasedGame` 等预设。

也就是说，如果你开发的游戏不属于有预设的这两类游戏，你就只能直接 `AbstractGame`，并一一重写方法。

如果你认为 `MGC` 有必要加一种新的游戏预设，欢迎随时提出。

> 总结一下这部分（`1. 编写游戏类`）：
> 
> 你开发的小游戏插件，需要定义一个（有且仅有一个） `AbstractGame` 的子类。
  至于怎么让 `MGC` 识别到你写的这个游戏类，后面“注册”部分会提到。

---

### 2. 编写房间类

每个小游戏插件除了需要写唯一的一个**游戏类**，还需要写唯一的一个**房间类**。

请先通过这几个例子理解“房间”这个概念：

- 打地鼠小游戏中，一个玩家开始玩后，其他玩家就不能加入了，因为这个摊位被占用了。所以可以设置多个摊位，这样就能实现多个游戏实例同时存在。有几个摊位，就有几个“房间”。
- 一局饥饿游戏开始后，由于不允许中途加入，其他玩家只能等待游戏结束。如果有多个“房间”，另一局游戏就可以单独开始。

上面的例子中，不同的房间，有同一套参数，只是值不同。以打地鼠游戏为例：

- 玩家加入打地鼠游戏后，需要将他传送到他站立的点位。不同摊位都有这样一个点位 `spawnAt`，类型是 `Location`，值不相同；
- 为了让打地鼠游戏更有特色，让不同摊位的地鼠的颜色不同。于是定义这个房间的地鼠的颜色 `color`，类型可以是字符串，由游戏逻辑解析为生成的实体的颜色，并以某种形式呈现。

基于这些需求，你需要写一个 `JsonGameRoom` 的子类，示例如下：

```java
public class WhackAMoleGameRoom extends JsonGameRoom {
    public Location spawnAt; // 开始游戏后被传送到的位置
    public String color = "red"; // 地鼠的颜色。你可以设置一个默认值。若不设置，默认为 null
    // 更多
}
```

需要注意两点：

- 字段的作用域只能是 `public`。
- 字段类型只支持基本类型 `Integer`、`Boolean`、`String` 等，以及 bukkit 的 `Location`。

进入服务器后，你可以通过这个命令来编辑房间数据（当然，需要先完成注册才可以编辑，具体见后面的编辑部分）：

```
/mgcop room list|create|edit|delete <gameId> [roomId]
```

一开始，你的游戏是没有任何房间的。此时尝试加入游戏只会收到提示“没有空余房间”。
你需要通过 `create` 子命令创建。比如如果你想创建三个房间，就可以创建 ID 分别为 `roomA`、`roomB`、`roomC` 的房间。

创建成功后，`edit` 子命令会为你打开一个菜单。你可以根据提示，为当前这个房间设置参数值。
具体的操作方式，游戏内已经足够直观，这里不赘述。

你的代码中，需要使用到这些字段时，可以先获取游戏实例（也就是你创建的 `AbstractGame` 的子类的实例。比如可以通过 `PlayerExt#getGame` 获取），
再通过 `AbstractGame#getGameRoom` 获取房间实例 `room`。然后通过 `room.color` 即可获取你设置的颜色字符串。

> 提示：如果在游戏类内用到房间数据，直接使用 `getGameRoom()`，也就是 `this.getGameRoom()` 即可。

> 由于可以设置默认值，你也可以把“配置”放在这里。比如不同摊位的地鼠的生成间隔均为 40 tick，你就可以加一个字段
> `public Integer interval = 40`，这样就不需要做额外的配置工作了。

关于房间，有一个特殊情况是，希望玩家可以自主选择加入的房间。比如这几种情况：

- 不同的房间对应不同的地图，体验有所差别，你希望玩家可以自主选择游玩哪个地图；
- 某个房间已有玩家且处于等待状态，另一个玩家可能不希望和他一起游玩，后者可能希望加入另一个房间。

要实现允许玩家自主选择房间，非常简单，你只需要为房间类添加一个注解 `@PlayerSelectable`。

为此，你还需要在这个子类中添加三个字段 `icon`、`displayName` 和 `description`（均为字符串），作为玩家选择房间时呈现的信息。

示例：

```java
@PlayerSelectable
public class WhackAMoleGameRoom extends JsonGameRoom {
    public Location spawnAt;
    public String color = "red";

    public String icon; // 例如 RED_WOOL，GREEN_WOOL 等，作为这个房间对应的按钮的图标
    public String displayName; // 例如 "<red>红色摊位"
    public String description; // 例如 "<yellow>一只只红色的小地鼠。是不是火龙果吃多了？"
}
```

> `icon` 字符串就是物品的原版 ID 并将字母改为大写。

这样玩家通过主菜单加入游戏时，可以选择随机加入一个房间，也可以选择自主选择房间。

玩家也可以通过命令 `/mgc joinroom <gameId> <roomName>` 来直接加入一个游戏的一个房间（若允许自主选择）。

> 如果你不需要设置任何房间参数，你也需要创建一个房间类作为占位符。可以不添加任何字段。

> 对于 `OpenSessionGame`，同样也需要有一个房间类，并且你应该只新建一个房间。

---

### 3. 向 `MCZJUGameCore` 注册游戏

写好上面说的这个游戏类和这个的房间类后，`MGC` 还识别不了你的游戏。你需要向它注册。

你需要在插件的主类的 `onEnable()` 中注册：

```java
public void onEnable() {
    MCZJUGameCore.getGameManager().registerGame(ExampleGame.class, ExampleGameRoom.class);
    // 其他插件启用时逻辑
}
```

### 4. 小游戏核心功能开发

上面的游戏类和房间类的注册，是你开发的小游戏和 `MGC` 对接的方式。在此基础上，你需要完成小游戏的所有逻辑的开发。

如何“触发”小游戏逻辑？一般来说，是在游戏类中你重写的 `onGameStart()` 方法中调用。

一局游戏结束后，如果结束？你需要调用 `MGC` 的游戏管理器：

```
MCZJUGameCore.getGameManager().endGame(game);
```

其中的 `game` 就是当前这个游戏的实例。

> 再次强调，不要通过调用游戏类中重写的 `onGameEnd()` 来结束游戏。

如何获取 `game` 这个实例呢？其实上面已经提到过了，一般有两种方法：

- 如果当前的逻辑就在 `AbstractGame` 的子类中，显然 `this` 就是 `game`。
- 另一种方式是，如果一个玩家正在游戏中，可以通过它来查询。这里需要使用包装的玩家类 `PlayerExt` 的方法 `getGame()`。

> 如果没有 `PlayerExt` 的实例，随时通过 bukkit 的 `Player` 实例创建即可：`PlayerExt playerExt = new PlayerExt(player)`。
> 在进阶文档中会有针对 `PlayerExt` 的更具体的说明。

很显然，小游戏的核心功能中，经常需要检查玩家是否处于你的游戏中。比如打地鼠游戏中，你需要识别玩家攻击“地鼠”。
此时需要排除其他玩家和地鼠的交互，你可以在监听到玩家攻击时，通过 `PlayerExt` 的方法，判断当前玩家是不是真的在玩打地鼠，示例：

```java
@EventHandler(ignoreCancelled = true)
public void onPlayerPunch(EntityDamageByEntityEvent event) {
    if (!(event.getDamager() instanceof Player player)) return;

    PlayerExt playerExt = new PlayerExt(player); // 创建 PlayerExt 实例
    if (!playerExt.isInGame(WhackAMoleGame.class)) return; // 如果这个玩家不再 WhackAMoleGame 这个类型的游戏中，则直接返回

    UUID hitId = event.getEntity().getUniqueId();
    boothManager.handleHit(player, hitId); // 后续逻辑，比如给玩家加分、播放地鼠钻地动画等
}
```

---

## 四、测试小游戏

将你的插件和 `MCZJUGameCore`（注意版本）一起装到测试服。

通过 `/mgc` 命令打开主菜单，如果你能看到你的小游戏的图标，就说明正确注册了。

先通过 `/mgcop room` 命令来创建房间，并设置房间参数。然后加入游戏，测试你写的功能。

---

## 五、进阶文档

`MCZJUGameCore` 还为你提供了许多便捷功能，详见[进阶文档](dev-advanced.md)。
