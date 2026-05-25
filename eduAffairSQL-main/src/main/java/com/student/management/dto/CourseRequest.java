package com.student.management.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 课程新建请求，默认状态为 enabled */
public record CourseRequest(
        @NotBlank(message = "不能为空") String code,
        @NotBlank(message = "不能为空") String name,
        @NotNull(message = "不能为空") Long departmentId,
        @NotNull(message = "不能为空") @DecimalMin(value = "0.5", message = "学分必须大于 0") Double credit
) {
}
