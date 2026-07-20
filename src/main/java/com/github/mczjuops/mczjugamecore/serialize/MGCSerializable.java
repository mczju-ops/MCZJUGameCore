package com.github.mczjuops.mczjugamecore.serialize;

import com.github.mczjuops.mczjugamecore.game.room.FieldDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface MGCSerializable {

    /**
     * 仅在MGC中调用，根据字段名获取值
     * @param name 字段名
     * @param clazz 字段所属的类型，比如Location, Integer, String等
     * @return  字段的值
     * @param <T>   字段的类型，如Location.class
     */
    public @Nullable <T> T getField(String name, Class<T> clazz);

    /**
     * 设置字段值。仅在本插件开发中有用，其它插件一般用不到
     * @param name  字段名
     * @param value 值
     */
    public void setField(String name, Object value);

    public <T> @NotNull List<T> getList(String name, Class<T> clazz);


    /**
     * 获取所有的字段
     * @return 字段集合，name -> type
     */
    public Map<String, Class<?>> getAllFields();

    public Class<?> getFieldType(String name);

    public boolean save();


    public boolean isModified();

    public void setModified(boolean modified);

    public  boolean deleteData();

    /**
     * 获取某个字段的说明
     * @param fieldName 字段名
     * @return 字段说明，数组中每个元素占一行(如果是用于lore)，若没说明，则返回长度0的数组
     */
    @NotNull default String[] getFieldDescription(String fieldName){
        try {
            Field field = this.getClass().getDeclaredField(fieldName);
            FieldDescription annotation = field.getAnnotation(FieldDescription.class);
            if (annotation == null || annotation.value() == null) return new String[0];
            return annotation.value();
        } catch (NoSuchFieldException e) {
            return new String[0];
        }
    }
}
