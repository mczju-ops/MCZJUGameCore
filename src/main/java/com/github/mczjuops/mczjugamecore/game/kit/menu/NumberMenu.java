package com.github.mczjuops.mczjugamecore.game.kit.menu;

import com.github.mczjuops.mczjugamecore.game.kit.prompt.Pending;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** 数字选择菜单（例如「摸几张」「放在第几位」）。 */
public final class NumberMenu extends KitMenu {

    private final String title;
    private final int min;
    private final int max;
    private final String hint;
    private final Pending<Integer> pending;

    public NumberMenu(Player player, String title, int min, int max, String hint, Pending<Integer> pending) {
        super(player, title, rowsFor(min, max));
        this.title = title;
        this.min = min;
        this.max = max;
        this.hint = hint;
        this.pending = pending;
    }

    static int rowsFor(int min, int max) {
        int count = Math.max(1, max - min + 1);
        return Math.clamp((Math.max(9, count) + 8) / 9, 1, 6);
    }

    @Override
    protected String getTitle() {
        return title;
    }

    @Override
    protected void setup() {
        int index = 0;
        for (int value = min; value <= max && index < 54; value++, index++) {
            int chosen = value;
            setSlot(index, button(Material.PAPER, "<yellow>" + chosen,
                            hint == null ? List.of("<gray>点击选择") : List.of("<gray>" + hint)),
                    (clicker, event) -> {
                        markCompleted();
                        player.player().closeInventory();
                        pending.complete(chosen);
                    });
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
