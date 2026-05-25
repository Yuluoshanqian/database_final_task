package com.student.management.service;

import java.util.Map;

import com.student.management.security.SessionUser;
import org.springframework.stereotype.Service;

/**
 * 仪表盘服务，根据用户角色分发到对应的 Service。
 * 本身不包含业务逻辑，仅做路由——admin 看系统状态，teacher 看授课统计，student 看已选课程。
 */
@Service
public class DashboardService {
    private final AdminService adminService;
    private final TeacherService teacherService;
    private final StudentService studentService;

    public DashboardService(AdminService adminService, TeacherService teacherService, StudentService studentService) {
        this.adminService = adminService;
        this.teacherService = teacherService;
        this.studentService = studentService;
    }

    public Map<String, Object> dashboard(SessionUser user) {
        return switch (user.role()) {
            case "admin" -> adminService.dashboard();
            case "teacher" -> teacherService.dashboard(user);
            case "student" -> studentService.dashboard(user);
            default -> Map.of();
        };
    }
}
