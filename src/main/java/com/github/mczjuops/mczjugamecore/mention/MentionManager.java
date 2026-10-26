package com.github.mczjuops.mczjugamecore.mention;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * 聊天提醒（<code>@</code> 提示音、<code>@@</code> 标题）的配置与效果管理。
 * <p>
 * 行为移植自 MCDR 插件
 * <a href="https://github.com/TISUnion/Beep">Beep</a>：<code>@ 玩家</code> 让目标听到提示音，
 * <code>@@ 玩家</code> 额外在目标屏幕中央显示 title 并连响两下；<code>@ all</code> 作用于全服玩家。
 * 默认情况下带提醒的消息不广播，只给发送者回执（与 Beep 的取消转发一致），可通过
 * {@code hide-message: false} 改为照常广播。
 */
public final class MentionManager {

    /** 使用 {@code @ all} 需要的权限。 */
    public static final String PERMISSION_ALL = "mgc.mention.all";

    /** @ all 在解析结果中的保留名；Minecraft 玩家名不含 '@'，不会与之冲突。 */
    static final String ALL_TARGETS = "@all";

    /** 可识别的全服关键字，须与标记之间留有空格（如 <code>@ all</code>）。 */
    private static final Set<String> ALL_KEYWORDS = Set.of("all", "everyone");

    private final Path file;
    private final ConsoleSender logger = new ConsoleSender("Mention");
    private final Map<UUID, Long> lastUsedAt = new HashMap<>();

    private MentionSettings settings = MentionSettings.DEFAULTS;

    /** 从插件数据目录的 mention.yml 加载配置；首次运行时写出默认配置。 */
    public MentionManager() {
        file = MCZJUGameCore.getInstance().getDataFolder().toPath().resolve("mention.yml");
        try {
            reload();
        } catch (IllegalStateException e) {
            logger.error(e.getMessage());
        }
    }

    /** @return 当前生效的不可变配置快照 */
    public MentionSettings getSettings() {
        return settings;
    }

    /**
     * 重读 mention.yml；失败时保留旧配置并抛出异常，由命令层报告。
     * 已排队的声音不受影响，新消息立即使用新配置。
     *
     * @throws IllegalStateException 文件读取或配置解析失败
     */
    public void reload() {
        MCZJUGameCore plugin = MCZJUGameCore.getInstance();
        if (Files.notExists(file)) {
            try {
                plugin.saveResource("mention.yml", false);
            } catch (RuntimeException e) {
                logger.warn("未能写出默认 mention.yml，将使用内置默认值：%s".formatted(e.getMessage()));
            }
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            if (Files.exists(file)) config.load(file.toFile());
        } catch (Exception e) {
            throw new IllegalStateException("无法读取 mention.yml，已保留旧配置：" + e.getMessage(), e);
        }
        try {
            settings = MentionSettings.load(config);
        } catch (RuntimeException e) {
            throw new IllegalStateException("mention.yml 配置无效，已保留旧配置：" + e.getMessage(), e);
        }
        lastUsedAt.clear();
    }

    /**
     * 按冷却时间判断发送者本次是否可以使用提醒；成功时记录本次时间。
     *
     * @param sender 发送者
     * @return 冷却结束或未配置冷却时返回 true
     */
    public boolean tryUse(Player sender) {
        long cooldown = settings.cooldownMillis();
        if (cooldown <= 0) return true;
        long now = System.currentTimeMillis();
        Long last = lastUsedAt.get(sender.getUniqueId());
        if (last != null && now - last < cooldown) return false;
        lastUsedAt.put(sender.getUniqueId(), now);
        return true;
    }

    /** 玩家退出时清理冷却记录。 */
    public void forget(UUID playerId) {
        lastUsedAt.remove(playerId);
    }

    /**
     * 解析消息中的提醒目标；同一玩家被 <code>@</code> 与 <code>@@</code> 同时命中时按 <code>@@</code> 处理。
     * 可安全地在异步聊天线程调用。
     *
     * @param sender 发送者，用于权限判断
     * @param text 聊天纯文本
     * @return 按首次出现顺序排列的目标及其提醒强度
     */
    public Map<Player, MentionParser.Kind> resolveTargets(Player sender, String text) {
        List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        Map<String, Player> byName = new HashMap<>();
        Map<String, String> canonicalNames = new HashMap<>();
        for (Player player : online) {
            String key = player.getName().toLowerCase(Locale.ROOT);
            byName.put(key, player);
            canonicalNames.put(key, player.getName());
        }
        boolean allowAll = settings.allowAll() && sender.hasPermission(PERMISSION_ALL);
        Function<String, String> resolver = token -> resolveName(token, canonicalNames, allowAll);

        List<MentionParser.Mention> mentions = MentionParser.parse(text, settings.requireSpace(), resolver);
        Map<Player, MentionParser.Kind> targets = new LinkedHashMap<>();
        int limit = settings.maxTargets();
        for (MentionParser.Mention mention : mentions) {
            if (limit > 0 && targets.size() >= limit) break;
            if (ALL_TARGETS.equals(mention.name())) {
                for (Player player : online) {
                    if (limit > 0 && targets.size() >= limit) break;
                    targets.merge(player, mention.kind(), MentionManager::stronger);
                }
                continue;
            }
            Player player = byName.get(mention.name().toLowerCase(Locale.ROOT));
            if (player != null) targets.merge(player, mention.kind(), MentionManager::stronger);
        }
        return targets;
    }

