package com.github.mczjuops.mczjugamecore.menu;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.GameMeta;
import com.github.mczjuops.mczjugamecore.game.manager.AbstractGameManager;
import com.github.mczjuops.mczjugamecore.game.room.menu.GameRoomSelectMenu;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Range;

import java.util.*;

public class MainMenu extends Menu {

    private final AbstractGameManager gameManager;

    // 用于决定小游戏的布局
    private static final int COLUMNS = 9;
    private static final int CENTER_COLUMN = 4;
    private static final int[] CONTENT_ROWS = {1, 2, 3};

    public MainMenu(Player player) {
        super(player);
        this.gameManager = MCZJUGameCore.getGameManager();
    }

    @Override
    public void open() {
        super.open();
        player.player().playSound(player.player(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
    }

    @Override
    protected void setup() {

        ItemStack background = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE);
        background.editMeta(itemMeta -> itemMeta.setHideTooltip(true));

        for (int i = 0; i < 9; i++) setSlot(i, background);
        for (int i = inventory.getSize() - 1; i > inventory.getSize() - 10; i--) setSlot(i, background);

        setSlot(
                4,
                ItemBuilder.of(Material.CLOCK)
                        .customName("<#DEB12D>MCZJU<b>小游戏世界")
                        .lore(List.of(
                                "<yellow>欢迎来到小游戏世界",
                                "<yellow>你可以在此菜单浏览、加入小游戏"
                        ))
                        .glint(true)
                        .build()
        );

        var gameMetas = gameManager.getGameMetas();
        Map<Integer, String> arrangedIds = arrange(gameMetas.keySet());

        for (var entry : arrangedIds.entrySet()) {
            String gameId = arrangedIds.get(entry.getKey());
            GameMeta meta = gameMetas.get(gameId);
            boolean playerSelectable = gameManager.playerSelectable(gameId);
            boolean hasLobby = MCZJUGameCore.getLobbyManager().hasLobby(gameId);
            List<String> lore = new ArrayList<>(meta.description());
            lore.add("");
            lore.add("<yellow>作者：<white>%s".formatted(meta.author()));
            lore.add("");
            if (hasLobby && playerSelectable) {
                lore.add("<yellow><b>▶ 左键 进入游戏大厅");
                lore.add("<yellow><b>▶ 右键 选择游戏房间");
            } else if (hasLobby) {
                lore.add("<yellow><b>▶ 点击进入游戏大厅");
            } else if (playerSelectable) {
                lore.add("<yellow><b>▶ 左键 加入游戏");
                lore.add("<yellow><b>▶ 右键 选择房间");
            } else {
                lore.add("<yellow><b>▶ 点击加入游戏");
            }

            setSlot(
                    entry.getKey(),
                    ItemBuilder.of(Material.CLOCK)
                            .customName(meta.displayName())
                            .lore(lore)
                            .itemModel(meta.icon())
                            .build(),
                    (player, event) -> {
                        player.player().closeInventory();
                        if (hasLobby && playerSelectable) {
                            if (event.getClick() == ClickType.LEFT) {
                                teleportToGameLobby(player, gameId);
                            } else if (event.getClick() == ClickType.RIGHT) {
                                new GameRoomSelectMenu(player.player(), gameId, meta).open();
                            }
                        } else if (hasLobby) {
                            teleportToGameLobby(player, gameId);
                        } else if (playerSelectable) {
                            if (event.getClick() == ClickType.LEFT) {
                                gameManager.joinGame(player, gameId); // 直接接入游戏
                            } else if (event.getClick() == ClickType.RIGHT) {
                                // 打开房间选择菜单
                                new GameRoomSelectMenu(player.player(), gameId, meta).open();
                            }
                        } else {
                            gameManager.joinGame(player, gameId); // 直接加入游戏
                        }
                    }
            );
        }

        boolean inGame = player.isInGame();
        // 左下角按钮，传送到大厅出生点
        setSlot(
                inventory.getSize() - 9,
                ItemBuilder.of(Material.COMPASS)
                        .customName("<gold>返回出生点")
                        .lore(List.of(
                                "<gray>传送到小游戏世界的出生点",
                                inGame ? "<red>无法在游戏过程中使用该功能" : "<yellow>点击传送"
                        ))
                        .glint(!inGame)
                        .build(),
                (player, _) -> {
                    player.player().closeInventory();

                    if (inGame) {
                        player.sender().warn("<yellow>无法在游戏过程中使用该功能");
                        return;
                    }

                    Location lobbySpawn = MCZJUGameCore.getLobbyManager().getMainLobby();
                    if (lobbySpawn == null) {
                        player.sender().warn("<yellow>无法识别出生点，请询问管理员");
                    } else {
                        Player p = player.player();
                        p.teleport(lobbySpawn);
                        Bukkit.getScheduler().runTask(
                                MCZJUGameCore.getInstance(),
                                () -> p.playSound(p, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f)
                        );
                    }
                }
        );

        // 右下角，处于游戏内时为退出，处于大厅时为返回主城
        if (inGame) {
            var game = player.getGame();
            assert game != null;
            String gameName = game.getGameMeta().displayName();
            setSlot(
                    inventory.getSize() - 1,
                    ItemBuilder.of(Material.PALE_OAK_DOOR)
                            .customName("<gold>退出当前游戏")
                            .lore(List.of(
                                    "<yellow>点击退出游戏" + gameName
                            ))
                            .glint(true)
                            .build(),
                    (player, _) -> {
                        player.player().closeInventory();
                        player.player().performCommand("mgc leave");
                    }
            );
        } else {
            setSlot(
                    inventory.getSize() - 1,
                    ItemBuilder.of(Material.END_PORTAL_FRAME)
                            .customName("<gold>返回生存世界")
                            .lore(List.of(
                                    "<yellow>点击返回MCZJU生存世界"
                            ))
                            .glint(true)
                            .build(),
                    (player, _) -> new AlertMenu(player.player(), "确认返回生存世界？", () -> {
                        player.player().closeInventory();
                        String mainServerName = MCZJUGameCore.getConfigManager().getMainServer();

                        ByteArrayDataOutput out = ByteStreams.newDataOutput();
                        out.writeUTF("Connect");
                        out.writeUTF(mainServerName);
                        player.player().sendPluginMessage(MCZJUGameCore.getInstance(), "BungeeCord", out.toByteArray());
                    }).open()
            );
        }
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

    private boolean teleportToGameLobby(PlayerExt player, String gameId) {
        if (player.isInGame()) {
            player.sender().warn("无法在游戏过程中进行传送");
            return false;
        }

        Location lobby = MCZJUGameCore.getLobbyManager().getGameLobby(gameId);
        if (lobby == null || lobby.getWorld() == null) {
            player.sender().warn("该小游戏没有大厅");
            return false;
        }

        Player bukkitPlayer = player.player();
        bukkitPlayer.teleport(lobby);
        bukkitPlayer.playSound(bukkitPlayer, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        return true;
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
