package com.github.mczjuops.mczjugamecore.game.kit.support;

import com.github.mczjuops.mczjugamecore.game.kit.seat.Seat;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;

/**
 * 测试用的座位：没有 Bukkit 实体，可以自由摆布「人机 / 在线」。
 *
 * <p>kit 的所有交互都必须能在「没有 Player」的座位上跑通，这个类就是那条底线。
 */
public final class FakeSeat implements Seat {

    private final String name;
    private final boolean bot;
    private boolean online;

    public FakeSeat(String name, boolean bot) {
        this.name = name;
        this.bot = bot;
        this.online = !bot;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean isBot() {
        return bot;
    }

    @Override
    public boolean isOnline() {
        return bot || online;
    }

    @Override
    public PlayerExt ext() {
        return null;
    }

    public FakeSeat offline() {
        this.online = false;
        return this;
    }

    @Override
    public String toString() {
        return name;
    }
}
