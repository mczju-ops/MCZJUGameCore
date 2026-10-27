package com.github.mczjuops.mczjugamecore.game.kit.hand;

import com.github.mczjuops.mczjugamecore.game.kit.seat.Seat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 手牌管理：手牌就是玩家背包里的真实物品，可自由交换位置（理牌）。
 *
 * <p>人机没有背包，手牌存在内存里；两者的读写在同一个方法里分流，
 * 所以游戏逻辑不需要到处判断「这是不是人机」。
 *
 * <p>背包放不下时进入溢出列表，{@link #flushOverflow} 会在回合开始时尝试放回。
 *
 * <p><b>所有方法都必须在主线程调用</b>（游戏线程请通过
 * {@link com.github.mczjuops.mczjugamecore.game.kit.thread.GameThread#call} 编组）。
 */
public final class Hand<T> {

    private final HandItemCodec<T> codec;
    private final HandLayout layout;
    private final Comparator<T> sorter;

    private final Map<Seat, List<T>> botHands = new HashMap<>();
    private final Map<Seat, List<T>> overflow = new HashMap<>();

    public Hand(HandItemCodec<T> codec, HandLayout layout) {
        this(codec, layout, null);
    }

    /**
     * @param sorter 自动理牌的比较器；{@code null} 表示不自动排序（SoulCard 让玩家自己拖）
     */
    public Hand(HandItemCodec<T> codec, HandLayout layout, Comparator<T> sorter) {
        this.codec = codec;
        this.layout = layout;
        this.sorter = sorter;
    }

    public HandItemCodec<T> codec() {
        return codec;
    }

    public HandLayout layout() {
        return layout;
    }

    // ==================================================================
    //  读取
    // ==================================================================

    /** 当前可见手牌（不含溢出）。 */
    public List<T> scan(Seat seat) {
        if (seat.isBot()) {
            return new ArrayList<>(botHands.computeIfAbsent(seat, k -> new ArrayList<>()));
        }
        Player player = seat.player();
        if (player == null) {
            return new ArrayList<>();
        }
        requireMainThread();
        PlayerInventory inventory = player.getInventory();
        List<T> out = new ArrayList<>();
        for (int slot : layout.handSlots()) {
            T item = codec.read(inventory.getItem(slot));
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }

    /** 手牌总数（可见 + 溢出）。胜负判定与「手上几张」都应该用这个。 */
    public int size(Seat seat) {
        return scan(seat).size() + overflowOf(seat).size();
    }

    public int visibleCount(Seat seat) {
        return scan(seat).size();
    }

    public List<T> overflowOf(Seat seat) {
        return new ArrayList<>(overflow.computeIfAbsent(seat, k -> new ArrayList<>()));
    }

    public int overflowCount(Seat seat) {
        return overflow.computeIfAbsent(seat, k -> new ArrayList<>()).size();
    }

    public boolean has(Seat seat, String key) {
        return count(seat, key) > 0;
    }

    public int count(Seat seat, String key) {
        int n = 0;
        for (T item : scan(seat)) {
            if (key.equals(codec.key(item))) {
                n++;
            }
        }
        for (T item : overflowOf(seat)) {
            if (key.equals(codec.key(item))) {
                n++;
            }
        }
        return n;
    }

    /** 找到第一张 key 匹配的牌（可见优先，再找溢出）。 */
    public T first(Seat seat, String key) {
        for (T item : scan(seat)) {
            if (key.equals(codec.key(item))) {
                return item;
            }
        }
        for (T item : overflowOf(seat)) {
            if (key.equals(codec.key(item))) {
                return item;
            }
        }
        return null;
    }

    // ==================================================================
    //  写入
    // ==================================================================

    /** 覆盖手牌：先清空所有手牌槽，再按顺序写入；放不下的进溢出。 */
    public void set(Seat seat, List<T> items) {
        clear(seat);
        for (T item : items) {
            give(seat, item);
        }
    }

    /** 塞入一张牌。手牌槽满了进溢出。 */
    public boolean give(Seat seat, T item) {
        if (item == null) {
            return false;
        }
        if (seat.isBot()) {
            List<T> hand = botHands.computeIfAbsent(seat, k -> new ArrayList<>());
            if (hand.size() < layout.capacity()) {
                hand.add(item);
            } else {
                overflow.computeIfAbsent(seat, k -> new ArrayList<>()).add(item);
            }
            return true;
        }
        Player player = seat.player();
        if (player == null) {
            return false;
        }
        requireMainThread();
        PlayerInventory inventory = player.getInventory();
        for (int slot : layout.handSlots()) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, codec.render(item));
                return true;
            }
        }
        overflow.computeIfAbsent(seat, k -> new ArrayList<>()).add(item);
        return true;
    }

    /** 取走一张 key 匹配的牌（先溢出、再手牌）。 */
    public T take(Seat seat, String key) {
        List<T> extra = overflow.computeIfAbsent(seat, k -> new ArrayList<>());
        for (int i = 0; i < extra.size(); i++) {
            if (key.equals(codec.key(extra.get(i)))) {
                return extra.remove(i);
            }
        }
        if (seat.isBot()) {
            List<T> hand = botHands.computeIfAbsent(seat, k -> new ArrayList<>());
            for (int i = 0; i < hand.size(); i++) {
                if (key.equals(codec.key(hand.get(i)))) {
                    return hand.remove(i);
                }
            }
            return null;
        }
        Player player = seat.player();
        if (player == null) {
            return null;
        }
        requireMainThread();
        PlayerInventory inventory = player.getInventory();
        for (int slot : layout.handSlots()) {
            T item = codec.read(inventory.getItem(slot));
            if (item != null && key.equals(codec.key(item))) {
                inventory.setItem(slot, null);
                return item;
            }
        }
        return null;
    }

    /** 清空手牌槽与溢出列表（不动保留槽与副手）。 */
    public void clear(Seat seat) {
        overflow.remove(seat);
        if (seat.isBot()) {
            botHands.computeIfAbsent(seat, k -> new ArrayList<>()).clear();
            return;
        }
        Player player = seat.player();
        if (player == null) {
            return;
        }
        requireMainThread();
        PlayerInventory inventory = player.getInventory();
        for (int slot : layout.handSlots()) {
            inventory.setItem(slot, null);
        }
    }

    /** 把保留槽物品与副手占位物写进背包。 */
    public void applyLayout(Seat seat) {
        if (seat.isBot()) {
            return;
        }
        Player player = seat.player();
        if (player == null) {
            return;
        }
        requireMainThread();
        layout.applyTo(player.getInventory());
    }

    /** 尝试把溢出区放回手牌槽。 */
    public void flushOverflow(Seat seat) {
        List<T> extra = overflow.computeIfAbsent(seat, k -> new ArrayList<>());
        if (extra.isEmpty()) {
            return;
        }
        List<T> remaining = new ArrayList<>();
        for (T item : new ArrayList<>(extra)) {
            if (visibleCount(seat) < layout.capacity() && give(seat, item)) {
                continue;
            }
            remaining.add(item);
        }
        extra.clear();
        extra.addAll(remaining);
    }

    /**
     * 按构造时的比较器重排手牌（日麻的自动理牌）。
     *
     * <p>没有比较器时是空操作。
     */
    public void sort(Seat seat) {
        if (sorter == null) {
            return;
        }
        if (seat.isBot()) {
            botHands.computeIfAbsent(seat, k -> new ArrayList<>()).sort(sorter);
            return;
        }
        List<T> current = scan(seat);
        if (current.size() < 2) {
            return;
        }
        current.sort(sorter);
        set(seat, current);
    }

    /** 丢掉某个座位在 kit 里的全部记录（玩家退出时调用）。 */
    public void forget(Seat seat) {
        botHands.remove(seat);
        overflow.remove(seat);
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Hand 只能在主线程调用（游戏线程请用 GameThread#call）");
        }
    }

    /** 该物品是否属于本游戏（用于监听器判断要不要拦）。 */
    public boolean owns(ItemStack stack) {
        return codec.read(stack) != null;
    }
}
