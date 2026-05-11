package com.github.mczjuops.mczjugamecore.utils.countdown;

import java.util.function.Consumer;

public enum CountDownType {
    SAMPLE(
            "广播样例",
            "标题样例",
            "完成文本样例",
            (ctx) -> {
                //自定义逻辑和触发事件
            }
    );

    private final String broadcastMessagePrefix;
    private final String titleSuffix;
    private final String completionMessage;
    private final Consumer<CountdownContext> onComplete;

    CountDownType(String broadcastMessagePrefix,
                  String titleSuffix,
                  String completionMessage,
                  Consumer<CountdownContext> onComplete) {
        this.broadcastMessagePrefix = broadcastMessagePrefix;
        this.titleSuffix = titleSuffix;
        this.completionMessage = completionMessage;
        this.onComplete = onComplete;
    }

    public String getBroadcastMessagePrefix() { return broadcastMessagePrefix; }

    public String getTitleSuffix() { return titleSuffix; }

    public String getCompletionMessage() { return completionMessage; }

    public Consumer<CountdownContext> getOnComplete() { return onComplete; }

    // 上下文对象，用于在回调中访问必要组件
    public record CountdownContext(CountDown counter) {}
}