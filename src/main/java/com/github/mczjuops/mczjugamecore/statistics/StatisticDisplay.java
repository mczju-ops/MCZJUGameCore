package com.github.mczjuops.mczjugamecore.statistics;

import org.bukkit.Material;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * 自定义统计展示项，不保存数值，也不触发成就。
 * @param key 由已注册游戏 ID 和统计 ID 组成的稳定标识
 * @param name 菜单名称，支持 MiniMessage
 * @param icon 菜单图标，必须是有效物品
 * @param value 打开或刷新所在页时在主线程调用，返回已格式化的 MiniMessage 文本；null 表示暂无数据。
 *              应只读取或计算内存数据，不执行阻塞 I/O
 */
public record StatisticDisplay(StatisticKey key, String name, Material icon, Function<UUID, String> value) {
    /** 校验 ID、名称、图标及取值函数；无效定义抛出 IllegalArgumentException。 */
    public StatisticDisplay {
        Objects.requireNonNull(key);
        Objects.requireNonNull(name);
        Objects.requireNonNull(icon);
        Objects.requireNonNull(value);
        if (name.isBlank() || !icon.isItem() || icon.isAir())
            throw new IllegalArgumentException("Invalid statistic display");
    }
    /**
     * 创建自定义统计展示定义，供统计管理器批量注册。
     * @param key 游戏及自定义统计的稳定标识
     * @param name 支持 MiniMessage 的名称
     * @param icon 菜单物品图标
     * @param value 主线程取值函数，返回格式化文本；null 表示暂无数据
     * @return 不可变展示定义
     */
    public static StatisticDisplay of(StatisticKey key, String name, Material icon,
                                      Function<UUID, String> value) {
        return new StatisticDisplay(key, name, icon, value);
    }
}
