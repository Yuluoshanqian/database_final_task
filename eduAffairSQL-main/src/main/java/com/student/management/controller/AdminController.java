package com.student.management.controller;

import java.util.List;
import java.util.Map;

import com.student.management.common.ApiResponse;
import com.student.management.dto.CourseRequest;
import com.student.management.dto.CreateOfferingRequest;
import com.student.management.dto.CreateUserRequest;
import com.student.management.dto.NoticeRequest;
import com.student.management.dto.SemesterRequest;
import com.student.management.dto.StudentProfileRequest;
import com.student.management.dto.TeacherRequest;
import com.student.management.security.RequireRole;
import com.student.management.security.SessionUser;
import com.student.management.service.AdminService;
import com.student.management.service.BackupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员控制器，所有接口需要 admin 角色。
 *
 * 路由分组：
 * /users           — 用户管理（查看、新增、删除）
 * /teachers        — 教师管理（查看、新增、修改、启用/弃用）
 * /students        — 学生管理（查看、新增、修改、启用/弃用）
 * /courses         — 课程管理（查看、新增、启用/弃用）
 * /offerings       — 课程班管理（查看、新增、修改、删除）
 * /semesters       — 学期管理（新增、修改）+ 选课/登分阶段控制
 * /teaching        — 管理员代学生选课/退课
 * /notices         — 通知管理
 * /logs            — 事务审计日志
 * /backups         — 数据库备份
 */
@RestController
@RequestMapping("/api/admin")
@RequireRole("admin")
public class AdminController {
    private final AdminService adminService;
    private final BackupService backupService;

    public AdminController(AdminService adminService, BackupService backupService) {
        this.adminService = adminService;
        this.backupService = backupService;
    }

    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> users() {
        return ApiResponse.ok(adminService.listUsers());
    }

