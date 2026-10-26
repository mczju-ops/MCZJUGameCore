package com.github.mczjuops.mczjugamecore.mention;

import net.kyori.adventure.key.Key;

/**
 * 一条提醒音效配置。
 *
 * @param key 音效键名
 * @param volume 音量
 * @param pitch 音调
 */
public record MentionSound(Key key, float volume, float pitch) {
}
