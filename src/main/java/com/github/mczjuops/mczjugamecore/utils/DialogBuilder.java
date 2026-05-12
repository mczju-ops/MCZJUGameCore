package com.github.mczjuops.mczjugamecore.utils;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话框（Dialog）的链式构建工具。
 *
 * <p>原生 Dialog API 相当复杂，本类对最常用的场景进行了封装，以链式调用的形式简化构建流程。
 * 作为代价，部分高级功能被舍弃，例如：
 * <ul>
 *   <li>{@link DialogType#multiAction} 等多按钮类型</li>
 *   <li>Body、Input 控件的部分细粒度参数</li>
 *   <li>等等（完整能力请参考 minecraft wiki 的「对话框定义格式」条目）</li>
 * </ul>
 * 如需使用上述功能，可以尝试直接操作原生 API 或拓展此工具类
 *
 * <h2>示例</h2>
 * <p>一个带文本输入框和滑条的，底部有“确认”“取消”两个按钮的对话框：
 * <pre>{@code
 * DialogBuilder.of("<yellow>设置传送点")
 *     .text("<gray>请填写传送点名称与延迟")
 *     .item(ItemStack.of(Material.COMPASS), "<green>目标位置：当前位置")
 *     .textInput("name", "<yellow>传送点名称")
 *     .slider("delay", "<yellow>延迟（秒）", 0, 10, 1, 3)
 *     .showConfirm(player, 150,
 *         "<green>确认", (p, res) -> {
 *             String name  = res.text("name");
 *             int delay = res.intValue("delay");
 *             // ... 处理逻辑
 *         },
 *         "<red>取消", null
 *     );
 * }</pre>
 *
 * <p><b>注意：</b>每个方法均有独立的参数说明，使用前请仔细查看各方法的注释，
 * 尤其是控件的 {@code key}、宽度、有效期等细节。
 *
 * @see DialogBase
 * @see DialogType
 * @see DialogBody
 * @see DialogInput
 */
@SuppressWarnings("UnstableApiUsage")
public class DialogBuilder {

    private final Component title;
    private final List<DialogBody> body = new ArrayList<>();
    private final List<DialogInput> inputs = new ArrayList<>();
    private boolean canCloseWithEscape = true;
    private int uses = 1; // 只允许点击一次
    private Duration lifetime = Duration.ofMinutes(5); // 5 分钟有效期（注意避免内存泄漏）

    public DialogBuilder(Component title) {
        this.title = title;
    }

    /** 快速创建一个 DialogBuilder */
    public static DialogBuilder of(String title) {
        return new DialogBuilder(TextParser.parse(title));
    }

    // === 静态正文 ===

    /** 添加一行文字，支持 MiniMessage */
    public DialogBuilder text(String text) {
        body.add(DialogBody.plainMessage(TextParser.parse(text)));
        return this;
    }

    /** 快捷添加空行（通常是为了文本较少时稍微上下居中，但是请注意不同客户端的界面尺寸不同，呈现效果也不一样） */
    public DialogBuilder emptyLine() {
        body.add(DialogBody.plainMessage(Component.empty()));
        return this;
    }

    /**
     * 添加一行最左边有一个物品的文字
     *
     * @param item 物品
     * @param description 文字，支持 MiniMessage
     * @param showDecorations 是否显示物品额外信息（数量、耐久等）
     * @param showTooltip 是否在鼠标悬停时显示物品提示框
     * @param width 物品图标宽度，默认（也建议） 16
     * @param height 物品图标高度，默认（也建议） 16
     */
    public DialogBuilder item(
            ItemStack item, @Nullable String description,
            boolean showDecorations, boolean showTooltip,
            int width, int height
    ) {
        if (description == null) {
            body.add(
                    DialogBody.item(item)
                            .showDecorations(showDecorations)
                            .showTooltip(showTooltip)
                            .width(width).height(height)
                            .build()
            );
        } else {
            body.add(
                    DialogBody.item(
                            item, DialogBody.plainMessage(TextParser.parse(description)),
                            showDecorations, showTooltip,
                            width, height
                    )
            );
        }
        return this;
    }

    /** 上一个方法的便捷重载，后四个参数使用默认值 */
    public DialogBuilder item(ItemStack item, String description) {
        body.add(
                DialogBody.item(
                        item, DialogBody.plainMessage(TextParser.parse(description)),
                        true, true,
                        16, 16
                )
        );
        return this;
    }

    // === 输入控件 ===

    /**
     * 文本输入框
     *
     * @param key 此控件的键
     * @param label 标签文本，支持 MiniMessage
     * @param maxLength 输入文本的最大长度，默认 32
     * @param width 文本框的宽度，默认 200
     * @param initial 初始文本
     */
    public DialogBuilder textInput(
            String key, String label, int maxLength,
            @Range(from = 1, to = 1024) int width, String initial
    ) {
        inputs.add(
                DialogInput.text(
                        key, width,
                        TextParser.parse(label), true,
                        initial, maxLength, null
                )
        );
        return this;
    }

    /** 上一个方法的便捷重载 */
    public DialogBuilder textInput(String key, String label) {
        inputs.add(DialogInput.text(key, TextParser.parse(label)).build());
        return this;
    }

    /**
     * 数值滑条
     *
     * @param key 此控件的键
     * @param label 标签文本，支持 MiniMessage
     * @param start 滑条在最左侧时代表的数字
     * @param end 滑条在最右侧时代表的数字
     * @param step 滑条每步进一次的数值变化量
     * @param initial 滑条初始时的数字
     *
     * */
    public DialogBuilder slider(
            String key, String label,
            float start, float end,
            float step, float initial
    ) {
        inputs.add(DialogInput.numberRange(key, TextParser.parse(label), start, end)
                .initial(initial).step(step)
                .build());
        return this;
    }

    /**
     * 布尔开关
     *
     * @param key 此控件的键
     * @param label 标签文本，支持 MiniMessage
     * @param initial 初始值
     * @param onTrue 复选框选中时输入的真实值（默认为 "true"）
     * @param onFalse 复选框未选中时输入的真实值（默认为 "false"）
     */
    public DialogBuilder toggle(String key, String label, boolean initial, String onTrue, String onFalse) {
        inputs.add(DialogInput.bool(key, TextParser.parse(label), initial, onTrue, onFalse));
        return this;
    }

    /** 上一个方法的便捷重载 */
    public DialogBuilder toggle(String key, String label, boolean initial) {
        inputs.add(DialogInput.bool(key, TextParser.parse(label), initial, "true", "false"));
        return this;
    }

    // === 全局选项（按需设置，一般不需要） ===

    /** 禁止按 Esc 关闭 */
    public DialogBuilder noEscape() {
        this.canCloseWithEscape = false;
        return this;
    }

    /** 回调可触发次数（默认 1） */
    public DialogBuilder uses(int uses) {
        this.uses = uses;
        return this;
    }

    /** 回调有效期（默认 5 分钟） */
    public DialogBuilder lifetime(Duration lifetime) {
        this.lifetime = lifetime;
        return this;
    }

    // === 终端方法 ===

    /**
     * notice 对话框：底部有一个按钮
     *
     * @param player 为此玩家打开对话框
     * @param width 按钮的宽度，默认 150
     * @param buttonText 按钮文本，支持 MiniMessage
     * @param callback 按钮回调
     */
    public void showNotice(
            Player player,
            @Range(from = 1L, to = 1024L) int width,
            String buttonText,
            @Nullable Callback callback
    ) {
        show(player, DialogType.notice(makeButton(width,TextParser.parse(buttonText), callback)));
    }

    /**
     * confirmation 对话框：底部有两个按钮
     *
     * @param player 为此玩家打开对话框
     * @param width 按钮的宽度，默认 150
     * @param leftText 左侧按钮的文本，支持 MiniMessage
     * @param onClickLeft 左侧按钮回调
     * @param rightText 右侧按钮的文本，支持 MiniMessage
     * @param onClickRight 右侧按钮回调
     */
    public void showConfirm(
            Player player,
            @Range(from = 1L, to = 1024L) int width,
            String leftText, @Nullable Callback onClickLeft,
            String rightText, @Nullable Callback onClickRight) {
        show(player, DialogType.confirmation(
                makeButton(width, TextParser.parse(leftText), onClickLeft),
                makeButton(width, TextParser.parse(rightText), onClickRight)
        ));
    }

    // === 内部构建逻辑 ===

    private void show(Player player, DialogType type) {
        Dialog dialog = Dialog.create(b -> b.empty()
                .base(buildBase())
                .type(type)
        );
        player.showDialog(dialog);
    }

    private DialogBase buildBase() {
        DialogBase.Builder base = DialogBase.builder(title)
                .canCloseWithEscape(canCloseWithEscape);
        if (!body.isEmpty()) base.body(body);
        if (!inputs.isEmpty()) base.inputs(inputs);
        return base.build();
    }

    private ClickCallback.Options buildOptions() {
        return ClickCallback.Options.builder()
                .uses(uses)
                .lifetime(lifetime)
                .build();
    }

    private ActionButton makeButton(int width, Component label, @Nullable Callback callback) {
        ActionButton.Builder btn = ActionButton.builder(label);
        if (callback != null) {
            btn.action(DialogAction.customClick(
                    (view, audience) -> {
                        if (audience instanceof Player p) {
                            callback.handle(p, new Response(view));
                        }
                    },
                    buildOptions()
            ));
        }
        return btn.width(width).build();
    }

    /** 直接拿到 Player 和包装后的 Response */
    @FunctionalInterface
    public interface Callback {
        void handle(Player player, Response response);
    }

    /** 对 DialogResponseView 的轻量包装，提供语义化取值 */
    public record Response(DialogResponseView view) {

        /** 获取文本输入的值 */
        public String text(String key) {
            return view.getText(key);
        }

        /** 获取滑条的浮点值 */
        public float floatValue(String key) {
            Float value = view.getFloat(key);
            if (value == null) return 0f;
            else return value;
        }

        /** 获取滑条的整数值（截断小数） */
        public int intValue(String key) {
            return (int) floatValue(key);
        }

        /** 获取布尔开关的值 */
        public boolean bool(String key) {
            return Boolean.TRUE.equals(view.getBoolean(key));
        }
    }
}
