package com.student.management.dto;

import jakarta.validation.constraints.NotNull;

/** 学生退课请求，传选课记录 ID 而非课程班 ID */
public record DropCourseRequest(@NotNull(message = "不能为空") Long enrollmentId) {
}
