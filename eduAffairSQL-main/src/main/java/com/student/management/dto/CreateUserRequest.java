package com.student.management.dto;

import jakarta.validation.constraints.NotBlank;

/** 管理员新建用户请求（不含学生/教师扩展信息） */
public record CreateUserRequest(
        @NotBlank(message = "不能为空") String username,
        @NotBlank(message = "不能为空") String password,
        @NotBlank(message = "不能为空") String displayName,
        @NotBlank(message = "不能为空") String role
) {
}
