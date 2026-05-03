# MCZJU 小游戏框架

解决所有小游戏中的重复逻辑，如等待房间、游戏房间的tp坐标设置等，让你专注于游戏开发流程。

下面称基于本插件开发的小游戏插件为`具体插件`，一些说明以起床战争为例。

## 设计思路

- 模仿paper api，所有组件都需要注册到组件管理器(manager)中。具体插件只用和这些manager打交道(类似Bukkit中有很多manager)
- 用模板方法+多态，比如AbstractGame下可以有SinglePlayerGame、MultiPlayerGame甚至更多子类，抽象类负责写子类重复的逻辑
- 用策略模式(strategy)，让框架变得更容易扩展，比如GameWaitStrategy，默认行为是人到上限直接开，还可以再继承几个strategy提供更多的等待逻辑，让每个Game自己选(getStrategy方法)
- 为Player加扩展方法: PlayerExt，它本身只有player一个字段，但通过调用其它manager的代码，让playerExt提供isInParty等方法。让开发更符合直觉(player.getParty比playerManager.getParty(player)用起来更爽)

## 代码逻辑

1个Game类=1个游戏

1个游戏可以有1个或多个房间，这些房间拥有相同的属性(出生点之类的属性)，但具体值不同(不同房间,出生位置不同)，这些可以创建一个GameRoom类，继承JsonGameRoom，可以在游戏内的箱子菜单里修改这些值。
但有一些不用在游戏里修改的，可以直接写死的，比如击球力度，可以直接写配置文件。

另，某些游戏不需要GameRoom来放参数，但也需要实现并在游戏中创建一个房间，用来当占位符，代表有1个空闲房间。

有些简单的场景类游戏，比如猜灯谜，不需要本插件进行生命周期管理，那么就不用注册game，而是直接调用`ScoreManager`进行计分就可以。

其它游戏的整个生命周期由本插件管理，本插件自动根据Game实现类中设置的等待策略(`GameWaitStrategy`)，启动游戏。游戏进行中，由具体插件对游戏事件进行处理，比如资源点刷新等。

## 目前进度

实现了大概的框架，可以开始游戏和结束游戏了。

但还有很多钩子没有写好。比如AbstractGame中应该有onPlayerDeath。以及有很多接口还没实现，比如`PlayerManager`的`leaveGame`等。

更多需要写或修改的模块，请看issue。

## 主要模块说明

- Game：游戏类，提供onGameStart等hook，你需要继承一个抽象的游戏类，来写自己的游戏逻辑。后面本插件需要实现MultiPlayerGame等更多的抽象类，让开发更简单
- GameRoom：游戏房间，可以有1个或多个。存放房间的各种配置。比如，起床战争的房间设置中，需要有4个队伍的出生点坐标、资源刷新坐标和刷新频率等。配置不在config文件中设置，而是在游戏中用箱子菜单和调试棒设置（本插件提供）。

只有上面几个是需要在具体插件中实现的，下面的都由本插件完成：


- Menu：箱子菜单，继承后向MenuFacade注册后，就可以通过menu指令或代码让某个玩家打开这个菜单。
- Sender：发信器，包括向玩家发送消息、在Console写log、全服广播等
- PlayerExt：为方便小游戏开发，为Player类新增了一些扩展方法，放在这个类里面。
- Party：组队系统，和hyp的有点像，里面会提供智能分队的方法
- ScoreManager：单局的计分管理器，具体插件调用它来给玩家分数。HistoryScoreManager用于搞排行榜。
- ItemManager：特殊物品管理器，提供物品辨别(`isThis`)等功能
- GameRoomManager：游戏房间管理器，可以用它获取空闲房间、分配房间给一个新开的游戏

策略，用于处理具体插件中共同面临的一些事件，每一个策略写1个实现类：

- GameWaitStrategy：游戏等待策略，比如最少开始玩家、最多开始玩家、计时时间、1人游戏直接开始等。
- PlayerQuitStrategy：玩家退出策略，游戏是继续进行，还是直接停止等。

所有注册方法详见initialize包和对应manager的代码注释，后面再补文档。

### 具体插件代码示例

等框架完善一些，能正常使用后，考虑上传到jitpack仓库，就不需要每次用都自己clone本仓库编译一遍了。

见[链接](https://github.com/mczju-ops/MGC-example-plugin)

## 如何贡献

整体框架已经搭好了！

还有些地方需要改善、或者还没写，详见issue。另外目前有很多细节上的问题，有很多提示没写，比如玩家加入游戏后没提示游戏现在是几缺几，也可以提issue，然后进行小的修改。

文档将会尽快补充！

关于多人合作开发规范，详情请见[链接](https://github.com/mczju-ops/mczju-infra-docs/blob/main/dev/contributing.md)