package com.github.mczjuops.mczjugamecore.game.room.menu;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.room.AbstractGameRoom;
import com.github.mczjuops.mczjugamecore.menu.Menu;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.DialogBuilder;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GameRoomSettingMenu extends Menu {

    private final AbstractGameRoom gameRoom;

    private interface FieldHandler {
        Material getMaterial(); // 在编辑菜单中，用什么图标
        void handle(PlayerExt player, AbstractGameRoom room, String fieldName);
    }

    private static final Map<Class<?>, FieldHandler> HANDLERS = new LinkedHashMap<>();

    static {
        // Boolean
        HANDLERS.put(Boolean.class, new FieldHandler() {
            @Override public Material getMaterial() { return Material.LEVER; }
            @Override public void handle(PlayerExt player, AbstractGameRoom room, String fieldName) {
                Boolean current = room.getField(fieldName, Boolean.class);
                boolean currentValue = current != null && current;
                DialogBuilder.of("<yellow>设置布尔值：%s".formatted(fieldName))
                        .emptyLine()
                        .emptyLine()
                        .toggle("value", "<yellow>启用", currentValue)
                        .showConfirm(
                                player.player(), 150,
                                "确认", (p, r) -> {
                                    boolean newValue = r.bool("value");
                                    room.setField(fieldName, newValue);
                                    room.setModified(true);
                                    player.sender().success("<green>设置成功：<dark_aqua>%s</dark_aqua> -> <dark_green>%s".formatted(fieldName, newValue));
                                    reopenLater(player, room.getGameName(), room.getRoomName());
                                },
                                "取消", (p, r) -> reopenLater(player, room.getGameName(), room.getRoomName())
                        );
            }
        });

        // Integer
        HANDLERS.put(Integer.class, new FieldHandler() {
            @Override public Material getMaterial() { return Material.PAPER; }
            @Override public void handle(PlayerExt player, AbstractGameRoom room, String fieldName) {
                openNumberInput(player, room, fieldName, Integer.class, Integer::valueOf);
            }
        });

        // Long
        HANDLERS.put(Long.class, new FieldHandler() {
            @Override public Material getMaterial() { return Material.MAP; }
            @Override public void handle(PlayerExt player, AbstractGameRoom room, String fieldName) {
                openNumberInput(player, room, fieldName, Long.class, Long::valueOf);
            }
        });

        // Float
        HANDLERS.put(Float.class, new FieldHandler() {
            @Override public Material getMaterial() { return Material.PAINTING; }
            @Override public void handle(PlayerExt player, AbstractGameRoom room, String fieldName) {
                openNumberInput(player, room, fieldName, Float.class, Float::valueOf);
            }
        });

        // Double
        HANDLERS.put(Double.class, new FieldHandler() {
            @Override public Material getMaterial() { return Material.PAINTING; }
            @Override public void handle(PlayerExt player, AbstractGameRoom room, String fieldName) {
                openNumberInput(player, room, fieldName, Double.class, Double::valueOf);
            }
        });

        // String
        HANDLERS.put(String.class, new FieldHandler() {
            @Override public Material getMaterial() { return Material.NAME_TAG; }
            @Override public void handle(PlayerExt player, AbstractGameRoom room, String fieldName) {
                DialogBuilder.of("<yellow>设置字符串：%s".formatted(fieldName))
                        .emptyLine()
                        .emptyLine()
                        .textInput("value", "<yellow>请输入字符串")
                        .showConfirm(
                                player.player(), 150,
                                "确认", (p, r) -> {
                                    String input = r.text("value");
                                    room.setField(fieldName, input);
                                    room.setModified(true);
                                    player.sender().success("<green>设置成功：<dark_aqua>%s</dark_aqua> -> <dark_green>%s".formatted(fieldName, input));
                                    reopenLater(player, room.getGameName(), room.getRoomName());
                                },
                                "取消", (p, r) -> reopenLater(player, room.getGameName(), room.getRoomName())
                        );
            }
        });

        // Location
        HANDLERS.put(Location.class, new FieldHandler() {
            @Override public Material getMaterial() { return Material.COMPASS; }
            @Override public void handle(PlayerExt player, AbstractGameRoom room, String fieldName) {
                player.player().closeInventory();
                player.selectLocation(location -> {
                    room.setField(fieldName, location);
                    room.setModified(true);
                    player.sender().success("<green>位置设置成功");
                    reopenLater(player, room.getGameName(), room.getRoomName());
                });
            }
        });
    }

    private static <T> void openNumberInput(
            PlayerExt player, AbstractGameRoom room, String fieldName,
            Class<T> type, java.util.function.Function<String, T> parser
    ) {
        Object current = room.getField(fieldName, type);
        String hint = current != null ? "<gray>当前值：<white>%s".formatted(current) : "<yellow>请输入数字";

        DialogBuilder.of("<yellow>设置数值：%s".formatted(fieldName))
                .emptyLine()
                .emptyLine()
                .textInput("value", hint)
                .showConfirm(
                        player.player(), 150,
                        "确认", (p, r) -> {
                            String input = r.text("value").trim();
                            try {
                                T parsed = parser.apply(input);
                                room.setField(fieldName, parsed);
                                room.setModified(true);
                                player.sender().success("<green>设置成功：<dark_aqua>%s</dark_aqua> -> <dark_green>%s".formatted(fieldName, parsed));
                                reopenLater(player, room.getGameName(), room.getRoomName());
                            } catch (NumberFormatException e) {
                                player.sender().error("<red>输入格式错误：\"%s\"不是合法的%s".formatted(input, type.getSimpleName()));
                                // 重新打开让玩家重试
                                reopenLater(player, room.getGameName(), room.getRoomName());
                            }
                        },
                        "取消", (p, r) -> reopenLater(player, room.getGameName(), room.getRoomName())
                );
    }

    private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_BOXED = Map.of(
            boolean.class, Boolean.class,
            int.class,     Integer.class,
            long.class,    Long.class,
            float.class,   Float.class,
            double.class,  Double.class
    );

    private static Class<?> box(Class<?> type) {
        return PRIMITIVE_TO_BOXED.getOrDefault(type, type);
    }

    public GameRoomSettingMenu(Player player, AbstractGameRoom gameRoom) {
        super(player);
        this.gameRoom = gameRoom;
    }

    @Override
    public String getTitle() {
        return "编辑房间参数";
    }

    @Override
    public int getRows() {
        return 6;
    }

    @Override
    public String getPermission() {
        return "mgc.dev";
    }

    @Override
    public void setup() {

        ItemStack background = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE);
        background.editMeta(itemMeta -> itemMeta.setHideTooltip(true));

        for (int i = 0; i < 9; i++) setSlot(i, background);
        for (int i = inventory.getSize() - 1; i > inventory.getSize() - 10; i--) setSlot(i, background);

        setSlot(
                4,
                ItemBuilder.of(Material.WRITABLE_BOOK)
                        .customName("<green>编辑房间参数")
                        .lore(List.of(
                                "<gray>游戏名：<white>%s".formatted(gameRoom.getGameName()),
                                "<gray>房间名：<white>%s".formatted(gameRoom.getRoomName())
                        ))
                        .glint(true)
                        .build()
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
                (p, r, args) -> {
                    MCZJUGameCore.getGameRoomManager().saveGameRoom(gameRoom.getGameName(), gameRoom.getRoomName());
                    player.player().playSound(player.player().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    player.sender().success("<green>成功保存该房间的数据");
                }
        );

        Map<String, Class<?>> allFields = gameRoom.getAllFields();

        int slot = 9; // 从第 10 格开始填充

        for (Map.Entry<String, Class<?>> entry : allFields.entrySet()) {
            String fieldName = entry.getKey();
            Class<?> type = box(entry.getValue());

            Object value = gameRoom.getField(fieldName, type);
            String valueStr = value != null ? format(value) : "<red>未设置</red>";

            FieldHandler handler = HANDLERS.get(type);
            if (handler == null) {
                // 不支持的类型，显示但是说明不可编辑
                setSlot(
                        slot,
                        ItemBuilder.of(Material.BARRIER)
                                .customName("<yellow>参数名<white>%s".formatted(fieldName))
                                .lore(List.of(
                                        "<yellow>类型：<dark_aqua>%s".formatted(type.getName()),
                                        "",
                                        "<red>此类型暂不支持编辑"
                                ))
                                .build()
                );
            } else {
                setSlot(
                        slot,
                        ItemBuilder.of(handler.getMaterial())
                                .customName("<yellow>参数名：<white>%s".formatted(fieldName))
                                .lore(List.of(
                                        "<green>类型：<dark_aqua>%s".formatted(type.getSimpleName()),
                                        "<green>值：<dark_green>%s".formatted(valueStr),
                                        "",
                                        "<yellow>点击编辑"
                                ))
                                .glint(value != null)
                                .build(),
                        (p, r, args) -> handler.handle(player, gameRoom, fieldName)
                );
            }

            slot++;
            if (slot > inventory.getSize() - 9) break; // 最多 36 个参数，更多的参数直接忽略。应该不至于这么多
        }
    }

    private String format(Object value) {
        if (value instanceof Location location) {
            DecimalFormat df = new DecimalFormat("#.##");
            return "world: %s, x: %s, y: %s, z: %s, pitch: %s, yaw: %s".formatted(
                    location.getWorld().getName(),
                    df.format(location.getX()), df.format(location.getY()), df.format(location.getZ()),
                    df.format(location.getPitch()), df.format(location.getYaw())
            );
        }
        else return value.toString();
    }

    private static void reopenLater(PlayerExt player, String gameName, String roomName) {
        Bukkit.getScheduler().runTask(
                MCZJUGameCore.getInstance(),
                () -> {
                    AbstractGameRoom gameRoom = MCZJUGameCore.getGameRoomManager().getGameRoom(gameName, roomName);
                    new GameRoomSettingMenu(player.player(), gameRoom).open();
                }
        );
    }
}
