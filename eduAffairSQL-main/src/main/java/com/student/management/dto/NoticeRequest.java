package com.student.management.dto;

import jakarta.validation.constraints.NotBlank;

/** 通知公告请求，audience: all/teacher/student */
public record NoticeRequest(
        @NotBlank(message = "不能为空") String title,
        @NotBlank(message = "不能为空") String content,
        @NotBlank(message = "不能为空") String audience
) {
}
