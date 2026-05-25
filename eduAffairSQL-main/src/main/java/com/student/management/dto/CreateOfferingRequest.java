package com.student.management.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 课程班新建/修改请求，内含排课时间段列表 */
public record CreateOfferingRequest(
        @NotNull(message = "不能为空") Long courseId,
        @NotNull(message = "不能为空") Long semesterId,
        @NotNull(message = "不能为空") Long teacherId,
        @NotEmpty(message = "至少需要一个上课时间段") List<@Valid OfferingTimeRequest> times,
        @NotNull(message = "不能为空") Integer capacity,
        String status,
        Double examRatio
) {
    /** 单个上课时间段：星期、节次、周次范围、教室、单双周 */
    public record OfferingTimeRequest(
            @NotNull(message = "不能为空") Long classroomId,
            @NotNull(message = "不能为空") @Min(value = 1, message = "星期必须在 1 到 7 之间")
            @Max(value = 7, message = "星期必须在 1 到 7 之间") Integer dayOfWeek,
            @NotNull(message = "不能为空") @Min(value = 1, message = "开始节次不能小于 1")
            @Max(value = 12, message = "开始节次不能大于 12") Integer startSection,
            @NotNull(message = "不能为空") @Min(value = 1, message = "结束节次不能小于 1")
            @Max(value = 12, message = "结束节次不能大于 12") Integer endSection,
            @NotNull(message = "不能为空") @Min(value = 1, message = "起始周不能小于 1")
            @Max(value = 30, message = "起始周不能大于 30") Integer startWeek,
            @NotNull(message = "不能为空") @Min(value = 1, message = "结束周不能小于 1")
            @Max(value = 30, message = "结束周不能大于 30") Integer endWeek,
            @NotBlank(message = "不能为空")
            @Pattern(regexp = "all|odd|even", message = "周次只能是全部、单周或双周") String weekType
    ) {
    }
}
