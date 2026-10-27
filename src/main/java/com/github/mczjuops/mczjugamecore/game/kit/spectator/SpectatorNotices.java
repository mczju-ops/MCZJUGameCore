package com.github.mczjuops.mczjugamecore.game.kit.spectator;

import org.bukkit.Sound;

/**
 * 旁观会话的展示配置：标题、聊天文案与音效。
 *
 * <p>kit 只负责"死亡 → 旁观 → 复活"这套机制，玩家看到什么属于游戏规则。
 * 默认提供一套通用文案，游戏可以逐项替换、整体静音，或关闭内置展示后
 * 用 {@link SpectatorSession#onSpectate} / {@link SpectatorSession#onRevive}
 * 回调自己渲染。
 *
 * <p>文案使用 MiniMessage 语法；含 {@code %d} 的文案会用剩余秒数填充。
 * 每个会话持有自己的实例，互不影响。
 */
public final class SpectatorNotices {

    private boolean chat = true;
    private boolean titles = true;
    private boolean sounds = true;

    private String eliminateChat = "<gray>你已被淘汰，现在以旁观者身份观战";
    private String eliminateTitle = "<red>你已被淘汰";
    private String eliminateSubtitle = "<gray>现在以旁观者身份观战";
    private String awaitingChat = "<yellow>你已阵亡，%d 秒后复活";
    private String countdownText = "<yellow>%d 秒后复活";
    private String reviveChat = "<green>你已复活";
    private String reviveTitle = "<green>你已复活";
    private String reviveSubtitle = "";

    private Sound eliminateSound = Sound.BLOCK_BEACON_DEACTIVATE;
    private Sound countdownSound = Sound.BLOCK_NOTE_BLOCK_HAT;
    private Sound reviveSound = Sound.ENTITY_PLAYER_LEVELUP;
    private float eliminateVolume = 0.6f;
    private float countdownVolume = 0.5f;
    private float reviveVolume = 1.0f;

    /** 创建一套默认展示配置。 */
    public SpectatorNotices() {
    }

    /**
     * 关闭全部内置展示（聊天、标题、音效），但机制照常工作。
     * @return 新建的静音配置
     */
    public static SpectatorNotices silent() {
        return new SpectatorNotices().chat(false).titles(false).sounds(false);
    }

    /** @return 是否发送聊天/动作栏文案 */
    public boolean chat() {
        return chat;
    }

    /**
     * 开关聊天与动作栏文案。
     * @param enabled 是否发送
     * @return 本配置，便于链式编写
     */
    public SpectatorNotices chat(boolean enabled) {
        this.chat = enabled;
        return this;
    }

    /** @return 是否显示淘汰/复活标题 */
    public boolean titles() {
        return titles;
    }

    /**
     * 开关淘汰与复活标题。
     * @param enabled 是否显示
     * @return 本配置，便于链式编写
     */
    public SpectatorNotices titles(boolean enabled) {
        this.titles = enabled;
        return this;
    }

    /** @return 是否播放音效 */
    public boolean sounds() {
        return sounds;
    }

    /**
     * 开关淘汰、倒计时与复活音效。
     * @param enabled 是否播放
     * @return 本配置，便于链式编写
     */
    public SpectatorNotices sounds(boolean enabled) {
        this.sounds = enabled;
        return this;
    }

    /** @return 淘汰时发给玩家的聊天文案 */
    public String eliminateChat() {
        return eliminateChat;
    }

    /** @return 淘汰标题 */
    public String eliminateTitle() {
        return eliminateTitle;
    }

    /** @return 淘汰副标题 */
    public String eliminateSubtitle() {
        return eliminateSubtitle;
    }

    /** @return 等复活开始时的文案，{@code %d} 为秒数 */
    public String awaitingChat() {
        return awaitingChat;
    }

    /** @return 每秒倒计时文案，{@code %d} 为剩余秒数 */
    public String countdownText() {
        return countdownText;
    }

    /** @return 复活时的聊天文案 */
    public String reviveChat() {
        return reviveChat;
    }

    /** @return 复活标题 */
    public String reviveTitle() {
        return reviveTitle;
    }

    /** @return 复活副标题 */
    public String reviveSubtitle() {
        return reviveSubtitle;
    }

    /** @return 淘汰音效 */
    public Sound eliminateSound() {
        return eliminateSound;
    }

    /** @return 倒计时每秒音效 */
    public Sound countdownSound() {
        return countdownSound;
    }

    /** @return 复活音效 */
    public Sound reviveSound() {
        return reviveSound;
    }

    /** @return 淘汰音效音量 */
    public float eliminateVolume() {
        return eliminateVolume;
    }

    /** @return 倒计时音效音量 */
    public float countdownVolume() {
        return countdownVolume;
    }

    /** @return 复活音效音量 */
    public float reviveVolume() {
        return reviveVolume;
    }

    /**
     * 一次性替换淘汰展示文案。
     * @param chat 聊天文案
     * @param title 标题
     * @param subtitle 副标题
     * @return 本配置，便于链式编写
     */
    public SpectatorNotices eliminateText(String chat, String title, String subtitle) {
        if (chat != null) this.eliminateChat = chat;
        if (title != null) this.eliminateTitle = title;
        if (subtitle != null) this.eliminateSubtitle = subtitle;
        return this;
    }

    /**
     * 一次性替换复活展示文案。
     * @param chat 聊天文案
     * @param title 标题
     * @param subtitle 副标题
     * @return 本配置，便于链式编写
     */
    public SpectatorNotices reviveText(String chat, String title, String subtitle) {
        if (chat != null) this.reviveChat = chat;
        if (title != null) this.reviveTitle = title;
        if (subtitle != null) this.reviveSubtitle = subtitle;
        return this;
    }

    /**
     * 替换等复活期间的两条文案。
     * @param awaiting 阵亡时的文案，{@code %d} 为秒数
     * @param countdown 每秒倒计时文案，{@code %d} 为剩余秒数
     * @return 本配置，便于链式编写
     */
    public SpectatorNotices respawnText(String awaiting, String countdown) {
        if (awaiting != null) this.awaitingChat = awaiting;
        if (countdown != null) this.countdownText = countdown;
        return this;
    }

    /**
     * 替换三种音效。
     * @param eliminate 淘汰音效，可为 null 表示不播放
     * @param countdown 倒计时音效，可为 null 表示不播放
     * @param revive 复活音效，可为 null 表示不播放
     * @return 本配置，便于链式编写
     */
    public SpectatorNotices sounds(Sound eliminate, Sound countdown, Sound revive) {
        this.eliminateSound = eliminate;
        this.countdownSound = countdown;
        this.reviveSound = revive;
        return this;
    }
}
