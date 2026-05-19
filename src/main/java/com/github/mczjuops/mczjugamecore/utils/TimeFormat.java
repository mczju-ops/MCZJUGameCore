package com.github.mczjuops.mczjugamecore.utils;

/**
 * 时间格式化工具类。
 * 不适用于为负的时间
 */
public final class TimeFormat {

    private TimeFormat() {}

    /**
     * 将毫秒格式化为 mm:ss.SSS，保留 3 位小数。
     *
     * <pre>{@code
     * TimeFormat.formatMillis3(61543); // "01:01.543"
     * }</pre>
     *
     * @param millis 毫秒数
     * @return 格式化后的时间字符串
     */
    public static String formatMillis3(long millis) {
        return format(millis, 3);
    }

    /**
     * 将毫秒格式化为 mm:ss.SS，保留 2 位小数。
     *
     * <pre>{@code
     * TimeFormat.formatMillis2(59995); // "01:00.00"
     * }</pre>
     *
     * @param millis 毫秒数
     * @return 格式化后的时间字符串
     */
    public static String formatMillis2(long millis) {
        return format(millis, 2);
    }

    /**
     * 将毫秒格式化为 mm:ss，不保留小数。
     *
     * <pre>{@code
     * TimeFormat.formatMillis0(59500); // "01:00"
     * }</pre>
     *
     * @param millis 毫秒数
     * @return 格式化后的时间字符串
     */
    public static String formatMillis0(long millis) {
        return format(millis, 0);
    }

    /** 通用格式化方法 */
    private static String format(long millis, int decimals) {
        if (decimals < 0 || decimals > 3) {
            throw new IllegalArgumentException("decimals must be between 0 and 3");
        }

        long unit = (long) Math.pow(10, 3 - decimals);
        long roundedMillis = Math.round(millis / (double) unit) * unit;

        long totalSeconds = roundedMillis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        if (decimals == 0) {
            return "%02d:%02d".formatted(minutes, seconds);
        }

        long fractional = roundedMillis % 1000;
        long scaledFractional = fractional / unit;

        return ("%02d:%02d.%0" + decimals + "d")
                .formatted(minutes, seconds, scaledFractional);
    }
}