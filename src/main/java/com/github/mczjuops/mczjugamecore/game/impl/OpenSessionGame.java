package com.github.mczjuops.mczjugamecore.game.impl;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.MidGameJoinable;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.GameWaitStrategy;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.DefaultOpenSessionGameWaitStrategy;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.strategy.AbstractPlayerQuitStrategy;
import com.github.mczjuops.mczjugamecore.player.strategy.impl.DefaultQuitOpenSessionGameStrategy;
import org.jetbrains.annotations.NotNull;

/**
 * 开放会话型游戏
 *
 * <p>直观理解，就是只有“在游玩”和“不在游玩”两个状态的游戏
 * <p>自动处理了加入、中途加入游戏的策略
 * <p>子插件需要创建唯一的 GameRoom
 * <p>onGameInit 只在开服后首个玩家首次加入时被调用
 */
public abstract class OpenSessionGame extends AbstractGame implements MidGameJoinable {

    // 强制行为：调用 onPlayerJoin
    @Override
    protected final void onGameStart() {
        var players = getPlayers();
        if (!players.isEmpty()) onPlayerJoin(players.getFirst());
    }

    // 强制行为：调用 onPlayerJoin
    @Override
    public final boolean onPlayerMidJoin(PlayerExt player) {
        onPlayerJoin(player);
        return true; // 任何情况下都允许中途加入
    }

    // 不适用
    @Override
    protected final void onGameCancel() {}

    // 不适用
    @Override
    protected final void onGameAbort() {}

    // 不适用
    @Override
    protected final void onGameEnd() {}

    /** 默认行为：直接允许加入，阻止队伍加入 */
    @Override
    public GameWaitStrategy getGameWaitStrategy() {
        return new DefaultOpenSessionGameWaitStrategy(this);
    }

    /** 默认行为：调用 onPlayerQuit */
    @Override
    public @NotNull AbstractPlayerQuitStrategy getPlayerQuitStrategy() {
        return new DefaultQuitOpenSessionGameStrategy(this);
    }

    /** 任意玩家加入该游戏时 */
    public abstract void onPlayerJoin(PlayerExt player);

    /** 任意玩家退出该游戏时 */
    public abstract void onPlayerQuit(PlayerExt player);
}
