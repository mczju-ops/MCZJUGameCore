package com.github.mczjuops.mczjugamecore.menu;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Range;

import java.util.ArrayList;
import java.util.List;

/** 管理员游戏分类箱子菜单：分页选择游戏，再从配置中的类别中选择并保存。 */
public class GameCategorySettingMenu extends Menu {
    private static final int PAGE_SIZE = 45;
    private final String gameId;
    private final int page;

    /**
     * @param player 操作管理员（需要 mgc.dev 权限）
     * @param args 可选游戏 ID 和从零开始的游戏列表页码；无游戏 ID 时显示全部游戏
     */
    public GameCategorySettingMenu(Player player, Object... args) {
        super(player, args);
        gameId = args.length > 0 && args[0] instanceof String id ? id : null;
        page = args.length > 1 && args[1] instanceof Integer value ? Math.max(0, value) : 0;
    }

    @Override
    protected void setup() {
        if (gameId == null) setupGames();
        else setupCategories();
    }

    private void setupGames() {
        var config = MCZJUGameCore.getConfigManager();
        var metas = MCZJUGameCore.getGameManager().getGameMetas();
        List<String> ids = metas.keySet().stream().sorted().toList();
        int maxPage = Math.max(0, (ids.size() - 1) / PAGE_SIZE);
        int actualPage = Math.min(page, maxPage);
        int from = actualPage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, ids.size());
        for (int i = from; i < to; i++) {
            String id = ids.get(i);
            var meta = metas.get(id);
            var category = config.getGameCategories().get(config.getGameCategory(id));
            List<String> lore = new ArrayList<>(meta.description());
            lore.add("");
            lore.add("<gray>游戏 ID：<white>" + id);
            lore.add("<gray>当前类别：" + (category == null ? "<red>未配置类别" : category.name()));
            lore.add("<yellow>点击设置游戏类别");
            setSlot(i - from, ItemBuilder.of(Material.CLOCK).customName(meta.displayName())
                    .itemModel(meta.icon()).lore(lore).build(),
                    (p, event) -> new GameCategorySettingMenu(p.player(), id, actualPage).open());
        }
        if (actualPage > 0) setSlot(45, ItemBuilder.of(Material.ARROW).customName("<yellow>上一页").build(),
                (p, event) -> new GameCategorySettingMenu(p.player(), null, actualPage - 1).open());
        setSlot(49, ItemBuilder.of(Material.CHEST).customName("<gold>游戏分类管理")
                .lore(List.of("<yellow>第 %d/%d 页".formatted(actualPage + 1, maxPage + 1),
                        "<gray>点击游戏后选择类别")).build());
        if (actualPage < maxPage) setSlot(53, ItemBuilder.of(Material.ARROW).customName("<yellow>下一页").build(),
                (p, event) -> new GameCategorySettingMenu(p.player(), null, actualPage + 1).open());
    }

    private void setupCategories() {
        var config = MCZJUGameCore.getConfigManager();
        var meta = MCZJUGameCore.getGameManager().getGameMetas().get(gameId);
        setSlot(4, ItemBuilder.of(Material.CLOCK)
                .customName(meta == null ? gameId : meta.displayName())
                .lore(List.of("<yellow>请选择此游戏的类别")).build());
        for (var entry : MainMenu.arrange(config.getGameCategories().keySet()).entrySet()) {
            String id = entry.getValue();
            var category = config.getGameCategories().get(id);
            setSlot(entry.getKey(), ItemBuilder.of(Material.CHEST).itemModel(category.icon())
                    .customName(category.name()).lore(category.lore())
                    .glint(id.equals(config.getGameCategory(gameId))).build(), (p, event) -> {
                // 菜单打开后权限、类别或注册游戏可能发生变化，保存前再次校验。
                if (!p.player().hasPermission(getPermission())) {
                    p.sender().warn("没有游戏分类管理权限");
                    return;
                }
                if (!config.setGameCategory(gameId, id)) {
                    p.sender().error("设置游戏类别失败，请检查游戏、类别配置及服务器日志");
                    return;
                }
                p.sender().success("已将游戏 %s 设置为类别 %s".formatted(
                        meta == null ? gameId : meta.displayName(), config.getGameCategories().get(id).name()));
                new GameCategorySettingMenu(p.player(), null, page).open();
            });
        }
        setSlot(40, ItemBuilder.of(Material.ARROW).customName("<yellow>返回游戏列表").build(),
                (p, event) -> new GameCategorySettingMenu(p.player(), null, page).open());
    }

    @Override protected String getTitle() { return "游戏分类管理"; }
    @Override protected @Range(from = 1, to = 6) int getRows() {
        return args.length > 0 && args[0] instanceof String ? 5 : 6;
    }
    @Override protected String getPermission() { return "mgc.dev"; }
}
