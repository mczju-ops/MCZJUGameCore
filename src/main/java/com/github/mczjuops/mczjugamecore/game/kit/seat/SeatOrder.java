package com.github.mczjuops.mczjugamecore.game.kit.seat;

import java.util.Collection;
import java.util.List;

/**
 * 座次顺序：谁在谁的下家、两家隔几步。
 *
 * <p>日麻要按座次给荣和排优先级、判断「吃只能吃上家」；SoulCard 要按固定顺序轮转。
 * 顺序在构造时固定，成员的身份用 {@link Object#equals} 比较。
 *
 * @param <P> 座位类型
 */
public final class SeatOrder<P extends Seat> {

    private final List<P> seats;

    public SeatOrder(Collection<P> seats) {
        this.seats = List.copyOf(seats);
    }

    /** 按座次排列的全部座位（不可修改）。 */
    public List<P> all() {
        return seats;
    }

    public int size() {
        return seats.size();
    }

    public boolean isEmpty() {
        return seats.isEmpty();
    }

    /** @return 下标；不在此座次表中返回 {@code -1} */
    public int indexOf(P seat) {
        for (int i = 0; i < seats.size(); i++) {
            if (seats.get(i).equals(seat)) {
                return i;
            }
        }
        return -1;
    }

    public P at(int index) {
        return seats.get(Math.floorMod(index, seats.size()));
    }

    /**
     * 下家（末尾回卷）。
     *
     * @throws IllegalArgumentException 该座位不在座次表里
     */
    public P next(P from) {
        int i = indexOf(from);
        if (i < 0) {
            throw new IllegalArgumentException(from.name() + " 不在座次表中");
        }
        return at(i + 1);
    }

    /** 上家。 */
    public P previous(P from) {
        int i = indexOf(from);
        if (i < 0) {
            throw new IllegalArgumentException(from.name() + " 不在座次表中");
        }
        return at(i - 1);
    }

    /**
     * 从 {@code from} 数到 {@code to} 需要走几步（{@code from} 自己为 0）。
     *
     * <p>日麻用它排荣和优先级：步数越小越优先。
     */
    public int distance(P from, P to) {
        int a = indexOf(from);
        int b = indexOf(to);
        if (a < 0 || b < 0) {
            throw new IllegalArgumentException("座位不在座次表中");
        }
        return Math.floorMod(b - a, seats.size());
    }

    /** 从 {@code from} 的下家开始，按座次绕一圈（不含 {@code from} 自己）。 */
    public List<P> after(P from) {
        int i = indexOf(from);
        if (i < 0) {
            throw new IllegalArgumentException(from.name() + " 不在座次表中");
        }
        List<P> out = new java.util.ArrayList<>(seats.size() - 1);
        for (int k = 1; k < seats.size(); k++) {
            out.add(at(i + k));
        }
        return out;
    }
}
