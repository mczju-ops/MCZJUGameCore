package com.github.mczjuops.mczjugamecore.game.room;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldDescription {
    String[] value() default {};
}