package com.student.management.controller;

import java.util.List;
import java.util.Map;

import com.student.management.common.ApiResponse;
import com.student.management.dto.DropCourseRequest;
import com.student.management.dto.SelectCourseRequest;
import com.student.management.security.RequireRole;
import com.student.management.security.SessionUser;
import com.student.management.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生控制器，所有接口需要 student 角色。
 * SessionUser 由 CurrentUserArgumentResolver 自动注入，
 * StudentService 从 user.profile 中提取 studentId 用于查询。
 */
@RestController
@RequestMapping("/api/student")
@RequireRole("student")
public class StudentController {
    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping("/offerings")
    public ApiResponse<Map<String, Object>> offerings(SessionUser user, @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(studentService.offerings(user, keyword));
    }

    @PostMapping("/select")
    public ApiResponse<Map<String, Object>> select(SessionUser user, @Valid @RequestBody SelectCourseRequest request) {
        return ApiResponse.ok(studentService.selectCourse(user, request.offeringId()));
    }

    @PostMapping("/drop")
    public ApiResponse<Map<String, Object>> drop(SessionUser user, @Valid @RequestBody DropCourseRequest request) {
        return ApiResponse.ok(studentService.dropCourse(user, request.enrollmentId()));
    }

    @GetMapping("/schedule")
    public ApiResponse<List<Map<String, Object>>> schedule(SessionUser user,
                                                           @RequestParam(required = false) Long semesterId) {
        return ApiResponse.ok(studentService.schedule(user, semesterId));
    }

    @GetMapping("/grades")
    public ApiResponse<List<Map<String, Object>>> grades(SessionUser user) {
        return ApiResponse.ok(studentService.grades(user));
    }

    @GetMapping("/transcript")
    public ApiResponse<Map<String, Object>> transcript(SessionUser user, @RequestParam(required = false) Long semesterId) {
        return ApiResponse.ok(studentService.transcript(user, semesterId));
    }
}
