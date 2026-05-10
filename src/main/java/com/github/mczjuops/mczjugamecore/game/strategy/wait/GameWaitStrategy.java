package com.github.mczjuops.mczjugamecore.game.strategy.wait;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.party.Party;

public abstract class GameWaitStrategy {

    protected final AbstractGame game;
    GameWaitStrategy(AbstractGame game){
        this.game = game;
    }

    /**
     * 玩家加入等待时，由GameManager调用。如果可以开始，则由本方法调用GameManager中的start逻辑。
     * 这个和下面的party都有个代码执行顺序问题：不论是否能加入游戏，这个函数运行中的game.getPlayers()都会包含新玩家
     * @param player 加入的玩家，不需要手动执行任何操作，game实例的playerList里就能有这个玩家
     * @return  true: 允许加入; false：人满了等，不允许加入
     */
    public abstract boolean onPlayerJoin(PlayerExt player);

    /**
     * 一个party进入游戏。
     * 这样写代码会有点臃肿，但逻辑会简单一些。不考虑把player和party封装成enterRequest
     * @param party 加入的队伍，不需要手动执行任何操作，game实例的playerList里就能包含队伍中的所有玩家。分组靠PartyManager里的智能分组方法
     * @return  true: 允许加入; false：人满了等，不允许加入
     */
    public abstract boolean onPartyJoin(Party party);

    /**
     * 在游戏等待状态下，玩家离开时，由GameManager调用。
     * 和hyp一样，队长离开不会带队伍全员离开，除非p warp带走所有人，这个时候会一个个退出
     * 只有在等待开始时才会执行
     */
    public abstract void onPlayerLeave(PlayerExt player);

    /**
     * 尝试强制开始游戏，一般是指令触发。
     * 很多游戏不用到满人开始，而是感觉人差不多了，就可以在等待房间点开始按钮尝试开始游戏。
     */
    public abstract void tryStart();


    /**
     * 确认可以开始了，就调用这个函数来开始游戏。由GameManager调用
     */
    public void startGame(){
        MCZJUGameCore.getGameManager().startGame(game);
    }

    /**
     * 取消游戏，调用这个函数。由GameManager调用
     */
    public void cancelGame(){
        MCZJUGameCore.getGameManager().cancelGame(game);
    }
}
