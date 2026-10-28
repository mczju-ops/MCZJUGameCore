package com.github.mczjuops.mczjugamecore.statistics;

import org.bukkit.Material;

/** 按游戏及 UUID 持久化的累计统计；游玩秒数按 20 个有效游戏 tick 计算。 */
public enum GameStatistic {
    PLAY_TIME_SECONDS("游玩时长", Material.CLOCK),
    KILLS("有效击杀", Material.IRON_SWORD),
    DEATHS("死亡次数", Material.SKELETON_SKULL),
    GAMES_PLAYED("参赛次数", Material.COMPASS),
    WINS("胜利次数", Material.GOLD_INGOT),
    LOSSES("失败次数", Material.REDSTONE),
    DRAWS("平局次数", Material.WHITE_BANNER);

    private final String displayName;
    private final Material icon;
    GameStatistic(String displayName, Material icon) { this.displayName = displayName; this.icon = icon; }
    /** @return 菜单中的中文名称 */
    public String displayName() { return displayName; }
    /** @return 该统计在查询菜单中使用的物品图标 */
    public Material icon() { return icon; }
}
