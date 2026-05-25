package com.student.management.dto;

import jakarta.validation.constraints.NotNull;

/** 学生选课请求，传课程班 ID */
public record SelectCourseRequest(@NotNull(message = "不能为空") Long offeringId) {
}
