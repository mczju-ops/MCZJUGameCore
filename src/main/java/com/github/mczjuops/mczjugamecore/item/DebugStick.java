package com.github.mczjuops.mczjugamecore.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class DebugStick extends MGCItem{

    @Override
    protected ItemStack createItem() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        // 名称
        meta.displayName(Component.text("调试棒", NamedTextColor.GOLD));

        // Lore 描述
        meta.lore(List.of(
                Component.text("用于插件调试的工具", NamedTextColor.AQUA),
                Component.text("有各种神奇的用途", NamedTextColor.LIGHT_PURPLE)
        ));

        item.setItemMeta(meta);
        return item;
    }

    @Override
    public String getId() {
        return MGCMaterial.DEBUG_STICK.toString();
    }

}
