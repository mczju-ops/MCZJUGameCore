package com.github.mczjuops.mczjugamecore.game.selection;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.menu.Menu;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * GUI 目标选择器：向选择者弹出箱子菜单，列出可选玩家头颅，点击即选中目标。
 *
 * <p>基于 {@link Menu} 与 {@link ItemBuilder} 实现。默认关闭菜单即视为取消；
 * 传入 {@code requireSelection(true)} 后必须做出选择，否则菜单会被重新打开（超时请用 {@link #cancel()} 终止）。
 *
 * <pre>{@code
 * PlayerTargetSelector.builder()
 *     .source(currentPlayer)
 *     .candidates(game.getPlayers())
 *     .filter(p -> !p.equals(currentPlayer) && p.player().isOnline())
 *     .title("<gold>选择攻击目标")
 *     .requireSelection(true)    // 可选：关闭界面不算数，必须选一个
 *     .onSelect(target -> { })   // 已选中目标
 *     .onCancel(() -> { })       // 取消选择（仅非强制模式下可能触发）
 *     .build()
 *     .open();
 * }</pre>
 */
public final class PlayerTargetSelector {

    private final PlayerExt source;
    private final List<PlayerExt> candidates;
    private final Consumer<PlayerExt> onSelect;
    private final Runnable onCancel;
    private final String title;
    private final boolean requireSelection;

    private final SelectionMenu menu;
    private boolean finished;

    private PlayerTargetSelector(Builder builder) {
        this.source = builder.source;
        this.candidates = List.copyOf(filterCandidates(
                builder.candidates,
                candidate -> candidate != null
                        && candidate.player().isOnline()
                        && (!builder.excludeSource || !candidate.equals(builder.source))
                        && (builder.keep == null || builder.keep.test(candidate))));
        this.onSelect = builder.onSelect;
        this.onCancel = builder.onCancel;
        this.title = builder.title;
        this.requireSelection = builder.requireSelection;
        this.menu = new SelectionMenu(source.player(), this);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 打开选择菜单；若没有可选目标，直接触发取消回调。 */
    public void open() {
        if (candidates.isEmpty()) {
            cancelFlow();
            return;
        }
        menu.open();
    }

    /** 取消当前选择流程：关闭菜单且不触发 onCancel（用于超时等外部终止）。 */
    public void cancel() {
        if (finished) {
            return;
        }
        finished = true;
        source.player().closeInventory();
    }

    /** @return 当前可选目标列表（不可变快照） */
    public List<PlayerExt> getCandidates() {
        return candidates;
    }

    private void select(PlayerExt target) {
        if (finished) {
            return;
        }
        finished = true;
        // 先关闭菜单再回调：回调里通常会立刻开启下一个界面（如下一回合的选人菜单），
        // 若顺序反过来，新界面会被紧随其后的 closeInventory() 一并关掉。
        source.player().closeInventory();
        if (onSelect != null) {
            onSelect.accept(target);
        }
    }

    private void close() {
        if (finished) {
            return;
        }
        if (requireSelection && source.player().isOnline()) {
            // 强制选择模式：关掉界面不算取消，下一 tick 重新打开（不能在关闭事件里直接开）
            Bukkit.getScheduler().runTask(MCZJUGameCore.getInstance(), this::reopen);
            return;
        }
        cancelFlow();
    }

    /** 点击「跳过/取消」按钮：关闭界面并触发取消回调。 */
    private void skip() {
        if (finished) {
            return;
        }
        finished = true;
        source.player().closeInventory();
        if (onCancel != null) {
            onCancel.run();
        }
    }

    private void reopen() {
        if (finished) {
            return;
        }
        if (!source.player().isOnline()) {
            cancelFlow();
            return;
        }
        source.sender().warn("<yellow>必须选择一名目标");
        menu.open();
    }

    /** 结束流程并触发取消回调；关闭界面由调用方负责。 */
    private void cancelFlow() {
        if (finished) {
            return;
        }
        finished = true;
        if (onCancel != null) {
            onCancel.run();
        }
    }

    /**
     * 候选过滤：保留满足 {@code keep} 的元素。
     *
     * @param candidates 原始候选
     * @param keep       保留条件；为 null 时保留全部
     * @param <T>        元素类型
     * @return 过滤后的列表（保持原顺序）
     */
    static <T> List<T> filterCandidates(List<T> candidates, Predicate<T> keep) {
        List<T> result = new ArrayList<>();
        if (candidates == null) {
            return result;
        }
        for (T candidate : candidates) {
            if (keep == null || keep.test(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private static ItemStack head(PlayerExt target) {
        ItemStack item = ItemBuilder.of(Material.PLAYER_HEAD)
                .customName("<yellow>" + target.getName())
                .lore(List.of("<gray>点击选择该玩家"))
                .build();
        item.editMeta(meta -> {
            if (meta instanceof SkullMeta skull) {
                skull.setPlayerProfile(target.player().getPlayerProfile());
            }
        });
        return item;
    }

    /** 跳过按钮的布局：允许跳过时占一个额外槽位，因此行数按「候选数 + 1」计算。 */
    private static int slotCountFor(int candidateSize, boolean withSkipButton) {
        return candidateSize + (withSkipButton ? 1 : 0);
    }

    /** 按候选数量计算所需的箱子行数（每行 9 格，最多 6 行）。 */
    static int rowsFor(int size) {
        if (size <= 0) {
            return 1;
        }
        return Math.clamp((size + 8) / 9, 1, 6);
    }

    private static final class SelectionMenu extends Menu {

        private final PlayerTargetSelector selector;

        SelectionMenu(Player player, PlayerTargetSelector selector) {
            // Menu 构造器在 super() 期间就会调用 getRows()，此时 selector 字段尚未赋值，
            // 因此把行数放进 args 传入（Menu 在创建 inventory 之前已经赋值 args）。
            super(player, new Object[]{rowsFor(
                    slotCountFor(selector.candidates.size(), !selector.requireSelection))}, selector.title);
            this.selector = selector;
        }

        @Override
        protected void setup() {
            List<PlayerExt> targets = selector.candidates;
            for (int i = 0; i < targets.size(); i++) {
                PlayerExt target = targets.get(i);
                setSlot(i, head(target), (clicker, event) -> selector.select(target));
            }
            int skipSlot = getInventory().getSize() - 1;
            if (!selector.requireSelection && targets.size() <= skipSlot) {
                setSlot(skipSlot, skipButton(), (clicker, event) -> selector.skip());
            }
        }

        private static ItemStack skipButton() {
            return ItemBuilder.of(Material.BARRIER)
                    .customName("<red>跳过 / 取消")
                    .lore(List.of("<gray>放弃本次选择"))
                    .build();
        }

        @Override
        protected String getTitle() {
            return selector.title;
        }

        @Override
        protected @Range(from = 1, to = 6) int getRows() {
            return (int) args[0];
        }

        @Override
        protected String getPermission() {
            return "";
        }

        /**
         * 跳过 {@link Menu#open()} 的权限检查。
         *
         * <p>父类用 {@code hasPermission(getPermission())} 做门槛，而空权限字符串对普通玩家恒为
         * false（只有 OP 会因绕过权限判定而通过），会让非 OP 玩家永远看不到这个界面。
         * 目标选择是游戏内必经流程，不应当受权限控制。
         */
        @Override
        public void open() {
            setup();
            player.player().openInventory(getInventory());
        }

        @Override
        public void handleClose() {
            selector.close();
        }
    }

    public static final class Builder {

        private PlayerExt source;
        private List<PlayerExt> candidates = new ArrayList<>();
        private Predicate<PlayerExt> keep;
        private boolean excludeSource = true;
        private boolean requireSelection;
        private String title = "<gold>选择目标玩家";
        private Consumer<PlayerExt> onSelect;
        private Runnable onCancel;

        /** @param source 进行选择操作的玩家 */
        public Builder source(PlayerExt source) {
            this.source = source;
            return this;
        }

        /** @param candidates 候选目标（通常为房间内所有玩家） */
        public Builder candidates(List<PlayerExt> candidates) {
            this.candidates = candidates;
            return this;
        }

        /** @param keep 额外过滤条件（如排除已淘汰玩家） */
        public Builder filter(Predicate<PlayerExt> keep) {
            this.keep = keep;
            return this;
        }

        /** @param excludeSource 是否自动排除选择者自己，默认 true */
        public Builder excludeSource(boolean excludeSource) {
            this.excludeSource = excludeSource;
            return this;
        }

        /**
         * 是否要求必须选出结果。
         *
         * <p>默认 false：玩家关闭界面即视为放弃，触发 {@code onCancel}。
         * 为 true 时关闭界面不算放弃，会在下一 tick 重新打开菜单，直到选中目标或用
         * {@link PlayerTargetSelector#cancel()} 外部终止（例如回合超时）。
         *
         * @param requireSelection 是否强制选择
         */
        public Builder requireSelection(boolean requireSelection) {
            this.requireSelection = requireSelection;
            return this;
        }

        /** @param title 菜单标题（MiniMessage），默认 &lt;gold&gt;选择目标玩家 */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /** @param onSelect 选中目标后的回调 */
        public Builder onSelect(Consumer<PlayerExt> onSelect) {
            this.onSelect = onSelect;
            return this;
        }

        /** @param onCancel 取消选择（关闭菜单或候选为空）后的回调 */
        public Builder onCancel(Runnable onCancel) {
            this.onCancel = onCancel;
            return this;
        }

        public PlayerTargetSelector build() {
            if (source == null) {
                throw new IllegalArgumentException("source 不能为 null");
            }
            if (!source.player().isOnline()) {
                throw new IllegalStateException("source 玩家不在线，无法进行目标选择");
            }
            return new PlayerTargetSelector(this);
        }
    }
}
