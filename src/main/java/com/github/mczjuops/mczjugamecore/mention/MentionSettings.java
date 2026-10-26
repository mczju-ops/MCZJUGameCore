package com.github.mczjuops.mczjugamecore.mention;

import net.kyori.adventure.key.Key;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

/**
 * mention.yml 的只读快照。解析失败时不修改旧配置。
 *
 * @param enabled 是否启用聊天提醒
 * @param hideMessage 为 true 时被提醒的消息不广播（与 Beep 一致）
 * @param requireSpace 标记与名字之间是否必须有空白
 * @param allowAll 是否允许 @ all 提醒全服
 * @param maxTargets 一条消息最多提醒的玩家数，0 表示不限
 * @param cooldownMillis 同一玩家两次提醒的最小间隔（毫秒），0 表示不限制
 * @param pingSound @ 提醒的音效，null 表示静音
 * @param bigSound @@ 提醒的音效，null 表示静音
 * @param title @@ 提醒的标题，MiniMessage 格式
 * @param subtitle @@ 提醒的副标题，MiniMessage 格式
 * @param fadeInTicks 标题淡入时间（tick）
 * @param stayTicks 标题停留时间（tick）
 * @param fadeOutTicks 标题淡出时间（tick）
 * @param pingActionBar 收到提醒时显示的动作栏文字，MiniMessage 格式，留空不显示
 * @param senderFeedback 隐藏消息时给发送者的回执，{@code {targets}} 为被提醒的玩家，留空不显示
 * @param senderHint 打出了提醒但未命中任何在线玩家时给发送者的私聊提示，留空不提示
 */
public record MentionSettings(
        boolean enabled,
        boolean hideMessage,
        boolean requireSpace,
        boolean allowAll,
        int maxTargets,
        long cooldownMillis,
        @Nullable MentionSound pingSound,
        @Nullable MentionSound bigSound,
        String title,
        String subtitle,
        int fadeInTicks,
        int stayTicks,
        int fadeOutTicks,
        String pingActionBar,
        String senderFeedback,
        String senderHint
) {

    /** 与插件内置 mention.yml 一致的默认值，供配置文件缺失时使用。 */
    public static final MentionSettings DEFAULTS = new MentionSettings(
            true, false, true, true, 0, 0L,
            new MentionSound(Key.key("minecraft:entity.arrow.hit_player"), 1.0f, 1.0f),
            new MentionSound(Key.key("minecraft:entity.arrow.hit_player"), 1.0f, 1.0f),
            "<aqua>{player}</aqua>",
            "<gray>在 @ 你</gray>",
            2, 15, 5,
            "<gray>{player} 提醒了你</gray>",
            "<gray>已提醒：</gray><white>{targets}</white>",
            "<gray>提醒未生效：名字需用空格分隔，例如 <white>@ Steve 你好</white>、<white>@@ Steve</white>；"
                    + "并确认玩家在线</gray>"
    );

    /**
     * 读取配置；缺失项使用默认值，数值越界会被收敛到合法范围。
     *
     * @param config mention.yml 的内容
     * @return 解析结果
     * @throws IllegalArgumentException 音效键名非法
     */
    public static MentionSettings load(FileConfiguration config) {
        return new MentionSettings(
                config.getBoolean("enabled", true),
                config.getBoolean("hide-message", DEFAULTS.hideMessage()),
                config.getBoolean("require-space", true),
                config.getBoolean("allow-all", true),
                Math.max(0, config.getInt("max-targets", 0)),
                Math.max(0L, config.getLong("cooldown-millis", 0L)),
                readSound(config, "ping", DEFAULTS.pingSound()),
                readSound(config, "big", DEFAULTS.bigSound()),
                config.getString("big.title", DEFAULTS.title()),
                config.getString("big.subtitle", DEFAULTS.subtitle()),
                Math.max(0, config.getInt("big.fade-in", DEFAULTS.fadeInTicks())),
                Math.max(0, config.getInt("big.stay", DEFAULTS.stayTicks())),
                Math.max(0, config.getInt("big.fade-out", DEFAULTS.fadeOutTicks())),
                config.getString("ping-actionbar", DEFAULTS.pingActionBar()),
                config.getString("sender-feedback", DEFAULTS.senderFeedback()),
                config.getString("sender-hint", DEFAULTS.senderHint())
        );
    }

    private static @Nullable MentionSound readSound(FileConfiguration config, String section,
                                                    @Nullable MentionSound fallback) {
        String raw = config.getString(section + ".sound");
        // 缺少该项时沿用默认音效；显式留空或写 none 表示静音
        if (raw == null) return fallback;
        if (raw.isBlank() || raw.equalsIgnoreCase("none")) return null;
        Key key;
        try {
            key = Key.key(raw.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("%s.sound 不是合法音效键名：%s".formatted(section, raw));
        }
        float volume = (float) config.getDouble(section + ".volume", 1.0);
        float pitch = (float) config.getDouble(section + ".pitch", 1.0);
        return new MentionSound(key, Math.max(0.0f, volume), Math.max(0.01f, pitch));
    }
}
