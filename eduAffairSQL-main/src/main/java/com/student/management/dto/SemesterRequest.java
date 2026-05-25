package com.student.management.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 学期新建/修改请求，日期格式 yyyy-MM-dd */
public record SemesterRequest(
        @NotBlank(message = "不能为空") String name,
        @NotBlank(message = "不能为空") String startDate,
        @NotBlank(message = "不能为空") String endDate,
        @NotNull(message = "不能为空")
        @DecimalMin(value = "1.0", message = "最大学分必须大于 0")
        BigDecimal maxCredit
) {
}
