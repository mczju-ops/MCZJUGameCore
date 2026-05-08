package com.github.mczjuops.mczjugamecore.utils;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class CommandUtils {

    /**
     * 对候选集合进行前缀匹配补全，不区分大小写。
     * @param candidates 候选字符串集合
     * @param builder SuggestionsBuilder，由 Brigadier 提供
     * @return CompletableFuture，用于 Brigadier 的异步补全机制
     */
    public static CompletableFuture<Suggestions> suggestMatching(
            Collection<String> candidates,
            SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemaining().toLowerCase();

        for (String s : candidates) {
            if (s == null) continue;
            if (s.toLowerCase().startsWith(remaining)) {
                builder.suggest(s);
            }
        }
        return builder.buildFuture();
    }

    /**
     * 从输入字符串（例如 "/cmd sub arg1 arg2"）中按空格拆分，并取对应 token。
     * @param builder SuggestionsBuilder
     * @param index   token 索引（从 0 开始）
     * @return token 字符串；若 index 越界则返回 null
     */
    public static @Nullable String getToken(SuggestionsBuilder builder, int index) {
        String input = builder.getInput().trim();

        // 去除命令前面的 "/"，避免 token[0] 出现 "" 的情况
        if (input.startsWith("/")) {
            input = input.substring(1);
        }

        String[] tokens = input.split("\\s+");

        if (index < 0 || index >= tokens.length) return null;
        return tokens[index];
    }
}
