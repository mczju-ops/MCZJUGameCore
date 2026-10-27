package com.github.mczjuops.mczjugamecore.game.kit.menu;

import com.github.mczjuops.mczjugamecore.menu.Menu;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 游戏内菜单基类：固定标题与行数，统一跳过权限检查。
 *
 * <p>{@link Menu} 在构造期间就会调用 {@code getRows()}，此时子类字段尚未赋值，
 * 因此行数通过 {@code args} 传入。
 */
public abstract class KitMenu extends Menu {

    protected boolean completed;

    protected KitMenu(Player player, String title, int rows) {
        super(player, new Object[]{Math.clamp(rows, 1, 6)}, title);
    }

    @Override
    protected int getRows() {
        return (int) args[0];
    }

    @Override
    protected String getPermission() {
        return "";
    }

    /** 以「跳过权限检查」的方式打开（游戏内必经流程）。 */
    public void show() {
        openUnchecked();
    }

    /** 刷新界面内容。 */
    public void redraw() {
        refresh();
    }

    /** 占位填充，避免空槽看起来像可点击。 */
    protected void fillEmpty(ItemStack filler) {
        for (int i = 0; i < getInventory().getSize(); i++) {
            if (getInventory().getItem(i) == null) {
                setSlot(i, filler);
            }
        }
    }

    protected static ItemStack filler() {
        return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .customName("<dark_gray>·")
                .build();
    }

    /**
     * 不可选条目的占位物。
     *
     * <p>与 {@link #filler()} 的区别：这个表示「有这一项，但你现在不能用」，
     * 用于日麻振听时的「荣和」—— 玩家必须看得见它、并知道为什么点不了。
     */
    protected static ItemStack disabledHolder() {
        return ItemBuilder.of(Material.BARRIER)
                .customName("<dark_gray><strikethrough>不可选择")
                .build();
    }

    protected static ItemStack button(Material material, String name, List<String> lore) {
        return ItemBuilder.of(material).customName(name).lore(lore).build();
    }

    /** 当前打开的菜单是否属于该玩家。 */
    public boolean isViewer(Player player) {
        return this.player.player().getUniqueId().equals(player.getUniqueId());
    }

    /**
     * 标记为「已正常提交」，用于区分「玩家点按钮完成」与「玩家按 Esc 关闭」。
     */
    protected void markCompleted() {
        this.completed = true;
    }
}
