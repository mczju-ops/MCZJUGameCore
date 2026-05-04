package com.github.mczjuops.mczjugamecore.player.strategy;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.bukkit.event.entity.PlayerDeathEvent;

public abstract class AbstractPlayerDeathStrategy {
    protected final AbstractGame game;
    public AbstractPlayerDeathStrategy(AbstractGame game){
        this.game = game;
    }

    /**
     * 实现类需要实现解决玩家死亡的处理方法。可以是直接终止游戏，也可以直接原地复活玩家。
     * @param player    死亡玩家
     * @param event    死亡事件
     */
    public abstract void onPlayerDeath(PlayerExt player, PlayerDeathEvent event);
}
