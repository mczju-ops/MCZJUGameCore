package com.github.mczjuops.mczjugamecore.game.kit.menu;

import com.github.mczjuops.mczjugamecore.game.kit.prompt.Pending;
import com.github.mczjuops.mczjugamecore.game.kit.seat.Seat;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

/**
 * 选人菜单：列出候选玩家的头颅，点击即选中。
 *
 * <p>菜单关闭即视为取消（{@link Pending#cancelNow()}）；超时由 {@link Pending} 自己的计时器负责，
 * 菜单本身不带第二套超时，避免「谁先超时」的歧义。
 */
public final class SeatPickMenu extends KitMenu {

    private final List<Seat> candidates;
    private final Pending<Seat> pending;
    private final String title;

    public SeatPickMenu(Player viewer, String title, List<Seat> candidates, Pending<Seat> pending) {
        super(viewer, title, rowsFor(candidates.size()));
        this.title = title;
        this.candidates = List.copyOf(candidates);
        this.pending = pending;
    }

    static int rowsFor(int size) {
        return Math.clamp((Math.max(9, size) + 8) / 9, 1, 6);
    }

    @Override
    protected String getTitle() {
        return title;
    }

    @Override
    protected void setup() {
        for (int i = 0; i < candidates.size() && i < 54; i++) {
            Seat seat = candidates.get(i);
            Player target = seat.player();
            ItemStack head = ItemBuilder.of(Material.PLAYER_HEAD)
                    .customName("<yellow>" + seat.name())
                    .lore(List.of("<gray>点击选择该玩家"))
                    .build();
            if (target != null && head.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(target);
                head.setItemMeta(meta);
            }
            setSlot(i, head, (clicker, event) -> {
                markCompleted();
                player.player().closeInventory();
                pending.complete(seat);
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
