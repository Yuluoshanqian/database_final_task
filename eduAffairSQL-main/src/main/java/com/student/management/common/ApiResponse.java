package com.student.management.common;

/**
 * 统一 API 响应体，所有接口返回此格式的 JSON。
 * 使用 Java 17 record 保证不可变性，前端统一解析 success/data/message 三个字段。
 */
public record ApiResponse<T>(boolean success, T data, String message) {

    /**
     * 成功响应，message 为 null（前端不需要时省略）。
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * 失败响应，data 为 null（仅传达错误信息）。
     */
    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
