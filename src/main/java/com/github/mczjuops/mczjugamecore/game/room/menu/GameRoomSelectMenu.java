package com.github.mczjuops.mczjugamecore.game.room.menu;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.GameMeta;
import com.github.mczjuops.mczjugamecore.game.GameState;
import com.github.mczjuops.mczjugamecore.game.MidGameJoinable;
import com.github.mczjuops.mczjugamecore.game.room.AbstractGameRoom;
import com.github.mczjuops.mczjugamecore.menu.MainMenu;
import com.github.mczjuops.mczjugamecore.menu.Menu;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Range;

import java.lang.reflect.Field;
import java.util.*;

public class GameRoomSelectMenu extends Menu {

    private final String gameId;
    private final GameMeta gameMeta;

    // 用于决定房间图标的布局
    private static final int COLUMNS = 9;
    private static final int CENTER_COLUMN = 4;
    private static final int[] CONTENT_ROWS = {1, 2, 3};

    public GameRoomSelectMenu(Player player, String gameId, GameMeta gameMeta, Object... args) {
        super(player, args);
        this.gameId = gameId;
        this.gameMeta = gameMeta;
    }

    @Override
    protected void setup() {
        ItemStack background = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE);
        background.editMeta(itemMeta -> itemMeta.setHideTooltip(true));

        for (int i = 0; i < 9; i++) setSlot(i, background);
        for (int i = inventory.getSize() - 1; i > inventory.getSize() - 10; i--) setSlot(i, background);

        List<String> titleLore = new ArrayList<>(gameMeta.description());
        titleLore.add("");
        titleLore.add("<yellow>作者：<white>%s".formatted(gameMeta.author()));
        setSlot(
                4,
                ItemBuilder.of(Material.CLOCK)
                        .customName(gameMeta.displayName())
                        .lore(titleLore)
                        .itemModel(gameMeta.icon())
                        .build()
        );

        var rooms = MCZJUGameCore.getGameRoomManager().getGameRooms(gameId);
        if (rooms != null && !rooms.isEmpty()) {
            // 将所欲房间展开到菜单
            Map<Integer, AbstractGameRoom> arrangedRooms = arrange(rooms);

            for (var entry : arrangedRooms.entrySet()) {
                var room = entry.getValue();

                String roomDisplayName = "<gray>未命名房间";
                Material roomIcon = Material.REDSTONE_BLOCK;
                String roomDescription = "";

                try {
                    Field field = room.getClass().getField("displayName");
                    if (field.getType() == String.class) {
                        Object value = field.get(room);
                        if (value != null) roomDisplayName = (String) value;
                    }
                } catch (NoSuchFieldException | IllegalAccessException ignored) {}

                try {
                    Field field = room.getClass().getField("icon");
                    if (field.getType() == String.class) {
                        Object value = field.get(room);
                        String iconStr = (String) value;
                        if (iconStr != null) {
                            Material icon = Material.matchMaterial(iconStr);
                            if (icon != null) roomIcon = icon;
                        }
                    }
                } catch (NoSuchFieldException | IllegalAccessException ignored) {}

                try {
                    Field field = room.getClass().getField("description");
                    if (field.getType() == String.class) {
                        Object value = field.get(room);
                        if (value != null) roomDescription = (String) value;
                    }
                } catch (NoSuchFieldException | IllegalAccessException ignored) {}

                String roomName = room.getRoomName();
                var game = MCZJUGameCore.getGameManager().getGame(gameId, roomName);
                String gameState = "<green>空闲"; // 不存在该游戏实例
                if (game != null) {
                    if (game.getState() == GameState.WAITING) {
                        gameState = "<dark_green>等待更多玩家";
                    } else if (game instanceof MidGameJoinable) {
                        gameState = "<aqua>可中途加入";
                    } else {
                        gameState = "<red>已开始";
                    }
                }

                List<String> lore = new ArrayList<>();
                if (!roomDescription.isBlank()) lore.add(roomDescription);
                lore.add("");
                lore.add("<yellow>当前状态：" + gameState);

                if (game != null) {
                    lore.add("<yellow>玩家：");
                    var players = game.getPlayers();
                    players.forEach(player -> lore.add(player.getDisplayName()));
                } else {
                    lore.add("<yellow>玩家：<gray>无");
                }

                lore.add("");
                lore.add("<yellow><b>点击加入该房间");

                setSlot(
                        entry.getKey(),
                        ItemBuilder.of(Material.CLOCK)
                                .customName(roomDisplayName)
                                .lore(lore)
                                .itemModel(roomIcon)
                                .glint(!gameState.equals("<red>已开始"))
                                .build(),
                        (player, event) -> {
                            // 注意玩家打开菜单期间，房间状态可能发生变化
                            player.player().closeInventory();
                            MCZJUGameCore.getGameManager().joinGame(player, gameId, roomName);
                        }
                );
            }
        }

        setSlot(
                inventory.getSize() - 5,
                ItemBuilder.of(Material.ARROW)
                        .customName("<yellow>返回主菜单")
                        .build(),
                (player, event) -> new MainMenu(player.player()).open()
        );
    }

    @Override
    protected String getTitle() {
        return "选择一个游戏房间";
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
     * 根据房间数量返回应该使用的 slot 列表
     *
     * @param roomCount 房间数量，范围 0 ~ 27，超过时须重新设计
     * @return slot 列表，顺序为从上到下、从左到右
     */
    public static List<Integer> slotsFor(int roomCount) {
        if (roomCount > 27) {
            throw new IllegalArgumentException("roomCount cannot be greater than 27.");
        }

        int[] rowCounts = rowCounts(roomCount);

        List<Integer> result = new ArrayList<>(roomCount);

        for (int i = 0; i < CONTENT_ROWS.length; i++) {
            int inventoryRow = CONTENT_ROWS[i];
            int countInRow = rowCounts[i];

            for (int column : centeredColumns(countInRow)) {
                result.add(inventoryRow * COLUMNS + column);
            }
        }

        return result;
    }

    /** 把房间集合自动映射到 slot */
    public static <T> Map<Integer, T> arrange(Collection<T> rooms) {
        List<T> list = new ArrayList<>(rooms);
        List<Integer> slots = slotsFor(list.size());

        Map<Integer, T> result = new LinkedHashMap<>();

        for (int i = 0; i < list.size(); i++) {
            result.put(slots.get(i), list.get(i));
        }

        return result;
    }

    /**
     * 计算三行分别放多少个。
     * roomCount <= 9:
     *   只使用中间行
     * roomCount > 9:
     *   使用三行
     *   上下行数量相等
     *   中间行优先不少于上下行
     */
    private static int[] rowCounts(int roomCount) {
        if (roomCount == 0) {
            return new int[]{0, 0, 0};
        }

        if (roomCount <= 9) {
            return new int[]{0, roomCount, 0};
        }

        int bestSide = -1;
        int bestMiddle = -1;
        int bestScore = Integer.MAX_VALUE;

        for (int side = 0; side <= 9; side++) {
            int middle = roomCount - side * 2;

            if (middle < 0 || middle > 9) {
                continue;
            }

            int difference = Math.abs(middle - side);

            /*
             * 优先让中间行 >= 上下行。
             * 如果做不到，比如 roomCount = 26，就允许中间行少一点。
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
            throw new IllegalStateException("Cannot calculate row layout for roomCount=" + roomCount);
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
