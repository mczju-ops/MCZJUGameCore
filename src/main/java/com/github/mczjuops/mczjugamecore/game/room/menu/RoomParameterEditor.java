package com.github.mczjuops.mczjugamecore.game.room.menu;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.DialogBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/** 房间标量参数和 List 元素共用的输入、格式化工具。 */
final class RoomParameterEditor {
    private static final Map<Class<?>, Class<?>> BOXED = Map.of(
            boolean.class, Boolean.class, int.class, Integer.class, long.class, Long.class,
            float.class, Float.class, double.class, Double.class);
    private static final Map<Class<?>, Function<String, ?>> NUMBERS = Map.of(
            Integer.class, Integer::valueOf, Long.class, Long::valueOf,
            Float.class, Float::valueOf, Double.class, Double::valueOf);

    private RoomParameterEditor() {}

    static Class<?> box(Class<?> type) {
        return BOXED.getOrDefault(type, type);
    }

    static boolean supports(Class<?> type) {
        return type == Boolean.class || type == String.class || type == Location.class || NUMBERS.containsKey(type);
    }

    static Material material(Class<?> type) {
        if (type == Boolean.class) return Material.LEVER;
        if (type == String.class) return Material.NAME_TAG;
        if (type == Location.class) return Material.COMPASS;
        if (type == Long.class) return Material.MAP;
        if (type == Float.class || type == Double.class) return Material.PAINTING;
        return Material.PAPER;
    }

    static String format(Object value) {
        if (value == null) return "<red>未设置</red>";
        if (value instanceof Location location) {
            DecimalFormat df = new DecimalFormat("#.##");
            return "world: %s, x: %s, y: %s, z: %s, pitch: %s, yaw: %s".formatted(
                    location.getWorld() == null ? "未设置" : location.getWorld().getName(),
                    df.format(location.getX()), df.format(location.getY()), df.format(location.getZ()),
                    df.format(location.getPitch()), df.format(location.getYaw()));
        }
        return value.toString();
    }

    static void later(Runnable action) {
        Bukkit.getScheduler().runTask(MCZJUGameCore.getInstance(), action);
    }

    static void edit(PlayerExt player, String name, Class<?> type, Object current,
                     Consumer<Object> setter, Runnable returnAction) {
        player.player().closeInventory();
        if (type == Location.class) {
            player.selectLocation(location -> {
                setter.accept(location);
                later(returnAction);
            });
            return;
        }
        DialogBuilder dialog = DialogBuilder.of("<yellow>编辑：" + name);
        if (type == Boolean.class) {
            dialog.toggle("value", "<yellow>启用", Boolean.TRUE.equals(current));
        } else {
            String initial = current == null ? "" : current.toString();
            dialog.textInput("value", "<yellow>请输入" + type.getSimpleName(),
                    Math.max(1024, initial.length()), 200, initial);
        }
        dialog.showConfirm(player.player(), 150, "确认", (p, response) -> {
            try {
                Object value;
                if (type == Boolean.class) value = response.bool("value");
                else if (type == String.class) value = response.text("value");
                else value = NUMBERS.get(type).apply(response.text("value").trim());
                setter.accept(value);
            } catch (NumberFormatException e) {
                player.sender().error("<red>输入格式错误，请输入合法的" + type.getSimpleName());
            }
            later(returnAction);
        }, "取消", (p, response) -> later(returnAction));
    }
}
