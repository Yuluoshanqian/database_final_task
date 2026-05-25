package com.student.management.dto;

import jakarta.validation.constraints.NotBlank;

/** 修改密码请求 */
public record ChangePasswordRequest(
        @NotBlank(message = "不能为空") String oldPassword,
        @NotBlank(message = "不能为空") String newPassword
) {
}
