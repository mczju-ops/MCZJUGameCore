package com.github.mczjuops.mczjugamecore.menu;

import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class AlertMenu extends Menu {

    private final Runnable onConfirm;

    public AlertMenu(Player player, Runnable onConfirm) {
        super(player);
        this.onConfirm = onConfirm;
    }

    @Override
    public String getTitle() {
        return "确认操作";
    }

    @Override
    public int getRows() {
        return 3;
    }

    @Override
    public String getPermission() {
        return "menu.default";
    }

    @Override
    public void setup() {
        setSlot(
                11,
                ItemBuilder.of(Material.GREEN_WOOL)
                        .customName("<green>确认")
                        .build(),
                (player, event) -> {
                    onConfirm.run();
                    player.player().closeInventory();
                }
        );

        setSlot(
                15,
                ItemBuilder.of(Material.RED_WOOL)
                        .customName("<red>取消")
                        .build(),
                (player, event) -> player.player().closeInventory()
        );
    }
}
