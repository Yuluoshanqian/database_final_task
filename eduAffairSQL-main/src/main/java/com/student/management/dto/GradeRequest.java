package com.student.management.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 教师录入成绩请求，成绩范围 0-100 */
public record GradeRequest(
        @NotNull(message = "不能为空") Long enrollmentId,
        @NotNull(message = "不能为空") @Min(value = 0, message = "不能小于0") @Max(value = 100, message = "不能大于100") Double usualScore,
        @NotNull(message = "不能为空") @Min(value = 0, message = "不能小于0") @Max(value = 100, message = "不能大于100") Double examScore
) {
}
