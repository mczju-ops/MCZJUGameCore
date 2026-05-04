package com.github.mczjuops.mczjugamecore.player.strategy.impl;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.strategy.AbstractPlayerDeathStrategy;
import org.bukkit.Location;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * 默认玩家死亡处理策略，直接别死了
 */
public class DefaultPlayerDeathStrategy extends AbstractPlayerDeathStrategy {
    public DefaultPlayerDeathStrategy(AbstractGame game) {
        super(game);
    }

    @Override
    public void onPlayerDeath(PlayerExt player, PlayerDeathEvent event) {
        player.player().setNoDamageTicks(10);   // 设置10t无敌
        event.setCancelled(true);
    }
}
