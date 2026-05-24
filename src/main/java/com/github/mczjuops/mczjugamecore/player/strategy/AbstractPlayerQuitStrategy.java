package com.github.mczjuops.mczjugamecore.player.strategy;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.jetbrains.annotations.NotNull;

/**
 * 玩家中途退出的解决逻辑
 * 退出原因：掉线、执行指令退出
 */
public abstract class AbstractPlayerQuitStrategy {
    protected final AbstractGame game;
    public AbstractPlayerQuitStrategy(@NotNull AbstractGame game){
        this.game = game;
    }

    /**
     * 实现类需要实现解决玩家退出的方法。可以是直接终止游戏，也可以啥都不做继续进行。
     * @param player    退出玩家
     * @param reason    退出原因
     */
    public abstract void onPlayerQuit(@NotNull PlayerExt player, @NotNull  PlayerQuitReason reason);
}
