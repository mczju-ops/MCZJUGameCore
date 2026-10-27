package com.github.mczjuops.mczjugamecore.game.kit.menu;

import com.github.mczjuops.mczjugamecore.game.kit.prompt.Pending;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** 是 / 否 确认菜单。 */
public final class ConfirmMenu extends KitMenu {

    private final String title;
    private final String description;
    /** 中间的说明行（lore），用于把「已经抽到了什么」这类信息摆到确认界面上。 */
    private final List<String> details;
    private final String yesLabel;
    private final String noLabel;
    private final Pending<Boolean> pending;

    public ConfirmMenu(Player player, String title, String description,
                       String yesLabel, String noLabel, Pending<Boolean> pending) {
        this(player, title, description, List.of(), yesLabel, noLabel, pending);
    }

    public ConfirmMenu(Player player, String title, String description, List<String> details,
                       String yesLabel, String noLabel, Pending<Boolean> pending) {
        super(player, title, 3);
        this.title = title;
        this.description = description;
        this.details = details == null ? List.of() : List.copyOf(details);
        this.yesLabel = yesLabel;
        this.noLabel = noLabel;
        this.pending = pending;
    }

    @Override
    protected String getTitle() {
        return title;
    }

    @Override
    protected void setup() {
        setSlot(13, button(Material.PAPER, "<aqua>" + (description == null ? "" : description), details), null);
        setSlot(11, button(Material.LIME_CONCRETE, "<green>" + yesLabel, List.of("<gray>点击确认")),
                (clicker, event) -> {
                    markCompleted();
                    player.player().closeInventory();
                    pending.complete(Boolean.TRUE);
                });
        setSlot(15, button(Material.RED_CONCRETE, "<red>" + noLabel, List.of("<gray>点击取消")),
                (clicker, event) -> {
                    markCompleted();
                    player.player().closeInventory();
                    pending.complete(Boolean.FALSE);
                });
        fillEmpty(filler());
    }

    @Override
    public void handleClose() {
        if (!completed) {
            pending.cancelNow();
        }
    }
}
