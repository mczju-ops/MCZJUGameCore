package com.github.mczjuops.mczjugamecore.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * 以 MiniMessage 格式将字符串解析为 Adventure 的 Component
 */
public class TextParser {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private TextParser() {}

    /** 将 MiniMessage 格式字符串解析为 Component */
    public static Component parse(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        return MM.deserialize(text);
    }

    /**
     * 将 MiniMessage 格式字符串解析为 Component，并设置为非斜体
     * 常用于物品名称或描述，因为不设置斜体时，字体默认为斜体
     */
    public static Component parseNonItalic(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        return MM.deserialize(text).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** 从 Component 中提取字符串，舍弃所有格式、点击事件等额外信息 */
    public static String plain(Component text) {
        if (text == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(text);
    }
}
