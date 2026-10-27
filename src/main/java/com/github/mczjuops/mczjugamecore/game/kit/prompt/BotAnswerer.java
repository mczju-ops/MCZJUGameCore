package com.github.mczjuops.mczjugamecore.game.kit.prompt;

/**
 * 人机的回答策略。
 *
 * <p>集中在这里的意义：SoulCard 之前把「这是不是人机」散在 36 个调用点上，
 * 每个效果自己调 BotBrain；收进来以后，所有询问对人机都走同一条路，
 * {@code pickXxx} 之类的包装不必再判断人机。
 *
 * <p>返回 {@code null} 表示「无法决定」，由 {@link AskOptions#onTimeout()} 的保底策略接管。
 */
public interface BotAnswerer {

    <T> T answer(AskRequest<T> request);
}
