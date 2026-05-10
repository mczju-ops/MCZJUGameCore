package com.github.mczjuops.mczjugamecore.profile;

import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Arrays;

/** Bukkit Player 和 {@link PlayerSnapshot} 的桥接层，负责实际的物品栏读写 */
public class ProfileCapture {

    private final ConsoleSender logger = new ConsoleSender("MGC: %s".formatted(getClass().getSimpleName()));

    /**
     * 从玩家当前状态生成不可变的 {@link PlayerSnapshot}。
     * 调用时机：profile 切换前、玩家下线前。
     * 返回结果随后存入 {@link ProfileData#putProfile}。
     */
    public PlayerSnapshot capture(Player player) {
        PlayerInventory inv = player.getInventory();

        return new PlayerSnapshot(
                serializeItems(inv.getStorageContents()), // 36 格主栏
                serializeItems(inv.getArmorContents()), // 4 格盔甲
                serializeItems(new ItemStack[]{ inv.getItemInOffHand() }), // 副手
                serializeItems(player.getEnderChest().getContents()), // 末影箱
                player.getLevel(),
                player.getExp()
        );
    }

    /**
     * 将 {@link PlayerSnapshot} 的数据应用到玩家身上。
     *
     * <ul>
     *   <li>调用前会自动 {@code closeInventory()}</li>
     *   <li>{@code snapshot} 为 {@code null} 时，等同于全清空
     *       （适用于玩家首次进入某 profile 的情形）。</li>
     * </ul>
     *
     * @param snapshot 目标快照，null 表示清空所有物品和 XP
     */
    public void apply(Player player, PlayerSnapshot snapshot) {
        // 关闭所有打开的 GUI（含末影箱 GUI），防止 UI 与数据不同步
        player.closeInventory();

        PlayerInventory inv = player.getInventory();

        if (snapshot == null) {
            inv.setStorageContents(new ItemStack[36]);
            inv.setArmorContents(new ItemStack[4]);
            inv.setItemInOffHand(null);
            player.getEnderChest().setContents(new ItemStack[27]);
            player.setLevel(0);
            player.setExp(0f);
            return;
        }

        inv.setStorageContents(deserializeItems(snapshot.mainContents(),  36));
        inv.setArmorContents(  deserializeItems(snapshot.armorContents(), 4));

        ItemStack[] offhandArr = deserializeItems(snapshot.offhand(), 1);
        inv.setItemInOffHand(offhandArr[0]); // null 槽位 = 空手，Bukkit 接受

        player.getEnderChest().setContents(
                deserializeItems(snapshot.enderChest(), 27));

        player.setLevel(snapshot.xpLevel());
        player.setExp(snapshot.xpProgress());
    }

    /**
     * ItemStack[] → byte[]（调用 Paper 原生 NBT 序列化）。
     * 数组为空或 null 时返回空 byte[]（存储层视为"无数据"）。
     */
    private static byte[] serializeItems(ItemStack[] items) {
        if (items == null || items.length == 0) return new byte[0];
        return ItemStack.serializeItemsAsBytes(items);
    }

    /**
     * byte[] → ItemStack[]（调用 Paper 原生 NBT 反序列化）。
     *
     * <ul>
     *   <li>data 为空时返回全 null 数组（等同于空物品栏）。</li>
     *   <li>长度不匹配时：截断（过长）或补 null（过短），并记录警告。</li>
     *   <li>反序列化抛出异常时降级为空数组，不向外传播。</li>
     * </ul>
     *
     * @param expectedSize 该物品栏分区的标准槽位数（36 / 4 / 27 / 1）
     */
    private ItemStack[] deserializeItems(byte[] data, int expectedSize) {
        if (data == null || data.length == 0) return new ItemStack[expectedSize];
        try {
            ItemStack[] result = ItemStack.deserializeItemsFromBytes(data);
            if (result.length == expectedSize) return result;

            logger.warn("槽位不匹配，期望：%s，实际：%s，已自动调整".formatted(expectedSize, result.length));
            return Arrays.copyOf(result, expectedSize);
        } catch (Exception e) {
            logger.warn("物品反序列化失败，槽位将清空：%s".formatted(e));
            return new ItemStack[expectedSize];
        }
    }
}