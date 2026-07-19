package com.github.mczjuops.mczjugamecore.item;

import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class DebugStick extends MGCItem{

    @Override
    protected @NonNull ItemStack createRawItem() {

        return ItemBuilder.of(Material.BLAZE_ROD)
                .customName("<gold>调试棒")
                .lore(List.of(
                        "<aqua>用于插件调试的工具",
                        "<gradient:red:gold>有各种神奇的功能"
                ))
                .glint(true)
                .build();
    }

    @Override
    public @NotNull String getId() {
        return MGCMaterial.DEBUG_STICK.toString();
    }
}
