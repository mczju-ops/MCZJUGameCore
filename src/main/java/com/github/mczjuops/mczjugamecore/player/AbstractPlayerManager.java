package com.github.mczjuops.mczjugamecore.player;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.strategy.PlayerQuitReason;
import org.bukkit.entity.Player;

import java.util.List;

public interface AbstractPlayerManager {

    /**
     * 获取某个游戏内的玩家
     * @param game  游戏实例
     * @return  游戏内的玩家List
     */
    List<PlayerExt> getPlayers(AbstractGame game);

    /**
     * 玩家加入某个游戏。在插件中请勿调用这个方法，而是在Game里的onPlayerJoin中处理
     * @param game  游戏实例
     * @return  是否成功加入，当玩家正在进行其它游戏时，会加入失败。
     */
    public boolean addPlayer(AbstractGame game);

    /**
     * 加入游戏。如果玩家是队长，则会带着全队加入。如果玩家在游戏中，则强制退出
     * @param player 玩家
     * @param game 游戏
     */
    public void joinGame(PlayerExt player, AbstractGame game);

    public void leaveGame(PlayerExt player, PlayerQuitReason reason);

    public AbstractGame getPlayerGame(PlayerExt player);

    /**
     * 游戏结束，或者房间意外销毁，调用本代码，移除所有玩家。仅由GameManager调用
     * @param game 游戏
     */
    void removeAllPlayer(AbstractGame game);

    public boolean isPlayerInGame(PlayerExt player, Class<? extends AbstractGame> gameClass);

}
