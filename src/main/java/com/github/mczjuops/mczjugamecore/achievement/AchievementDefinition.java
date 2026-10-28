package com.github.mczjuops.mczjugamecore.achievement;

import com.github.mczjuops.mczjugamecore.statistics.GameStatistic;
import org.bukkit.Material;
import java.util.Objects;

/**
 * 不可变成就定义。statistic 非 null 时进度来自统计，否则由子插件更新。
 * parent 仅表示原生成就树的父节点，不是解锁前置条件；复杂前置条件由子插件判断。
 * 名称和说明支持 MiniMessage，统一用于菜单、聊天和原生进度；icon 用于菜单及原生弹窗。目标/条件语义变化时应使用新 ID。
 */
public record AchievementDefinition(AchievementKey key, String name, String description,
                                    Material icon, long target, GameStatistic statistic,
                                    AchievementKey parent) {
    /** 校验定义；父节点须属于同一游戏，目标必须大于零。 */
    public AchievementDefinition {
        Objects.requireNonNull(key);
        Objects.requireNonNull(name);
        Objects.requireNonNull(description);
        Objects.requireNonNull(icon);
        if (name.isBlank() || !icon.isItem() || icon.isAir() || target <= 0
                || (parent != null && (!parent.gameId().equals(key.gameId()) || parent.equals(key)))) {
            throw new IllegalArgumentException("Invalid achievement definition");
        }
    }
    /** 创建手动解锁的一次性挑战，目标为 1。 */
    public static AchievementDefinition challenge(AchievementKey key, String name, String description, Material icon) {
        return counter(key, name, description, icon, 1);
    }
    /** 创建独立累计进度成就；target 必须为正。 */
    public static AchievementDefinition counter(AchievementKey key, String name, String description, Material icon, long target) {
        return new AchievementDefinition(key, name, description, icon, target, null, null);
    }
    /** 创建绑定统计的成就，达到 target 自动解锁；不允许直接修改其成就进度。 */
    public static AchievementDefinition statistic(AchievementKey key, String name, String description,
                                                Material icon, GameStatistic statistic, long target) {
        return new AchievementDefinition(key, name, description, icon, target, Objects.requireNonNull(statistic), null);
    }
    /** @return 带指定展示父节点的新定义；注册时检查缺失节点和循环 */
    public AchievementDefinition withParent(AchievementKey parent) {
        return new AchievementDefinition(key, name, description, icon, target, statistic, Objects.requireNonNull(parent));
    }
}
