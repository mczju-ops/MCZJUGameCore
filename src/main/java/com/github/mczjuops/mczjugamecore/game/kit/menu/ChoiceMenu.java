package com.github.mczjuops.mczjugamecore.game.kit.menu;

import com.github.mczjuops.mczjugamecore.game.kit.prompt.Pending;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 通用选项菜单：把若干选项渲染成按钮，点击后把 {@code value} 交付给 {@link Pending}。
 *
 * <p>按 {@link ChoiceOption#order()} 升序排列（稳定排序，同权重保持传入顺序）。
 * {@code enabled == false} 的选项**仍然显示**，但用屏障图标且点击无效 ——
 * 玩家必须能看见「我本来可以荣和，但是振听」。
 */
public final class ChoiceMenu extends KitMenu {

    private final List<ChoiceOption> options;
    private final Pending<String> pending;
    private final String hint;

    public ChoiceMenu(Player player, String title, List<ChoiceOption> options,
                      String hint, Pending<String> pending) {
        super(player, title, rowsFor(options.size()));
        this.options = sorted(options);
        this.pending = pending;
        this.hint = hint;
    }

    /** 按 {@code order} 升序稳定排序：同权重保持传入顺序（日麻要把「荣」排在「碰」前面）。 */
    static List<ChoiceOption> sorted(List<ChoiceOption> options) {
        List<ChoiceOption> copy = new ArrayList<>(options);
        copy.sort(Comparator.comparingInt(ChoiceOption::order));
        return copy;
    }

    static int rowsFor(int size) {
        return Math.clamp((Math.max(9, size) + 8) / 9, 1, 6);
    }

    @Override
    protected String getTitle() {
        return "<gold>请选择";
    }

    @Override
    protected void setup() {
        for (int i = 0; i < options.size() && i < 54; i++) {
            ChoiceOption option = options.get(i);
            if (!option.enabled()) {
                ItemStack blocked = button(Material.BARRIER, "<dark_gray>" + option.label(),
                        blockedLore(option));
                setSlot(i, blocked, null);
                continue;
            }
            ItemStack item = button(option.icon(), "<yellow>" + option.label(),
                    option.description() == null
                            ? List.of("<gray>点击选择")
                            : List.of("<gray>" + option.description()));
            setSlot(i, item, (clicker, event) -> {
                markCompleted();
                player.player().closeInventory();
                pending.complete(option.value());
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

    private static List<String> blockedLore(ChoiceOption option) {
        List<String> lore = new ArrayList<>();
        if (option.description() != null) {
            lore.add("<dark_gray>" + option.description());
        }
        lore.add("<red>当前无法选择");
        return lore;
    }

    @Override
    public void handleClose() {
        if (!completed) {
            pending.cancelNow();
        }
    }
}
