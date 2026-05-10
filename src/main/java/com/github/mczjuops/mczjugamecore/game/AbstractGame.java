package com.github.mczjuops.mczjugamecore.game;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.room.AbstractGameRoom;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.GameWaitStrategy;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.strategy.AbstractPlayerDeathStrategy;
import com.github.mczjuops.mczjugamecore.player.strategy.AbstractPlayerQuitStrategy;
import com.github.mczjuops.mczjugamecore.player.strategy.impl.DefaultPlayerDeathStrategy;
import com.github.mczjuops.mczjugamecore.player.strategy.impl.DefaultPlayerQuitStrategy;
import com.github.mczjuops.mczjugamecore.utils.sender.Sender;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.GameSender;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 抽象游戏类，所有游戏都实现这个类，或它的子类
 * 当第一个人加入的时候创建实例
 * 请勿在onEnable前创建实例
 * 中途加入的逻辑后面再写个接口，实现那个接口就能中途加入。晚点写。
 */
public abstract  class AbstractGame {
    private GameState state = GameState.WAITING;    // 游戏状态，由本框架自动设置，请勿手动设置。
    private AbstractGameRoom gameRoom;
    /**
     * 必须有一个无参的构造器，但不建议在构造器里写任何逻辑，因为此时game room还没创建
     */
    public AbstractGame(){}

    protected final Sender sender = new GameSender(this);

    protected final ConsoleSender logger = new ConsoleSender("MGC: %s".formatted(getClass().getSimpleName()));

    /**
     * 游戏的唯一 ID，不允许重复
     */
    public abstract String getId();

    /**
     * 游戏的一些信息，主要是展示用
     */
    public abstract GameMeta getGameMeta();

    /**
     * 出现在菜单中时的游戏图标
     */
    public abstract Material getIcon();

    /**
     * 获取游戏等待逻辑
     * @return 游戏等待逻辑
     */
    public abstract GameWaitStrategy getGameWaitStrategy();

    /**
     * 获取玩家退出处理策略
     * @return  玩家退出处理策略
     */
    public @NotNull AbstractPlayerQuitStrategy getPlayerQuitStrategy(){
        return new DefaultPlayerQuitStrategy(this);
    }

    /**
     * 玩家死亡的处理策略
     * @return 玩家死亡的处理策略
     */
    public @NotNull AbstractPlayerDeathStrategy getPlayerDeathStrategy(){
        return new DefaultPlayerDeathStrategy(this);
    }

    /**
     * 获取加入了游戏的玩家，在onGameInit
     * @return  玩家列表
     */
    public List<PlayerExt> getPlayers(){
        return MCZJUGameCore.getPlayerManager().getPlayers(this);
    }

    /**
     * GameManager来调用这个方法
     * @return true: 游戏初始化成功，gameManager执行start；false: 游戏无法开始，执行销毁
     */
    public boolean gameInit(){
        return onGameInit();
    }

    /**
     * 处理游戏初始化逻辑。在第一个玩家加入等待时调用。
     */
    protected abstract boolean onGameInit();

    /**
     * 在gameManager中调用这个方法
     */
    public void gameStart(){
        setState(GameState.STATING);
        onGameStart();
        setState(GameState.RUNNING);
    }

    /**
     * 处理游戏开始逻辑，比如给玩家发物品之类的。
     */
    protected abstract void onGameStart();

    /**
     * init时，如果init失败，或者多人游戏最后一个等待玩家退出，自动执行这个步骤。由GameManager调用，请勿直接调用。
     */
    public void cancelGame(){
        onGameCancel();
        setState(GameState.END);
    }

    /**
     * 游戏被取消（还未开始）
     */
    protected abstract void onGameCancel();

    /**
     * 强行终止游戏。由GameManager调用，请勿直接调用。
     */
    public void abortGame(){
        onGameAbort();
        setState(GameState.END);
    }

    /**
     * 游戏在运行中意外终止。比如最后一个玩家退出。
     */
    protected abstract void onGameAbort();

    /**
     * 游戏正常结束，执行结算流程。由GameManager调用，请勿直接调用。
     */
    public void endGame(){
        onGameEnd();
        setState(GameState.END);
    }

    /**
     * 游戏结束，执行结算流程。
     */
    protected abstract void onGameEnd();

    /**
     * 获取游戏状态：等待、运行中等
     * @return 游戏状态
     */
    public GameState getState() {
        return state;
    }


    /**
     * 设置游戏状态。请勿在开发插件时直接调用。
     * @param state 状态
     */
    public void setState(GameState state) {
        this.state = state;
    }

    public AbstractGameRoom getGameRoom() {
        return gameRoom;
    }

    public void setGameRoom(AbstractGameRoom gameRoom) {
        this.gameRoom = gameRoom;
    }

    public Sender sender(){
        return sender;
    }
}
