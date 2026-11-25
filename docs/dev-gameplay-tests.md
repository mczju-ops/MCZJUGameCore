# 小游戏联机自动测试

使用 [mczju-game-test skill](../skills/mczju-game-test/SKILL.md) 时，可以直接让 AI 安装 Minecraft 测试环境，或者测试当前基于 MGC 的小游戏插件。AI 会根据运行环境选择安装入口，构建小游戏并启动独立的本地 Paper，通过 Minecraft Console Client（MCC）的内置 MCP 操作真实玩家会话。Windows 已适配原生入口，联机验收待测试。

默认使用本地测试服。首次启动需要你明确同意 [Minecraft EULA](https://aka.ms/MinecraftEULA)。如果明确指定已有服务器，可以使用为该场景准备好的测试房间和账号连接。

## 在小游戏仓库中维护案例

将可重复执行的案例保存到小游戏仓库的 `mc-tests/`。在 JSON 中描述场景需要的房间、地图、玩家、准备步骤、玩法输入和预期结果。把配置、地图副本等用到的文件放在案例文件所在目录的子目录里。已有专用测试入口的项目也可以继续使用它。

例如，双人猜羊毛案例用两名 MCC 玩家实际入场，验证后手摆放受限、先手提交空槽的反馈，以及正确放置后的小游戏胜负结算。示例还覆盖玩家中途退出后的结果。小游戏可以按自己的玩法设计额外案例，包括队伍角色、回合顺序、自定义菜单、重连和数据保留。

- [工具安装、运行参数与已有服务器接入](../skills/mczju-game-test/references/setup.md)
- [案例格式和断言](../skills/mczju-game-test/references/scenarios.md)
- [双人猜羊毛案例](../skills/mczju-game-test/assets/wordle)

MCC 提供玩家视角的聊天、背包、菜单和世界信息。需要核对服务端的事件、房间或方块状态时，在案例中启用独立探针。运行器按案例保留报告、Paper 日志、MCC 日志和探针证据。
