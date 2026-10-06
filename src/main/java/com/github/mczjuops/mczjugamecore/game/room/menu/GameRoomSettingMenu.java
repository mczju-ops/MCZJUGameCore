package com.github.mczjuops.mczjugamecore.game.room.menu;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.room.AbstractGameRoom;
import com.github.mczjuops.mczjugamecore.menu.Menu;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 房间参数编辑菜单，每页展示 36 个参数；支持标量及单层 List 编辑。 */
public class GameRoomSettingMenu extends Menu {
    private static final int PAGE_SIZE = 36;
    private final AbstractGameRoom gameRoom;
    private int page;

    /**
     * 打开房间参数首页。
     * @param player 操作玩家，需要 mgc.dev 权限
     * @param gameRoom 要编辑的房间
     */
    public GameRoomSettingMenu(Player player, AbstractGameRoom gameRoom) {
        this(player, gameRoom, 0);
    }

    /**
     * 创建指定页的参数菜单。
     * @param player 操作玩家，需要 mgc.dev 权限
     * @param gameRoom 要编辑的房间
     * @param page 从零开始的页码，越界时夹取到有效页
     */
    public GameRoomSettingMenu(Player player, AbstractGameRoom gameRoom, int page) {
        super(player);
        this.gameRoom = gameRoom;
        this.page = Math.max(0, page);
    }

    /** {@inheritDoc} */
    @Override public String getTitle() { return "编辑房间参数"; }
    /** {@inheritDoc} */
    @Override public int getRows() { return 6; }
    /** {@inheritDoc} */
    @Override public String getPermission() { return "mgc.dev"; }

    /** 填充当前页参数、翻页按钮和保存按钮。 */
    @Override
    public void setup() {
        ItemStack background = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE);
        background.editMeta(meta -> meta.setHideTooltip(true));
        for (int i = 0; i < 9; i++) setSlot(i, background);
        for (int i = 45; i < 54; i++) setSlot(i, background);

        List<Map.Entry<String, Class<?>>> fields = new ArrayList<>(gameRoom.getAllFields().entrySet());
        int maxPage = Math.max(0, (fields.size() - 1) / PAGE_SIZE);
        page = Math.min(page, maxPage);
        setSlot(4, ItemBuilder.of(Material.WRITABLE_BOOK).customName("<green>编辑房间参数")
                .lore(List.of("<gray>游戏名：<white>" + gameRoom.getGameId(),
                        "<gray>房间名：<white>" + gameRoom.getRoomName(),
                        "<yellow>第 %d/%d 页，共 %d 个参数".formatted(page + 1, maxPage + 1, fields.size())))
                .glint(true).build());
        if (page > 0) setSlot(45, ItemBuilder.of(Material.ARROW).customName("<yellow>上一页").build(),
                (p, event) -> { page--; refresh(); });
        if (page < maxPage) setSlot(51, ItemBuilder.of(Material.ARROW).customName("<yellow>下一页").build(),
                (p, event) -> { page++; refresh(); });
        setSlot(53, ItemBuilder.of(Material.CHEST).customName("<green>保存数据")
                .lore(List.of("<yellow>点击将修改后的数据保存到文件", "",
                        "<gray>修改立即生效；保存将数据持久化到文件",
                        "<gray>服务器关闭时会自动保存，但异常崩溃可能导致数据丢失")).build(),
                (p, event) -> {
                    MCZJUGameCore.getGameRoomManager().saveGameRoom(gameRoom.getGameId(), gameRoom.getRoomName());
                    player.player().playSound(player.player().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    player.sender().success("<green>成功保存该房间的数据");
                });

        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, fields.size());
        for (int i = from; i < to; i++) {
            var entry = fields.get(i);
            String name = entry.getKey();
            Class<?> type = RoomParameterEditor.box(entry.getValue());
            Object value = gameRoom.getField(name, type);
            boolean list = List.class.isAssignableFrom(type);
            Class<?> elementType = list ? GameRoomListSettingMenu.elementType(gameRoom, name) : null;
            boolean editable = list ? elementType != null : RoomParameterEditor.supports(type);
            List<String> lore = new ArrayList<>(List.of(
                    "<yellow>类型：<dark_aqua>" + type.getSimpleName(),
                    "<green>值：<dark_green>" + (value instanceof List<?> values
                            ? "共 %d 个元素，点击查看".formatted(values.size()) : RoomParameterEditor.format(value))));
            lore.addAll(Arrays.asList(gameRoom.getFieldDescription(name)));
            lore.add("");
            lore.add(editable ? "<yellow>点击编辑" : list
                    ? "<red>仅支持明确声明元素类型的单层 List（布尔、数字、字符串、坐标）"
                    : "<red>此类型暂不支持编辑");
            ItemStack item = ItemBuilder.of(!editable ? Material.BARRIER : list
                            ? Material.BOOK : RoomParameterEditor.material(type))
                    .customName("<yellow>参数名：<white>" + name).lore(lore).glint(value != null).build();
            if (!editable) setSlot(9 + i - from, item);
            else setSlot(9 + i - from, item, (p, event) -> {
                if (list) new GameRoomListSettingMenu(p.player(), gameRoom, name, page).open();
                else RoomParameterEditor.edit(player, name, type, gameRoom.getField(name, type), newValue -> {
                    gameRoom.setField(name, newValue);
                    gameRoom.setModified(true);
                    player.sender().success("<green>设置成功：" + name);
                }, () -> new GameRoomSettingMenu(player.player(), gameRoom, page).open());
            });
        }
    }
}