    private static MentionParser.Kind stronger(MentionParser.Kind first, MentionParser.Kind second) {
        return first == MentionParser.Kind.TITLE || second == MentionParser.Kind.TITLE
                ? MentionParser.Kind.TITLE
                : MentionParser.Kind.PING;
    }

    /**
     * 把聊天中的候选单词解析为规范目标名，纯函数，便于测试。
     * <p>
     * 只有空白才分词，整词精确匹配（不区分大小写）；中文同样按整词处理，不做任何前缀截断。
     *
     * @param token 候选单词
     * @param names 小写玩家名到规范玩家名的映射
     * @param allowAll 是否允许 all / everyone 关键字
     * @return 规范目标名（可能是 {@link #ALL_TARGETS}），不是目标时返回 null
     */
    static @Nullable String resolveName(String token, Map<String, String> names, boolean allowAll) {
        String lower = token.toLowerCase(Locale.ROOT);
        if (allowAll && ALL_KEYWORDS.contains(lower)) return ALL_TARGETS;
        return names.get(lower);
    }

    /**
     * 对目标施加提醒效果，必须在服务器主线程调用。
     *
     * @param sender 发送者
     * @param targets 目标及提醒强度
     */
    public void apply(Player sender, Map<Player, MentionParser.Kind> targets) {
        if (targets.isEmpty()) return;
        String senderName = sender.getName();
        String actionBar = settings.pingActionBar();
        Component actionBarComponent = actionBar == null || actionBar.isBlank()
                ? null
                : TextParser.parse(actionBar.replace("{player}", senderName));
        for (Map.Entry<Player, MentionParser.Kind> entry : targets.entrySet()) {
            Player target = entry.getKey();
            if (!target.isOnline()) continue;
            if (actionBarComponent != null) target.sendActionBar(actionBarComponent);
            if (entry.getValue() == MentionParser.Kind.TITLE) {
                showBig(target, senderName);
            } else {
                play(target, settings.pingSound());
            }
        }
    }

    private void showBig(Player target, String senderName) {
        MentionSettings current = settings;
        Component title = TextParser.parse(current.title().replace("{player}", senderName));
        Component subtitle = TextParser.parse(current.subtitle().replace("{player}", senderName));
        target.showTitle(Title.title(title, subtitle, Title.Times.times(
                ticks(current.fadeInTicks()),
                ticks(current.stayTicks()),
                ticks(current.fadeOutTicks())
        )));
        // 与 Beep 一致：title 出现后连响两下
        if (current.bigSound() != null) {
            playLater(target, current.bigSound(), 7);
            playLater(target, current.bigSound(), 14);
        }
    }

    private void playLater(Player target, MentionSound sound, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(MCZJUGameCore.getInstance(), () -> {
            if (target.isOnline()) play(target, sound);
        }, delayTicks);
    }

    private void play(Player target, @Nullable MentionSound sound) {
        if (sound == null || !target.isOnline()) return;
        target.playSound(Sound.sound(sound.key(), Sound.Source.PLAYER, sound.volume(), sound.pitch()));
    }

    /** 消息被隐藏时给发送者回执；回执为空时不发送。 */
    public void sendFeedback(Player sender, Collection<Player> targets) {
        String template = settings.senderFeedback();
        if (template == null || template.isBlank() || targets.isEmpty()) return;
        String names = String.join(", ", targets.stream().map(Player::getName).toList());
        sender.sendMessage(TextParser.parse(template.replace("{targets}", names)));
    }

    /**
     * 消息以提醒标记开头却没有任何目标命中时，私下提示发送者正确的写法，避免毫无反应。
     * 可安全地在异步聊天线程调用。
     *
     * @param sender 发送者
     * @param text 聊天纯文本
     */
    public void hintIfAttempt(Player sender, String text) {
        String template = settings.senderHint();
        if (template == null || template.isBlank()) return;
        if (!text.stripLeading().startsWith("@")) return;
        sender.sendMessage(TextParser.parse(template));
    }

    private static Duration ticks(int ticks) {
        return Duration.ofMillis(ticks * 50L);
    }
}
