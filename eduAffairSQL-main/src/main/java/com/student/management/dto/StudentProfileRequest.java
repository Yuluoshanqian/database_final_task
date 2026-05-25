package com.student.management.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 学生新建/修改请求，初始密码默认为学号 */
public record StudentProfileRequest(
        @NotBlank(message = "不能为空") String studentNo,
        @NotBlank(message = "不能为空") String name,
        @NotBlank(message = "不能为空") @Email(message = "邮箱格式不正确") String email,
        @NotNull(message = "不能为空") Long majorId,
        @NotNull(message = "不能为空") Integer admissionYear
) {
}
