package com.github.mczjuops.mczjugamecore.achievement;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.menu.Menu;
import com.github.mczjuops.mczjugamecore.statistics.GameStatistic;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 玩家自己的统计/成就箱子菜单；游戏列表及成就列表均分页，支持筛选和手动刷新。 */
public final class AchievementMenu extends Menu {
    private final String gameId;
    private boolean statistics;
    private AchievementFilter filter = AchievementFilter.ALL;
    private int page;
    /** 打开游戏选择页；statistics 为 true 时选择游戏后显示统计。 */
    public AchievementMenu(Player player, boolean statistics) { this(player, null, statistics); }
    /** 打开指定游戏；gameId 为 null 时显示所有已注册游戏。 */
    public AchievementMenu(Player player, String gameId, boolean statistics) {
        super(player, new Object[0], "我的统计与成就");
        this.gameId = gameId;
        this.statistics = statistics;
    }
    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> {
            meta.displayName(TextParser.parseNonItalic(name).colorIfAbsent(NamedTextColor.WHITE));
            meta.lore(lore.stream().map(line -> TextParser.parseNonItalic(line).colorIfAbsent(NamedTextColor.GRAY)).toList());
        });
        return item;
    }
    @Override protected void setup() {
        if (gameId == null) { games(); return; }
        if (!MCZJUGameCore.getGameManager().getRegisteredGameIds().contains(gameId)) {
            setSlot(22, item(Material.BARRIER, "此游戏当前不可用", List.of()));
        } else if (statistics) {
            statistics();
        } else {
            achievements();
        }
        setSlot(45, item(Material.ARROW, "返回游戏列表", List.of()),
                (p, e) -> new AchievementMenu(p.player(), statistics).open());
        setSlot(48, item(Material.COMPASS, statistics ? "查看成就" : "查看统计", List.of()),
                (p, e) -> { statistics = !statistics; page = 0; refresh(); });
        setSlot(50, item(Material.CLOCK, "刷新", List.of()), (p, e) -> refresh());
        if (!statistics) setSlot(49, item(Material.HOPPER, "筛选：" + switch (filter) {
            case ALL -> "全部"; case UNLOCKED -> "已达成"; case LOCKED -> "未达成";
        }, List.of("点击切换", "原生成就树可按 L 查看（默认按键）")), (p, e) -> {
            filter = AchievementFilter.values()[(filter.ordinal() + 1) % AchievementFilter.values().length];
            page = 0;
            refresh();
        });
    }
    private void statistics() {
        var manager = MCZJUGameCore.getGameStatisticsManager();
        var builtin = GameStatistic.values();
        var custom = manager.getDisplays(gameId);
        int total = builtin.length + custom.size();
        clamp(total);
        for (int index = page * 36; index < Math.min(total, (page + 1) * 36); index++) {
            if (index < builtin.length) {
                GameStatistic stat = builtin[index];
                long value = manager.get(player.getUniqueId(), gameId, stat);
                String formatted = value == 0 ? "暂无统计信息" : stat == GameStatistic.PLAY_TIME_SECONDS
                        ? "%d 小时 %d 分 %d 秒".formatted(value / 3600, value / 60 % 60, value % 60) : Long.toString(value);
                setSlot(index % 36, item(stat.icon(), "<gold>" + stat.displayName(), List.of(gameName(), formatted)));
            } else {
                var display = custom.get(index - builtin.length);
                String formatted;
                try {
                    formatted = Objects.requireNonNullElse(display.value().apply(player.getUniqueId()), "暂无统计信息");
                } catch (RuntimeException exception) {
                    formatted = "暂时无法读取";
                    MCZJUGameCore.getInstance().getLogger().log(java.util.logging.Level.WARNING,
                            "Cannot read statistic display: " + display.key(), exception);
                }
                setSlot(index % 36, item(display.icon(), "<gold>" + display.name(), List.of(gameName(), formatted)));
            }
        }
        navigation(total);
    }
    private void games() {
        List<String> games = MCZJUGameCore.getGameManager().getRegisteredGameIds().stream().sorted().toList();
        clamp(games.size());
        for (int index = page * 36; index < Math.min(games.size(), (page + 1) * 36); index++) {
            String game = games.get(index);
            var meta = MCZJUGameCore.getGameManager().getGameMetas().get(game);
            var lore = new ArrayList<>(meta.description());
            lore.add("");
            lore.add("<yellow>点击查看我的" + (statistics ? "统计" : "成就"));
            setSlot(index % 36, ItemBuilder.of(Material.CLOCK).customName(meta.displayName())
                    .itemModel(meta.icon()).lore(lore).build(),
                    (p, e) -> new AchievementMenu(p.player(), game, statistics).open());
        }
        if (games.isEmpty()) setSlot(22, item(Material.PAPER, "暂无已注册游戏", List.of()));
        navigation(games.size());
        setSlot(49, item(Material.CLOCK, "刷新游戏列表", List.of()), (p, e) -> refresh());
    }
    private void achievements() {
        var manager = MCZJUGameCore.getAchievementManager();
        var all = manager.getDefinitions(gameId);
        long completed = all.stream().filter(d -> manager.getProgress(player.getUniqueId(), d.key()).unlocked()).count();
        var filtered = all.stream().filter(d -> {
            boolean unlocked = manager.getProgress(player.getUniqueId(), d.key()).unlocked();
            return filter == AchievementFilter.ALL || (filter == AchievementFilter.UNLOCKED) == unlocked;
        }).toList();
        clamp(filtered.size());
        for (int index = page * 36; index < Math.min(filtered.size(), (page + 1) * 36); index++) {
            var definition = filtered.get(index);
            var progress = manager.getProgress(player.getUniqueId(), definition.key());
            var global = manager.getStats(definition.key());
            List<String> lore = new ArrayList<>();
            lore.add(definition.description());
            lore.add("进度：" + progress.progress() + " / " + definition.target());
            if (progress.unlocked()) lore.add("解锁时间：" + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(progress.unlockedAt())));
            double completionRatio = global.completionRatio();
            lore.add("全服达成：" + rarityColor(completionRatio)
                    + String.format(Locale.ROOT, "%.2f%%", completionRatio * 100));
            if (definition.parent() != null) manager.getDefinitions(gameId).stream()
                    .filter(parent -> parent.key().equals(definition.parent())).findFirst()
                    .ifPresent(parent -> lore.add("<gray>父节点：<reset>" + parent.name()));
            ItemStack display = item(definition.icon(),
                    (progress.unlocked() ? "<green>[已达成]</green> " : "<gray>[未达成]</gray> ")
                            + "<gold>" + definition.name(), lore);
            display.editMeta(meta -> meta.setEnchantmentGlintOverride(progress.unlocked()));
            setSlot(index % 36, display);
        }
        if (filtered.isEmpty()) setSlot(22, item(Material.PAPER, "此筛选下暂无成就", List.of()));
        var meta = MCZJUGameCore.getGameManager().getGameMetas().get(gameId);
        setSlot(40, item(meta.icon(), "<gold>我的完成数量：" + completed + " / " + all.size(), List.of(gameName())));
        navigation(filtered.size());
    }
    /** 全服达成率越低，颜色越稀有：金、紫、蓝、绿、灰。 */
    private String rarityColor(double completionRatio) {
        if (completionRatio <= 0.01) return "<gold>";
        if (completionRatio <= 0.05) return "<light_purple>";
        if (completionRatio <= 0.10) return "<blue>";
        if (completionRatio <= 0.25) return "<green>";
        return "<gray>";
    }
    private String gameName() {
        var meta = MCZJUGameCore.getGameManager().getGameMetas().get(gameId);
        return meta == null ? "未知游戏" : meta.displayName();
    }
    private void clamp(int size) { page = Math.min(page, Math.max(0, (size - 1) / 36)); }
    private void navigation(int size) {
        if (page > 0) setSlot(46, item(Material.ARROW, "上一页", List.of()), (p, e) -> { page--; refresh(); });
        if ((page + 1) * 36 < size) setSlot(53, item(Material.ARROW, "下一页", List.of()), (p, e) -> { page++; refresh(); });
    }
    @Override protected String getTitle() { return "我的统计与成就"; }
    @Override protected int getRows() { return 6; }
    @Override protected String getPermission() { return "mgc.mgc"; }
}
