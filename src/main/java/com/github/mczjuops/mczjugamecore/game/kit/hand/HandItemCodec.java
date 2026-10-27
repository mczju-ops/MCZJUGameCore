package com.github.mczjuops.mczjugamecore.game.kit.hand;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 游戏物品与 {@link ItemStack} 之间的转换。
 *
 * @param <T> 游戏物品类型（SoulCard 是 Card，日麻是 Tile）
 */
public interface HandItemCodec<T> {

    /** 渲染成玩家背包里的物品。 */
    ItemStack render(T item);

    /** 渲染，并附加若干行说明（MiniMessage）。默认忽略说明。 */
    default ItemStack render(T item, List<String> extraLore) {
        return render(item);
    }

    /**
     * 从背包物品反解。
     *
     * @return 不是本游戏的物品时返回 {@code null}
     */
    T read(ItemStack stack);

    /**
     * 聚合用的键：同一种牌返回相同值。
     *
     * <p>**必须与「能不能凑成一组」一致**：日麻的赤 5 万和普通 5 万可以一起碰、一起暗杠，
     * 所以是同一个 key，赤只是个体属性。做成不同的 key 会导致「凑不出碰」的错判。
     */
    String key(T item);

    /** 日志 / 提示里显示的名字。 */
    String label(T item);
}
