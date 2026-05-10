package com.github.mczjuops.mczjugamecore.profile;

public record PlayerSnapshot(
        byte[] mainContents, // 36 格主物品栏
        byte[] armorContents, // 4 格盔甲
        byte[] offhand, // 1 格副手（序列化为单元素数组）
        byte[] enderChest, // 27 格末影箱
        int xpLevel,
        float xpProgress
) {
    // 全空的快照
    public static PlayerSnapshot empty() {
        return new PlayerSnapshot(
                new byte[0], new byte[0], new byte[0], new byte[0],
                0, 0f
        );
    }
}
