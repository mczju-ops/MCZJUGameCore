package com.github.mczjuops.mczjugamecore.game.room;

import java.lang.annotation.*;

/**
 * 适用于 {@link AbstractGameRoom} 子类的注解，表明玩家可以主动选择加入哪个房间。
 *
 * <p>约定：使用该注解的子类，需要有以下三个 public 字段（均为字符串）：
 * <ul>
 *     <li>icon：该房间的图标</li>
 *     <li>displayName：该房间的显示名</li>
 *     <li>description：该房间的描述</li>
 * </ul>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PlayerSelectable {
}
