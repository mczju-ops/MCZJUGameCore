package com.github.mczjuops.mczjugamecore.menu;

import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class AlertMenu extends Menu {

    private final Runnable onConfirm;

    public AlertMenu(Player player, Runnable onConfirm) {
        super(AlertMenu.class, player);
        this.onConfirm = onConfirm;
    }

    @Override
    public void setup() {
        setSlot(
                11,
                ItemBuilder.of(Material.GREEN_WOOL)
                        .customName("<green>确认")
                        .build(),
                (player, _) -> {
                    onConfirm.run();
                    player.player().closeInventory();
                }
        );

        setSlot(
                15,
                ItemBuilder.of(Material.RED_WOOL)
                        .customName("<red>取消")
                        .build(),
                (player, _) -> player.player().closeInventory()
        );
    }
}
