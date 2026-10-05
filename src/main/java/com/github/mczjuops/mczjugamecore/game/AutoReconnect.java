package com.github.mczjuops.mczjugamecore.game;

/**
 * 标记支持掉线后自动重连的游戏，无需配置自动重连专用的退出策略。
 * 监听器记录 RUNNING 阶段的掉线，退出仍使用游戏自身的退出策略。
 * 使用时应确保退出策略不会因单个玩家退出而结束游戏；所有玩家退出或满足其他终止条件时可结束。
 * 登录档案恢复后，仅重连原游戏实例；原局不可加入时返回主大厅。
 * 仍需实现 {@link #onPlayerMidJoin} 以恢复玩家状态。记录不跨插件或服务器重启保存。
 */
public interface AutoReconnect extends MidGameJoinable {
}