    @PostMapping("/users")
    public ApiResponse<Map<String, Object>> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(adminService.createUser(request));
    }

    @DeleteMapping("/users/{userId}")
    public ApiResponse<Map<String, Object>> deleteUser(SessionUser user, @PathVariable Long userId) {
        return ApiResponse.ok(adminService.deleteUser(user, userId));
    }

    @GetMapping("/teachers")
    public ApiResponse<List<Map<String, Object>>> teachers(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(adminService.listTeachers(keyword));
    }

    @PostMapping("/teachers")
    public ApiResponse<Map<String, Object>> createTeacher(@Valid @RequestBody TeacherRequest request) {
        return ApiResponse.ok(adminService.createTeacher(request));
    }

    @PutMapping("/teachers/{teacherId}")
    public ApiResponse<Map<String, Object>> updateTeacher(@PathVariable Long teacherId,
                                                          @Valid @RequestBody TeacherRequest request) {
        return ApiResponse.ok(adminService.updateTeacher(teacherId, request));
    }

    @PostMapping("/teachers/{teacherId}/disable")
    public ApiResponse<Map<String, Object>> disableTeacher(@PathVariable Long teacherId) {
        return ApiResponse.ok(adminService.disableTeacher(teacherId));
    }

    @PostMapping("/teachers/{teacherId}/enable")
    public ApiResponse<Map<String, Object>> enableTeacher(@PathVariable Long teacherId) {
        return ApiResponse.ok(adminService.enableTeacher(teacherId));
    }

    @GetMapping("/students")
    public ApiResponse<List<Map<String, Object>>> students(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(adminService.listStudents(keyword));
    }

    @PostMapping("/students")
    public ApiResponse<Map<String, Object>> createStudent(@Valid @RequestBody StudentProfileRequest request) {
        return ApiResponse.ok(adminService.createStudent(request));
    }

    @PutMapping("/students/{studentId}")
    public ApiResponse<Map<String, Object>> updateStudent(@PathVariable Long studentId,
                                                          @Valid @RequestBody StudentProfileRequest request) {
        return ApiResponse.ok(adminService.updateStudent(studentId, request));
    }

    @PostMapping("/students/{studentId}/disable")
    public ApiResponse<Map<String, Object>> disableStudent(@PathVariable Long studentId) {
        return ApiResponse.ok(adminService.disableStudent(studentId));
    }

    @PostMapping("/students/{studentId}/enable")
    public ApiResponse<Map<String, Object>> enableStudent(@PathVariable Long studentId) {
        return ApiResponse.ok(adminService.enableStudent(studentId));
    }

    @GetMapping("/catalog")
    public ApiResponse<Map<String, Object>> catalog() {
        return ApiResponse.ok(adminService.catalog());
    }

    @GetMapping("/courses")
    public ApiResponse<List<Map<String, Object>>> courses(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(adminService.listCourses(keyword));
    }

    @PostMapping("/courses")
    public ApiResponse<Map<String, Object>> createCourse(@Valid @RequestBody CourseRequest request) {
        return ApiResponse.ok(adminService.createCourse(request));
    }

    @PostMapping("/courses/{courseId}/enable")
    public ApiResponse<Map<String, Object>> enableCourse(@PathVariable Long courseId) {
        return ApiResponse.ok(adminService.enableCourse(courseId));
    }

    @PostMapping("/courses/{courseId}/disable")
    public ApiResponse<Map<String, Object>> disableCourse(@PathVariable Long courseId) {
        return ApiResponse.ok(adminService.disableCourse(courseId));
    }

    @GetMapping("/offerings")
    public ApiResponse<List<Map<String, Object>>> offerings(@RequestParam(required = false) String keyword,
                                                            @RequestParam(defaultValue = "false") boolean currentOnly,
                                                            @RequestParam(required = false) Long semesterId) {
        return ApiResponse.ok(adminService.listOfferings(keyword, currentOnly, semesterId));
    }

    @PostMapping("/offerings")
    public ApiResponse<Map<String, Object>> createOffering(@Valid @RequestBody CreateOfferingRequest request) {
        return ApiResponse.ok(adminService.createOffering(request));
    }

    @PutMapping("/offerings/{offeringId}")
    public ApiResponse<Map<String, Object>> updateOffering(@PathVariable Long offeringId,
                                                           @Valid @RequestBody CreateOfferingRequest request) {
        return ApiResponse.ok(adminService.updateOffering(offeringId, request));
    }

    @DeleteMapping("/offerings/{offeringId}")
    public ApiResponse<Map<String, Object>> deleteOffering(@PathVariable Long offeringId) {
        return ApiResponse.ok(adminService.deleteOffering(offeringId));
    }

    @PostMapping("/semesters")
    public ApiResponse<Map<String, Object>> createSemester(@Valid @RequestBody SemesterRequest request) {
        return ApiResponse.ok(adminService.createSemester(request));
    }

    @PutMapping("/semesters/{semesterId}")
    public ApiResponse<Map<String, Object>> updateSemester(@PathVariable Long semesterId,
                                                           @Valid @RequestBody SemesterRequest request) {
        return ApiResponse.ok(adminService.updateSemester(semesterId, request));
    }

    /**
     * 开放选课：全局同时只能有一个选课学期，开启新选课前自动关闭旧的。
     * 选课与登分互斥——登分中不能开选课。
     */
    @PostMapping("/semesters/{semesterId}/selection/start")
    public ApiResponse<Map<String, Object>> startSemesterSelection(@PathVariable Long semesterId) {
        return ApiResponse.ok(adminService.startSemesterSelection(semesterId));
    }

    /** 关闭指定学期的选课阶段。 */
    @PostMapping("/semesters/{semesterId}/selection/stop")
    public ApiResponse<Map<String, Object>> stopSemesterSelection(@PathVariable Long semesterId) {
        return ApiResponse.ok(adminService.stopSemesterSelection(semesterId));
    }

    /**
     * 开放登分：全局同时只能有一个登分学期，开启新登分前自动关闭旧的。
     * 未开始的学期不能登分，登分与选课互斥。
     */
    @PostMapping("/semesters/{semesterId}/grading/start")
    public ApiResponse<Map<String, Object>> startSemesterGrading(@PathVariable Long semesterId) {
        return ApiResponse.ok(adminService.startSemesterGrading(semesterId));
    }

    /** 关闭指定学期的登分阶段。 */
    @PostMapping("/semesters/{semesterId}/grading/stop")
    public ApiResponse<Map<String, Object>> stopSemesterGrading(@PathVariable Long semesterId) {
        return ApiResponse.ok(adminService.stopSemesterGrading(semesterId));
    }

    @GetMapping("/enrollment-report")
    public ApiResponse<List<Map<String, Object>>> enrollmentReport() {
        return ApiResponse.ok(adminService.enrollmentReport());
    }

    @GetMapping("/offerings/{offeringId}/roster")
    public ApiResponse<List<Map<String, Object>>> courseRoster(@PathVariable Long offeringId) {
        return ApiResponse.ok(adminService.courseRoster(offeringId));
    }

    @GetMapping("/teachers/{teacherId}/offerings")
    public ApiResponse<List<Map<String, Object>>> teacherOfferings(@PathVariable Long teacherId,
                                                                   @RequestParam(required = false) Long semesterId) {
        return ApiResponse.ok(adminService.teacherOfferings(teacherId, semesterId));
    }

    @GetMapping("/students/{studentId}/enrollments")
    public ApiResponse<List<Map<String, Object>>> studentEnrollments(@PathVariable Long studentId,
                                                                    @RequestParam(required = false) Long semesterId) {
        return ApiResponse.ok(adminService.studentEnrollments(studentId, semesterId));
    }

    @GetMapping("/offerings/{offeringId}/grade-stats")
    public ApiResponse<Map<String, Object>> courseGradeStats(@PathVariable Long offeringId) {
        return ApiResponse.ok(adminService.courseGradeStats(offeringId));
    }

    /**
     * 管理员代学生选课：前端传 { studentNo, offeringId }，后端通过学号查 studentId。
     * 使用 Map 而非 DTO 接收参数，以兼容前端可能的不同传参格式。
     */
    @PostMapping("/teaching/select")
    public ApiResponse<Map<String, Object>> adminSelectCourse(SessionUser user, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(adminService.adminSelectCourse(
                user,
                String.valueOf(body.get("studentNo")),
                Long.valueOf(String.valueOf(body.get("offeringId")))
        ));
    }

    /**
     * 管理员代学生退课：支持两种传参方式——
     * { enrollmentId } 直接退课，或 { studentNo, offeringId } 通过学号+课程班定位。
     */
    @PostMapping("/teaching/drop")
    public ApiResponse<Map<String, Object>> adminDropCourse(SessionUser user, @RequestBody Map<String, Object> body) {
        if (body.get("enrollmentId") != null) {
            return ApiResponse.ok(adminService.adminDropEnrollment(user, Long.valueOf(String.valueOf(body.get("enrollmentId")))));
        }
        return ApiResponse.ok(adminService.adminDropCourse(
                user,
                String.valueOf(body.get("studentNo")),
                Long.valueOf(String.valueOf(body.get("offeringId")))
        ));
    }

    @GetMapping("/students/{studentNo}/teaching")
    public ApiResponse<Map<String, Object>> studentTeaching(@PathVariable String studentNo) {
        return ApiResponse.ok(adminService.studentTeachingInfo(studentNo));
    }

    @PostMapping("/notices")
    public ApiResponse<Map<String, Object>> createNotice(SessionUser user, @Valid @RequestBody NoticeRequest request) {
        return ApiResponse.ok(adminService.createNotice(user, request));
    }

    @PutMapping("/notices/{noticeId}")
    public ApiResponse<Map<String, Object>> updateNotice(@PathVariable Long noticeId,
                                                         @Valid @RequestBody NoticeRequest request) {
        return ApiResponse.ok(adminService.updateNotice(noticeId, request));
    }

    @DeleteMapping("/notices/{noticeId}")
    public ApiResponse<Map<String, Object>> deleteNotice(@PathVariable Long noticeId) {
        return ApiResponse.ok(adminService.deleteNotice(noticeId));
    }

    /** 事务审计日志分页查询，前端分页参数会被 clamp 到安全范围。 */
    @GetMapping("/logs")
    public ApiResponse<Map<String, Object>> logs(@RequestParam(defaultValue = "1") Integer page,
                                                 @RequestParam(defaultValue = "10") Integer pageSize) {
        return ApiResponse.ok(adminService.transactionLogs(page, pageSize));
    }

    /** 备份记录分页查询。 */
    @GetMapping("/backups")
    public ApiResponse<Map<String, Object>> backups(@RequestParam(defaultValue = "1") Integer page,
                                                    @RequestParam(defaultValue = "10") Integer pageSize) {
        return ApiResponse.ok(backupService.backupRecords(page, pageSize));
    }

    /** 手动触发数据库备份，记录操作人 ID 到 backup_records 表。 */
    @PostMapping("/backups/run")
    public ApiResponse<Map<String, Object>> runBackup(SessionUser user) {
        return ApiResponse.ok(backupService.runManualBackup(user));
    }
}
