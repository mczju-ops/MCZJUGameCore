package com.github.mczjuops.mczjugamecore.game;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;

/**
 * 允许玩家中途加入，Game类继承这个接口，以实现允许玩家中途加入
 */
public interface MidGameJoinable {

    /**
     * 当玩家尝试中途加入时
     * @param player    玩家
     * @return  true: 允许中途加入
     */
    boolean onPlayerMidJoin(PlayerExt player);
}
