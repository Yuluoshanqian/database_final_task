package com.student.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;

/** 教师新建/修改请求，初始密码默认为工号 */
public record TeacherRequest(
        @NotBlank(message = "不能为空") String teacherNo,
        @NotBlank(message = "不能为空") String name,
        @NotBlank(message = "不能为空") @Email(message = "邮箱格式不正确") String email,
        @NotNull(message = "不能为空") Long departmentId,
        @NotBlank(message = "不能为空") String title
) {
}
