package com.github.mczjuops.mczjugamecore.score.leaderboard;

import java.lang.annotation.*;

/**
 * 适用于 {@link AbstractLeaderboard} 子类的注解，表明该排行榜需要由 MGC 定期刷新。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AutoRefresh {
}
