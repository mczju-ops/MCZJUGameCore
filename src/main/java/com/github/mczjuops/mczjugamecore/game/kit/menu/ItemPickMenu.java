package com.github.mczjuops.mczjugamecore.game.kit.menu;

import com.github.mczjuops.mczjugamecore.game.kit.hand.HandItemCodec;
import com.github.mczjuops.mczjugamecore.game.kit.prompt.AskOptions;
import com.github.mczjuops.mczjugamecore.game.kit.prompt.Pending;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从一组物品里选一个（不一定是自己的手牌）。
 *
 * <p>用于「抽到的牌」「弃牌堆候选」「牌堆顶」「别人打出的牌」等场景。
 *
 * <p>{@link AskOptions.GroupMode#BY_KEY} 时同类物品合并成一个按钮并标注张数，
 * 交付的是该类物品的**一个代表实例**（调用方用 {@code codec.key(result)} 找回全部同类项）。
 * 日麻暗杠就是靠它选「东」而不是某一张东。
 */
public final class ItemPickMenu<T> extends KitMenu {

    /** 一个按钮背后的一条：代表实例 + 同类张数。 */
    private record Entry<E>(E representative, int count) {
    }

    private final HandItemCodec<T> codec;
    private final List<Entry<T>> entries;
    private final Map<String, String> notes;
    private final Pending<T> pending;
    private final String hint;

    public ItemPickMenu(Player player, String title, List<T> pool, HandItemCodec<T> codec,
                        AskOptions options, Pending<T> pending) {
        super(player, title, rowsFor(countEntries(pool, codec, options).size()));
        this.codec = codec;
        this.entries = countEntries(pool, codec, options);
        this.notes = options == null ? Map.of() : options.notes();
        this.pending = pending;
        this.hint = options == null ? null : options.hint();
    }

    private static <E> List<Entry<E>> countEntries(List<E> pool, HandItemCodec<E> codec,
                                                   AskOptions options) {
        if (options != null && options.groupMode() == AskOptions.GroupMode.BY_KEY) {
            Map<String, Entry<E>> merged = new LinkedHashMap<>();
            for (E item : pool) {
                if (item == null) {
                    continue;
                }
                String key = codec.key(item);
                Entry<E> existing = merged.get(key);
                merged.put(key, existing == null
                        ? new Entry<>(item, 1)
                        : new Entry<>(existing.representative(), existing.count() + 1));
            }
            return new ArrayList<>(merged.values());
        }
        List<Entry<E>> single = new ArrayList<>();
        for (E item : pool) {
            if (item != null) {
                single.add(new Entry<>(item, 1));
            }
        }
        return single;
    }

    static int rowsFor(int size) {
        return Math.clamp((Math.max(9, size) + 8) / 9, 1, 6);
    }

    @Override
    protected String getTitle() {
        return "<gold>选择一张";
    }

    @Override
    protected void setup() {
        for (int i = 0; i < entries.size() && i < 54; i++) {
            Entry<T> entry = entries.get(i);
            List<String> lore = new ArrayList<>();
            String note = notes.get(codec.key(entry.representative()));
            if (note != null) {
                lore.add(note);
            }
            if (entry.count() > 1) {
                lore.add("<white>共 " + entry.count() + " 张");
            }
            ItemStack item = codec.render(entry.representative(), lore);
            setSlot(i, item, (clicker, event) -> {
                markCompleted();
                player.player().closeInventory();
                pending.complete(entry.representative());
            });
        }
        if (hint != null && !hint.isBlank()) {
            int slot = Math.min(53, getInventory().getSize() - 1);
            if (getInventory().getItem(slot) == null) {
                setSlot(slot, button(Material.PAPER, "<aqua>" + hint, List.of()), null);
            }
        }
        fillEmpty(filler());
    }

    @Override
    public void handleClose() {
        if (!completed) {
            pending.cancelNow();
        }
    }
}
