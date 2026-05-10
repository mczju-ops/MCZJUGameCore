package com.github.mczjuops.mczjugamecore.menu;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.GameMeta;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Range;

import java.util.*;

public class MainMenu extends Menu {

    // 用于决定小游戏的布局
    private static final int COLUMNS = 9;
    private static final int CENTER_COLUMN = 4;
    private static final int[] CONTENT_ROWS = {1, 2, 3};

    public MainMenu(Player player) {
        super(player);
    }

    @Override
    protected void setup() {

        ItemStack background = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE);
        background.editMeta(itemMeta -> itemMeta.setHideTooltip(true));

        for (int i = 0; i < 9; i++) setSlot(i, background);
        for (int i = inventory.getSize() - 1; i > inventory.getSize() - 10; i--) setSlot(i, background);

        setSlot(
                4,
                ItemBuilder.of(Material.NETHER_STAR)
                        .customName("<gold><b>小游戏一览")
                        .lore(List.of(
                                "<yellow>欢迎来到MCZJU小游戏世界",
                                "<yellow>你可以在此菜单浏览、加入小游戏"
                        ))
                        .build()
        );

        var gameMetas = MCZJUGameCore.getGameManager().getGameMetas();
        Map<Integer, String> arrangedIds = arrange(gameMetas.keySet());

        for (var entry : arrangedIds.entrySet()) {
            String gameId = arrangedIds.get(entry.getKey());
            GameMeta meta = gameMetas.get(gameId);
            List<String> lore = new ArrayList<>(meta.description());
            lore.add("");
            lore.add("<yellow>作者：<white>%s".formatted(meta.author()));
            lore.add("");
            lore.add("<yellow><b>▶ 点击游玩");

            setSlot(
                    entry.getKey(),
                    ItemBuilder.of(meta.icon())
                            .customName(meta.displayName())
                            .lore(lore)
                            .build(),
                    (player, event) -> {
                        player.player().closeInventory();
                        MCZJUGameCore.getGameManager().joinGame(player, gameId);
                    }
            );
        }

        setSlot(
                inventory.getSize() - 1,
                ItemBuilder.of(Material.PALE_OAK_DOOR)
                        .customName("<gold>返回主服")
                        .lore(List.of(
                                "<yellow>点击返回MCZJU主服"
                        ))
                        .build(),
                (player, event) -> {
                    String mainServer = MCZJUGameCore.getConfigManager().getMainServer();

                    ByteArrayDataOutput out = ByteStreams.newDataOutput();
                    out.writeUTF("Connect");
                    out.writeUTF(mainServer);
                    player.player().sendPluginMessage(MCZJUGameCore.getInstance(), "BungeeCord", out.toByteArray());
                }
        );
    }

    @Override
    protected String getTitle() {
        return "小游戏世界";
    }

    @Override
    protected @Range(from = 1, to = 6) int getRows() {
        return 5;
    }

    @Override
    protected String getPermission() {
        return "mgc.mgc";
    }

    /**
     * 根据小游戏数量返回应该使用的 slot 列表
     *
     * @param gameCount 小游戏数量，范围 0 ~ 27，超过时须重新设计
     * @return slot 列表，顺序为从上到下、从左到右
     */
    public static List<Integer> slotsFor(int gameCount) {
        if (gameCount > 27) {
            throw new IllegalArgumentException("gameCount cannot be greater than 27.");
        }

        int[] rowCounts = rowCounts(gameCount);

        List<Integer> result = new ArrayList<>(gameCount);

        for (int i = 0; i < CONTENT_ROWS.length; i++) {
            int inventoryRow = CONTENT_ROWS[i];
            int countInRow = rowCounts[i];

            for (int column : centeredColumns(countInRow)) {
                result.add(inventoryRow * COLUMNS + column);
            }
        }

        return result;
    }

    /** 把小游戏集合自动映射到 slot */
    public static <T> Map<Integer, T> arrange(Collection<T> games) {
        List<T> list = new ArrayList<>(games);
        List<Integer> slots = slotsFor(list.size());

        Map<Integer, T> result = new LinkedHashMap<>();

        for (int i = 0; i < list.size(); i++) {
            result.put(slots.get(i), list.get(i));
        }

        return result;
    }

    /**
     * 计算三行分别放多少个。
     * gameCount <= 9:
     *   只使用中间行
     * gameCount > 9:
     *   使用三行
     *   上下行数量相等
     *   中间行优先不少于上下行
     */
    private static int[] rowCounts(int gameCount) {
        if (gameCount == 0) {
            return new int[]{0, 0, 0};
        }

        if (gameCount <= 9) {
            return new int[]{0, gameCount, 0};
        }

        int bestSide = -1;
        int bestMiddle = -1;
        int bestScore = Integer.MAX_VALUE;

        for (int side = 0; side <= 9; side++) {
            int middle = gameCount - side * 2;

            if (middle < 0 || middle > 9) {
                continue;
            }

            int difference = Math.abs(middle - side);

            /*
             * 优先让中间行 >= 上下行。
             * 如果做不到，比如 gameCount = 26，就允许中间行少一点。
             */
            int centerPenalty = middle >= side ? 0 : 1000;

            int score = centerPenalty + difference;

            if (score < bestScore) {
                bestScore = score;
                bestSide = side;
                bestMiddle = middle;
            }
        }

        if (bestSide == -1) {
            throw new IllegalStateException("Cannot calculate row layout for gameCount=" + gameCount);
        }

        return new int[]{bestSide, bestMiddle, bestSide};
    }

    /**
     * 计算一行里应该使用哪些列
     * 例如：
     * 1 -> [4]
     * 2 -> [3, 5]
     * 3 -> [3, 4, 5]
     * 4 -> [2, 3, 5, 6]
     * 9 -> [0, 1, 2, 3, 4, 5, 6, 7, 8]
     */
    private static List<Integer> centeredColumns(int count) {
        if (count < 0 || count > 9) {
            throw new IllegalArgumentException("count must be between 0 and 9");
        }

        List<Integer> columns = new ArrayList<>(count);

        if (count == 0) {
            return columns;
        }

        if (count % 2 == 1) {
            int start = CENTER_COLUMN - count / 2;

            for (int column = start; column < start + count; column++) {
                columns.add(column);
            }
        } else {
            int half = count / 2;

            for (int column = CENTER_COLUMN - half; column < CENTER_COLUMN; column++) {
                columns.add(column);
            }

            for (int column = CENTER_COLUMN + 1; column <= CENTER_COLUMN + half; column++) {
                columns.add(column);
            }
        }

        return columns;
    }
}
