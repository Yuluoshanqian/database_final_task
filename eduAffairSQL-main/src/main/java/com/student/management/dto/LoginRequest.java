package com.student.management.dto;

import jakarta.validation.constraints.NotBlank;

/** 登录请求 */
public record LoginRequest(
        @NotBlank(message = "不能为空") String username,
        @NotBlank(message = "不能为空") String password
) {
}
