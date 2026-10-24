package com.github.mczjuops.mczjugamecore.menu;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.utils.DialogBuilder;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

/** 管理员按游戏配置 Wiki 的分页箱子菜单，左键编辑、右键移除。 */
public final class GameWikiSettingMenu extends Menu {
    private final int page;

    /**
     * @param player 管理员
     * @param args 可选从零开始的页码，负数按零处理
     */
    public GameWikiSettingMenu(Player player, Object... args) {
        super(player, args);
        page = args.length > 0 && args[0] instanceof Integer value ? Math.max(0, value) : 0;
    }

    @Override
    protected void setup() {
        var metas = MCZJUGameCore.getGameManager().getGameMetas();
        var manager = MCZJUGameCore.getGameWikiManager();
        List<String> ids = metas.keySet().stream().sorted().toList();
        int maxPage = Math.max(0, (ids.size() - 1) / 45);
        int actualPage = Math.min(page, maxPage);
        for (int i = actualPage * 45; i < Math.min(ids.size(), (actualPage + 1) * 45); i++) {
            String id = ids.get(i);
            var meta = metas.get(id);
            String url = manager.getLink(id);
            setSlot(i % 45, ItemBuilder.of(Material.CLOCK).itemModel(meta.icon())
                    .customName(meta.displayName()).lore(List.of("<gray>游戏 ID：" + id,
                            url == null ? "<red>尚未设置 Wiki" : "<green>已设置 Wiki",
                            "<yellow>左键：查看或编辑链接", "<yellow>右键：移除链接"))
                    .glint(url != null).build(), (p, event) -> {
                if (!canEdit(p.player(), id)) return;
                if (event.getClick() == ClickType.RIGHT) {
                    save(p.player(), id, null);
                    refresh();
                } else if (event.getClick() == ClickType.LEFT) {
                    p.player().closeInventory();
                    Bukkit.getScheduler().runTask(MCZJUGameCore.getInstance(), () -> {
                        if (!canEdit(p.player(), id)) return;
                        DialogBuilder.of("<gold>设置小游戏 Wiki")
                                .text("<gray>输入 HTTP/HTTPS 链接；留空则移除")
                                .textInput("url", "<yellow>Wiki 链接", 2048, 300,
                                        manager.getLink(id) == null ? "" : manager.getLink(id))
                                .showConfirm(p.player(), 150, "保存", (who, response) ->
                                        Bukkit.getScheduler().runTask(MCZJUGameCore.getInstance(), () -> {
                                            save(who, id, response.text("url"));
                                            reopen(who, actualPage);
                                        }), "取消", (who, response) ->
                                        Bukkit.getScheduler().runTask(MCZJUGameCore.getInstance(), () -> reopen(who, actualPage)));
                    });
                }
            });
        }
        if (actualPage > 0) setSlot(45, ItemBuilder.of(Material.ARROW).customName("<yellow>上一页").build(),
                (p, event) -> new GameWikiSettingMenu(p.player(), actualPage - 1).open());
        setSlot(49, ItemBuilder.of(Material.BOOK).customName("<gold>小游戏 Wiki 配置")
                .lore(List.of("<yellow>第 %d/%d 页".formatted(actualPage + 1, maxPage + 1))).build());
        if (actualPage < maxPage) setSlot(53, ItemBuilder.of(Material.ARROW).customName("<yellow>下一页").build(),
                (p, event) -> new GameWikiSettingMenu(p.player(), actualPage + 1).open());
    }

    private boolean canEdit(Player who, String id) {
        if (!who.isOnline()) return false;
        if (!who.hasPermission(getPermission())
                || !MCZJUGameCore.getGameManager().getRegisteredGameIds().contains(id)) {
            who.sendMessage("没有权限或小游戏已不存在，请重新打开菜单");
            return false;
        }
        return true;
    }

    private void save(Player who, String id, String url) {
        if (!canEdit(who, id)) return;
        try {
            who.sendMessage(MCZJUGameCore.getGameWikiManager().setLink(id, url)
                    ? "已保存 Wiki 配置" : "保存 Wiki 配置失败，请检查服务器日志");
        } catch (IllegalArgumentException e) {
            who.sendMessage(e.getMessage());
        }
    }

    private void reopen(Player who, int targetPage) {
        if (who.isOnline() && who.hasPermission(getPermission())) new GameWikiSettingMenu(who, targetPage).open();
    }

    @Override protected String getTitle() { return "小游戏 Wiki 配置"; }
    @Override protected int getRows() { return 6; }
    @Override protected String getPermission() { return "mgc.dev"; }
}
