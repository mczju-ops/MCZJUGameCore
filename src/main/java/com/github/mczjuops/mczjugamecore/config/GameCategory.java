package com.github.mczjuops.mczjugamecore.config;

import org.bukkit.Material;

import java.util.List;

/**
 * 配置中的游戏类别展示信息，名称和描述均支持 MiniMessage。
 * @param name 展示名称
 * @param lore 逐行展示的描述；构造时复制，避免外部修改
 * @param icon 类别图标；null、空气或非物品材质回退为箱子
 */
public record GameCategory(String name, List<String> lore, Material icon) {
    /**
     * 创建使用默认箱子图标的类别，名称和描述支持 MiniMessage。
     * @param name 展示名称
     * @param lore 逐行描述，构造时复制
     */
    public GameCategory(String name, List<String> lore) {
        this(name, lore, Material.CHEST);
    }

    /** 创建不可变的类别展示信息，并为无效图标使用默认箱子。 */
    public GameCategory {
        lore = List.copyOf(lore);
        if (icon == null || icon.isAir() || !icon.isItem()) icon = Material.CHEST;
    }
}
