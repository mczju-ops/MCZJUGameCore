package com.github.mczjuops.mczjugamecore.mention;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 从聊天纯文本中解析 <code>@</code> / <code>@@</code> 提醒，不依赖 Bukkit，便于单元测试。
 * <p>
 * 规则（与 MCDR 插件 Beep 一致）：
 * <ul>
 *     <li><code>@ 名字</code> 为普通提醒，<code>@@ 名字</code> 为强提醒（显示 title）。</li>
 *     <li>标记后紧跟的、能被解析器识别的连续单词都算作目标，遇到无法识别的单词即停止。</li>
 *     <li>{@code requireSpace} 为 true 时，标记与名字之间必须有空白，避免误伤邮箱等文本。</li>
 * </ul>
 */
public final class MentionParser {

    /** 提醒强度；同一玩家被两种标记同时命中时按 {@link #TITLE} 处理。 */
    public enum Kind { PING, TITLE }

    /**
     * @param kind 提醒强度
     * @param name 解析器返回的规范目标名（可能是玩家名，也可能是管理器自定义的保留字）
     */
    public record Mention(Kind kind, String name) {}

    private MentionParser() {}

    /**
     * 按出现顺序解析文本中的全部提醒，允许重复。
     *
     * @param text 聊天纯文本，null 或空串返回空列表
     * @param requireSpace 标记与名字之间是否必须有空白
     * @param resolver 把候选单词转换为规范目标名；返回 null 表示该单词不是目标，该标记的解析到此停止
     * @return 不可变的提醒列表
     */
    public static List<Mention> parse(String text, boolean requireSpace, Function<String, String> resolver) {
        if (text == null || text.isEmpty()) return List.of();
        List<Mention> mentions = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            if (text.charAt(index) != '@') {
                index++;
                continue;
            }
            boolean doubleAt = index + 1 < text.length() && text.charAt(index + 1) == '@';
            int afterMarker = index + (doubleAt ? 2 : 1);
            if (requireSpace && !(afterMarker < text.length() && Character.isWhitespace(text.charAt(afterMarker)))) {
                // 严格空格：标记后必须是空白，跳过一个标记长度继续扫描
                index = afterMarker;
                continue;
            }
            Kind kind = doubleAt ? Kind.TITLE : Kind.PING;
            int cursor = skipWhitespace(text, afterMarker);
            boolean consumed = false;
            while (cursor < text.length()) {
                int end = cursor;
                while (end < text.length() && !Character.isWhitespace(text.charAt(end))) end++;
                String resolved = resolver.apply(text.substring(cursor, end));
                if (resolved == null) break;
                mentions.add(new Mention(kind, resolved));
                consumed = true;
                cursor = skipWhitespace(text, end);
            }
            // 没有命中任何目标时只移动一个标记长度，避免漏掉紧随其后的其它标记
            index = consumed ? cursor : afterMarker;
        }
        return List.copyOf(mentions);
    }

    private static int skipWhitespace(String text, int from) {
        int cursor = from;
        while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) cursor++;
        return cursor;
    }
}
