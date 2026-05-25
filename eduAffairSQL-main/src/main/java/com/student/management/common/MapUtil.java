package com.student.management.common;

import java.util.Map;

/**
 * 从 Map 中安全提取类型化值的工具。
 * MyBatis 返回的查询结果是 {@code Map<String, Object>}，其中的数值可能是
 * Long/Integer/BigDecimal 等不同类型，需要灵活转换。
 */
public final class MapUtil {
    private MapUtil() {
    }

    /**
     * 提取 long 值，兼容 Number 及其子类和字符串表示。
     */
    public static long longValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    /**
     * 提取字符串值，null 返回 null（不转成 "null" 字符串）。
     */
    public static String stringValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 提取布尔值，兼容 Boolean、Number（0=false, 非0=true）、
     * 以及 MySQL 的 BIT 类型返回的 "1"/"0" 字符串。
     */
    public static boolean booleanValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = String.valueOf(value);
        return "1".equals(text) || Boolean.parseBoolean(text);
    }
}
