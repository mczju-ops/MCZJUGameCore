package com.github.mczjuops.mczjugamecore.game.kit.menu;

import org.bukkit.Material;

/**
 * 选项菜单里的一项。
 *
 * @param label       按钮标题
 * @param value       选中后交给 {@link com.github.mczjuops.mczjugamecore.game.kit.prompt.Pending} 的值
 * @param description 按钮 lore（可为 {@code null}）
 * @param icon        图标材质
 * @param enabled     是否可选；{@code false} 时仍然显示但点不动（日麻振听时的「荣和」）
 * @param order       排序权重，越小越靠前（日麻要把「荣」排在「碰」前面）
 */
public record ChoiceOption(String label, String value, String description,
                           Material icon, boolean enabled, int order) {

    public ChoiceOption(String label, String value) {
        this(label, value, null, Material.PAPER, true, 0);
    }

    public ChoiceOption(String label, String value, String description) {
        this(label, value, description, Material.PAPER, true, 0);
    }

    public ChoiceOption(String label, String value, String description, Material icon) {
        this(label, value, description, icon, true, 0);
    }

    /** 复制一份，只改「是否可选」。 */
    public ChoiceOption withEnabled(boolean flag) {
        return new ChoiceOption(label, value, description, icon, flag, order);
    }

    /** 复制一份，只改排序权重。 */
    public ChoiceOption withOrder(int weight) {
        return new ChoiceOption(label, value, description, icon, enabled, weight);
    }

    /** 复制一份，只改标题。 */
    public ChoiceOption withLabel(String text) {
        return new ChoiceOption(text, value, description, icon, enabled, order);
    }

    /** 复制一份，只改值。 */
    public ChoiceOption withValue(String text) {
        return new ChoiceOption(label, text, description, icon, enabled, order);
    }
}
