package com.github.mczjuops.mczjugamecore.game.room.menu;

import com.github.mczjuops.mczjugamecore.game.room.AbstractGameRoom;
import com.github.mczjuops.mczjugamecore.menu.Menu;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.DialogBuilder;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

// todo 优化此菜单，目前信息呈现不友好。比如界面显示游戏名和房间名，按钮给出更多提示
public class GameRoomSettingMenu extends Menu {

    private final AbstractGameRoom gameRoom;

    public GameRoomSettingMenu(Player player, AbstractGameRoom gameRoom) {
        super(GameRoomSettingMenu.class, player);
        this.gameRoom = gameRoom;
    }

    @Override
    public void setup() {
        Map<String, Class<?>> allFields = gameRoom.getAllFields();

        int slot = 0;

        for (Map.Entry<String, Class<?>> entry : allFields.entrySet()) {
            String fieldName = entry.getKey();
            Class<?> type = entry.getValue();

            Object value = gameRoom.getField(fieldName, type);
            String valueStr = value != null ? value.toString() : "空";

            Material material = Location.class.isAssignableFrom(type)
                    ? Material.COMPASS
                    : Material.BOOK;

            setSlot(
                    slot,
                    ItemBuilder.of(material)
                            .customName(fieldName)
                            .lore(List.of(
                                    "<yellow>类型：<dark_aqua>%s".formatted(type.getName()),
                                    "<yellow>值：<dark_green>%s".formatted(valueStr)
                                    ))
                            .build(),
                    (_, _) -> handleFieldClick(player, fieldName, type)
            );
            slot++; // 布局方式就是从槽位 0 开始一个个前进
        }
    }

    // 当前只有两种类型：设置坐标或输入内容
    private void handleFieldClick(PlayerExt player, String fieldName, Class<?> type) {
        if (Location.class.isAssignableFrom(type)) {
            player.player().closeInventory();
            player.selectLocation(location -> {
                gameRoom.setField(fieldName, location);
                player.sender().success("<green>坐标设置成功: %.2f，%.2f，%.2f".formatted(location.x(), location.y(), location.z()));
                gameRoom.setModified(true);
            });
        } else {
            player.player().closeInventory();
            DialogBuilder.of("输入值")
                    .emptyLine()
                    .emptyLine()
                    .textInput("value", "<yellow>请输入内容") // todo 优化一下提示，这里我没仔细研究这部分功能就没放
                    .showConfirm(
                            player.player(), 150,
                            "确认", (_, r) -> {
                                String input = r.text("value");
                                Object value = convert(input, type);
                                player.sender().success("<green>设置成功：<dark_aqua>%s</dark_aqua> -> <dark_green>%s".formatted(fieldName, value));
                                gameRoom.setModified(true);
                                // todo 输入容错，处理输入内容不合法的情况
                                // 如果需要重新打开 gui，可以在这个类里写一个 reopen()，重新打开刷新后（若需要）的菜单
                            }, "取消", null
                    );
        }
    }

    private Object convert(String input, Class<?> type) {
        try {
            if (type == int.class || type == Integer.class) {
                return Integer.valueOf(input);
            } else if (type == float.class || type == Float.class) {
                return Float.valueOf(input);
            } else if (type == double.class || type == Double.class) {
                return Double.valueOf(input);
            } else if (type == long.class || type == Long.class) {
                return Long.valueOf(input);
            } else if (type == boolean.class || type == Boolean.class) {
                return Boolean.valueOf(input);
            } else if (type == String.class) {
                return input;
            } else {
                throw new IllegalArgumentException("不支持的类型：%s".formatted(type));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("输入格式错误：%s".formatted(input));
        }
    }
}
