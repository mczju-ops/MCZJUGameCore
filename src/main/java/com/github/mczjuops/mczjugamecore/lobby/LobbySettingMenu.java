package com.github.mczjuops.mczjugamecore.lobby;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.menu.Menu;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.Range;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/** 配置主大厅、每个游戏类别大厅和已注册小游戏大厅的分页箱子菜单。 */
public class LobbySettingMenu extends Menu {
    private static final int PAGE_SIZE = 45;
    private final int page;

    /**
     * @param player 操作管理员，需要 mgc.dev 权限
     * @param args 第一个参数可为从零开始的页码，负数按零处理
     */
    public LobbySettingMenu(Player player, Object... args) {
        super(player, args);
        int requestedPage = args.length > 0 && args[0] instanceof Integer value ? value : 0;
        this.page = Math.max(0, requestedPage);
    }

    @Override
    protected void setup() {
        List<LobbyTarget> targets = new ArrayList<>();
        targets.add(new LobbyTarget(LobbyManager.MAIN_LOBBY_ID, LobbyType.MAIN));
        MCZJUGameCore.getConfigManager().getGameCategories().keySet().forEach(
                id -> targets.add(new LobbyTarget(id, LobbyType.CATEGORY)));
        MCZJUGameCore.getGameManager().getRegisteredGameIds().stream().sorted().forEach(
                id -> targets.add(new LobbyTarget(id, LobbyType.GAME)));

        int maxPage = Math.max(0, (targets.size() - 1) / PAGE_SIZE);
        int actualPage = Math.min(page, maxPage);
        int from = actualPage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, targets.size());
        for (int i = from; i < to; i++) {
            LobbyTarget target = targets.get(i);
            setSlot(i - from, createLobbyItem(target), (clickedPlayer, event) -> {
                if (!clickedPlayer.player().hasPermission(getPermission()) || !isValid(target)) {
                    clickedPlayer.sender().warn("没有权限或此大厅对应的游戏、类别已不存在，请重新打开菜单");
                    return;
                }
                var manager = MCZJUGameCore.getLobbyManager();
                if (event.getClick() == ClickType.RIGHT) {
                    boolean removed = target.type() == LobbyType.CATEGORY
                            ? manager.removeCategoryLobby(target.id()) : manager.removeLobby(target.id());
                    if (removed) {
                        clickedPlayer.sender().success("已移除 %s 的大厅位置".formatted(displayName(target)));
                        refresh();
                    } else {
                        clickedPlayer.sender().warn("此大厅尚未设置位置或保存失败，请检查服务器日志");
                    }
                    return;
                }

                clickedPlayer.player().closeInventory();
                clickedPlayer.selectLocation(location -> {
                    if (!clickedPlayer.player().hasPermission(getPermission()) || !isValid(target)) {
                        clickedPlayer.sender().warn("没有权限或此大厅对应的游戏、类别已不存在，未保存位置");
                        return;
                    }
                    if (target.type() == LobbyType.CATEGORY) {
                        if (!manager.setCategoryLobby(target.id(), location)) {
                            clickedPlayer.sender().error("保存类别大厅失败，请检查服务器日志");
                            return;
                        }
                    } else {
                        try {
                            manager.setLobby(target.id(), location);
                        } catch (IllegalStateException e) {
                            clickedPlayer.sender().error("保存大厅失败，请检查服务器日志");
                            return;
                        }
                    }
                    clickedPlayer.sender().success("已设置 %s 的大厅位置".formatted(displayName(target)));
                    new LobbySettingMenu(clickedPlayer.player(), actualPage).open();
                });
            });
        }

        if (actualPage > 0) setSlot(45, ItemBuilder.of(Material.ARROW).customName("<yellow>上一页").build(),
                (p, event) -> new LobbySettingMenu(p.player(), actualPage - 1).open());
        setSlot(49, ItemBuilder.of(Material.COMPASS)
                .customName("<gold>大厅位置配置")
                .lore(List.of("<gray>左键：设置位置", "<gray>右键：移除位置",
                        "<yellow>第 %d/%d 页".formatted(actualPage + 1, maxPage + 1))).build());
        if (actualPage < maxPage) setSlot(53, ItemBuilder.of(Material.ARROW).customName("<yellow>下一页").build(),
                (p, event) -> new LobbySettingMenu(p.player(), actualPage + 1).open());
    }

    private boolean isValid(LobbyTarget target) {
        return switch (target.type()) {
            case MAIN -> true;
            case CATEGORY -> MCZJUGameCore.getConfigManager().getGameCategories().containsKey(target.id());
            case GAME -> MCZJUGameCore.getGameManager().getRegisteredGameIds().contains(target.id());
        };
    }

    private org.bukkit.inventory.ItemStack createLobbyItem(LobbyTarget target) {
        var manager = MCZJUGameCore.getLobbyManager();
        Location location = target.type() == LobbyType.CATEGORY
                ? manager.getCategoryLobby(target.id()) : manager.getLobby(target.id());
        Material icon = switch (target.type()) {
            case MAIN -> Material.NETHER_STAR;
            case CATEGORY -> MCZJUGameCore.getConfigManager().getGameCategories().get(target.id()).icon();
            case GAME -> MCZJUGameCore.getGameManager().getGameMetas().get(target.id()).icon();
        };
        List<String> lore = new ArrayList<>();
        if (target.type() == LobbyType.CATEGORY) {
            lore.addAll(MCZJUGameCore.getConfigManager().getGameCategories().get(target.id()).lore());
            lore.add("");
            lore.add("<gray>类别大厅");
            lore.add("<gray>/lobby category:" + target.id());
        }
        if (location == null) {
            lore.add("<red>未配置");
        } else if (location.getWorld() == null) {
            lore.add("<red>配置世界未加载");
        } else {
            DecimalFormat df = new DecimalFormat("#.##");
            lore.add("<green>已配置");
            lore.add("<gray>世界：<white>" + location.getWorld().getName());
            lore.add("<gray>坐标：<white>%s, %s, %s".formatted(
                    df.format(location.getX()), df.format(location.getY()), df.format(location.getZ())));
        }
        lore.add("");
        lore.add("<yellow>左键设置当前位置");
        lore.add("<yellow>右键移除配置");
        return ItemBuilder.of(icon).customName(displayName(target)).lore(lore).glint(location != null).build();
    }

    private String displayName(LobbyTarget target) {
        if (target.type() == LobbyType.MAIN) return "<gold>主大厅";
        if (target.type() == LobbyType.CATEGORY) {
            var category = MCZJUGameCore.getConfigManager().getGameCategories().get(target.id());
            return (category == null ? target.id() : category.name()) + " <gray>(类别：" + target.id() + ")";
        }
        var meta = MCZJUGameCore.getGameManager().getGameMetas().get(target.id());
        return meta == null ? "<yellow>" + target.id() : meta.displayName() + " <gray>(" + target.id() + ")";
    }

    private enum LobbyType { MAIN, CATEGORY, GAME }
    private record LobbyTarget(String id, LobbyType type) {}

    @Override protected String getTitle() { return "大厅位置配置"; }
    @Override protected @Range(from = 1, to = 6) int getRows() { return 6; }
    @Override protected String getPermission() { return "mgc.dev"; }
}
