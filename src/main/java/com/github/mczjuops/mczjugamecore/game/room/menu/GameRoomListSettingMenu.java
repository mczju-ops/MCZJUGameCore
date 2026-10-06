package com.github.mczjuops.mczjugamecore.game.room.menu;

import com.github.mczjuops.mczjugamecore.game.room.AbstractGameRoom;
import com.github.mczjuops.mczjugamecore.menu.Menu;
import com.github.mczjuops.mczjugamecore.utils.DialogBuilder;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/** 单层 List 房间参数的分页菜单：查看元素、左键修改、右键确认删除和追加元素。 */
public class GameRoomListSettingMenu extends Menu {
    private static final int PAGE_SIZE = 36;
    private final AbstractGameRoom gameRoom;
    private final String fieldName;
    private final int parentPage;
    private int page;

    /**
     * 创建 List 参数首页；null 列表按空列表展示，首次新增时初始化。
     * @param player 操作玩家，需要 mgc.dev 权限
     * @param gameRoom 要编辑的房间
     * @param fieldName List 字段名，须声明支持的具体标量元素类型
     * @param parentPage 返回参数菜单时使用的从零开始页码
     */
    public GameRoomListSettingMenu(Player player, AbstractGameRoom gameRoom, String fieldName, int parentPage) {
        super(player);
        this.gameRoom = gameRoom;
        this.fieldName = fieldName;
        this.parentPage = Math.max(0, parentPage);
    }

    static Class<?> elementType(AbstractGameRoom room, String name) {
        try {
            var field = room.getClass().getDeclaredField(name);
            // 写回副本，兼容不可变初始列表；仅接受能接收这些副本的声明类型。
            if (!List.class.isAssignableFrom(field.getType())
                    || (!field.getType().isAssignableFrom(ArrayList.class)
                    && !field.getType().isAssignableFrom(LinkedList.class))) return null;
            if (!(field.getGenericType() instanceof ParameterizedType generic)
                    || !(generic.getActualTypeArguments()[0] instanceof Class<?> type)
                    || !RoomParameterEditor.supports(type)) return null;
            List<?> values = room.getField(name, List.class);
            if (values != null && values.stream().anyMatch(value -> value != null && !type.isInstance(value))) return null;
            return type;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private List<Object> values() {
        List<?> current = gameRoom.getField(fieldName, List.class);
        return current == null ? new ArrayList<>() : new ArrayList<>(current);
    }

    /** {@inheritDoc} */
    @Override protected String getTitle() { return "编辑房间列表参数"; }
    /** {@inheritDoc} */
    @Override protected int getRows() { return 6; }
    /** {@inheritDoc} */
    @Override protected String getPermission() { return "mgc.dev"; }

    /** 填充当前页元素、增删改入口和返回按钮；不支持的声明不提供编辑入口。 */
    @Override
    protected void setup() {
        ItemStack background = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE);
        background.editMeta(meta -> meta.setHideTooltip(true));
        for (int i = 0; i < 9; i++) setSlot(i, background);
        for (int i = 45; i < 54; i++) setSlot(i, background);
        setSlot(49, ItemBuilder.of(Material.OAK_DOOR).customName("<yellow>返回房间参数").build(),
                (p, event) -> new GameRoomSettingMenu(p.player(), gameRoom, parentPage).open());

        Class<?> type = elementType(gameRoom, fieldName);
        if (type == null) {
            setSlot(4, ItemBuilder.of(Material.BARRIER).customName("<red>此 List 暂不支持编辑").build());
            return;
        }
        List<Object> snapshot = values();
        int maxPage = Math.max(0, (snapshot.size() - 1) / PAGE_SIZE);
        page = Math.min(page, maxPage);
        setSlot(4, ItemBuilder.of(Material.BOOK).customName("<green>列表参数：<white>" + fieldName)
                .lore(List.of("<gray>元素类型：<white>" + type.getSimpleName(),
                        "<yellow>第 %d/%d 页，共 %d 个元素".formatted(page + 1, maxPage + 1, snapshot.size()),
                        "<gray>左键修改，右键删除；修改立即生效",
                        "<gray>返回房间参数后点击保存以写入文件")).build());
        if (snapshot.isEmpty()) setSlot(22, ItemBuilder.of(Material.PAPER)
                .customName("<gray>列表为空").lore(List.of("<yellow>点击底部按钮新增元素")).build());
        if (page > 0) setSlot(45, ItemBuilder.of(Material.ARROW).customName("<yellow>上一页").build(),
                (p, event) -> { page--; refresh(); });
        if (page < maxPage) setSlot(53, ItemBuilder.of(Material.ARROW).customName("<yellow>下一页").build(),
                (p, event) -> { page++; refresh(); });
        setSlot(47, ItemBuilder.of(Material.EMERALD).customName("<green>新增元素").build(),
                (p, event) -> RoomParameterEditor.edit(player, fieldName + "：新增元素", type, null, value -> {
                    List<Object> updated = new ArrayList<>(snapshot);
                    updated.add(value);
                    if (apply(snapshot, updated)) page = (updated.size() - 1) / PAGE_SIZE;
                }, this::openAgain));

        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, snapshot.size());
        for (int i = from; i < to; i++) {
            int index = i;
            Object value = snapshot.get(index);
            setSlot(9 + index - from, ItemBuilder.of(RoomParameterEditor.material(type))
                    .customName("<yellow>元素 #%d".formatted(index + 1))
                    .lore(List.of("<green>值：<white>" + RoomParameterEditor.format(value),
                            "<yellow>左键修改", "<red>右键删除（需要确认）")).build(), (p, event) -> {
                if (event.isRightClick()) delete(snapshot, index);
                else if (event.isLeftClick()) RoomParameterEditor.edit(player,
                        fieldName + " #%d".formatted(index + 1), type, value, newValue -> {
                            List<Object> updated = new ArrayList<>(snapshot);
                            updated.set(index, newValue);
                            apply(snapshot, updated);
                        }, this::openAgain);
            });
        }
    }

    private void delete(List<Object> snapshot, int index) {
        player.player().closeInventory();
        DialogBuilder.of("<red>删除列表元素")
                .text("<yellow>确认删除 %s 的第 %d 个元素？".formatted(fieldName, index + 1))
                .text("<gray>值：<white>" + RoomParameterEditor.format(snapshot.get(index)))
                .showConfirm(player.player(), 150, "删除", (p, response) -> {
                    List<Object> updated = new ArrayList<>(snapshot);
                    updated.remove(index);
                    apply(snapshot, updated);
                    RoomParameterEditor.later(this::openAgain);
                }, "取消", (p, response) -> RoomParameterEditor.later(this::openAgain));
    }

    private boolean apply(List<Object> snapshot, List<Object> updated) {
        if (!player.player().hasPermission(getPermission())) {
            player.sender().error("<red>没有房间参数编辑权限");
            return false;
        }
        if (elementType(gameRoom, fieldName) == null || !Objects.equals(values(), snapshot)) {
            player.sender().error("<red>列表已发生变化，请在刷新后的菜单中重新操作");
            return false;
        }
        Class<?> declared = gameRoom.getFieldType(fieldName);
        gameRoom.setField(fieldName, declared.isAssignableFrom(ArrayList.class)
                ? new ArrayList<>(updated) : new LinkedList<>(updated));
        gameRoom.setModified(true);
        player.sender().success("<green>已更新列表参数：" + fieldName);
        return true;
    }

    private void openAgain() {
        clearMenu();
        open();
    }
}
