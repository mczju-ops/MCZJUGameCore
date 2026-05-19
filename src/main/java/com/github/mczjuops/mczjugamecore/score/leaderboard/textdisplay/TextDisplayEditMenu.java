package com.github.mczjuops.mczjugamecore.score.leaderboard.textdisplay;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.menu.Menu;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Range;

import java.text.DecimalFormat;
import java.util.List;

public class TextDisplayEditMenu extends Menu {

    private final TextDisplayRecord record;

    public TextDisplayEditMenu(Player player, TextDisplayRecord record) {
        super(player);
        this.record = record;
    }

    @Override
    protected void setup() {
        inventory.clear();

        ItemStack background = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE);
        background.editMeta(itemMeta -> itemMeta.setHideTooltip(true));

        for (int i = 0; i < 9; i++) setSlot(i, background);
        for (int i = inventory.getSize() - 1; i > inventory.getSize() - 10; i--) setSlot(i, background);

        var props = record.getProperties();
        var billboard = props.getBillboard();
        boolean hasBackground = props.hasBackground();
        String currentLocation = record.getLocation() == null ? "<red>无" : "<dark_aqua>" + formatLocation(record.getLocation());

        setSlot(
                4,
                ItemBuilder.of(Material.NAME_TAG)
                        .customName("<green>编辑排行榜展示实体参数")
                        .lore(List.of(
                                "<gray>排行榜ID：<white>%s".formatted(record.getLeaderboardId()),
                                "<gray>展示实体ID：<white>%s".formatted(record.getEntityId())
                        ))
                        .glint(true)
                        .build()
        );

        setSlot(
                20,
                ItemBuilder.of(Material.COMPASS)
                        .customName("<green>设置展示实体位置")
                        .lore(List.of(
                                "<gray>当前设置：" + currentLocation,
                                "<gray>默认情况下，展示实体底部中心点是它的坐标",
                                "",
                                "<yellow>点击编辑",
                                "<gray>注意：如果展示实体已生成，先杀死实体才能更新位置"
                        ))
                        .glint(true)
                        .build(),
                (player, event) -> {
                    player.player().closeInventory();
                    player.selectLocation(location -> {
                        record.setLocation(location);
                        record.setModified(true);
                        reopenLater(player, record.getLeaderboardId(), record.getEntityId());
                    });
                }
        );

        setSlot(
                22,
                ItemBuilder.of(Material.OAK_SIGN)
                        .customName("<green>设置展示实体面朝玩家渲染时的固定轴")
                        .lore(List.of(
                                "<gray>当前设置：<gold>" + billboard.toString().toLowerCase(),
                                "<gray>可用：<yellow>fixed</yellow>或<yellow>vertical</yellow>",
                                "",
                                "<yellow>点击切换<gray>（刷新后生效）"
                        ))
                        .glint(true)
                        .build(),
                (player, event) -> {
                    var newBillboard = billboard == Display.Billboard.FIXED ? Display.Billboard.VERTICAL : Display.Billboard.FIXED;
                    props.setBillboard(newBillboard);
                    record.setModified(true);
                    player.player().playSound(player.player(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    refresh();
                }
        );

        setSlot(
                24,
                ItemBuilder.of(Material.ITEM_FRAME)
                        .customName("<green>设置是否显示半透明背景")
                        .lore(List.of(
                                "<gray>当前设置：" + (hasBackground ? "<green>是" : "<red>否"),
                                "<gray>默认值：<yellow>是",
                                "",
                                "<yellow>点击切换<gray>（刷新后生效）"
                        ))
                        .glint(true)
                        .build(),
                (player, event) -> {
                    props.setHasBackground(!hasBackground);
                    record.setModified(true);
                    player.player().playSound(player.player(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    refresh();
                }
        );

        setSlot(
                inventory.getSize() - 9,
                ItemBuilder.of(Material.EGG)
                        .customName("<green>刷新展示实体")
                        .lore(List.of(
                                "<yellow>点击刷新或生成排行榜%s的所有展示实体".formatted(record.getLeaderboardId()),
                                "<gray>未设置位置的展示实体将无法生成"
                        ))
                        .build(),
                (player, event) -> {
                    MCZJUGameCore.getLeaderboardManager().refresh(record.getLeaderboardId());
                    player.player().playSound(player.player(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    player.sender().success("<green>已尝试刷新所有该排行榜的展示实体");
                }
        );

        setSlot(
                inventory.getSize() - 1,
                ItemBuilder.of(Material.CHEST)
                        .customName("<green>保存数据")
                        .lore(List.of(
                                "<yellow>点击将修改后的数据保存到文件",
                                "",
                                "<gray>说明：",
                                "<gray>此操作的意义是将数据保存到文件（持久化数据）",
                                "<gray>即使不保存到文件，修改结果也会直接生效",
                                "<gray>服务器关闭时会自动保存，但崩溃等异常可能导致数据丢失"
                        ))
                        .build(),
                (player, event) -> {
                    MCZJUGameCore.getLeaderboardManager().saveTextDisplayAsync(record.getLeaderboardId(), record.getEntityId());
                    player.player().playSound(player.player().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    player.sender().success("<green>成功保存该房间的数据");
                }
        );
    }

    @Override
    protected String getTitle() {
        return "编辑排行榜展示实体";
    }

    @Override
    protected @Range(from = 1, to = 6) int getRows() {
        return 5;
    }

    @Override
    protected String getPermission() {
        return "mgc.dev";
    }

    private String formatLocation(Location location) {
        DecimalFormat df = new DecimalFormat("#.##");
        return "world: %s, x: %s, y: %s, z: %s, pitch: %s, yaw: %s".formatted(
                location.getWorld().getName(),
                df.format(location.getX()), df.format(location.getY()), df.format(location.getZ()),
                df.format(location.getPitch()), df.format(location.getYaw())
        );
    }

    private static void reopenLater(PlayerExt player, String leaderboardId, String entityId) {
        Bukkit.getScheduler().runTask(MCZJUGameCore.getInstance(), () -> {
            var record = MCZJUGameCore.getLeaderboardManager().getDisplayRecord(leaderboardId, entityId);
            new TextDisplayEditMenu(player.player(), record).open();
        });
    }
}
