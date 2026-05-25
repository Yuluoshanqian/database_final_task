package com.student.management.controller;

import java.util.Map;

import com.student.management.common.ApiResponse;
import com.student.management.security.RequireRole;
import com.student.management.security.SessionUser;
import com.student.management.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘控制器。根据登录用户的角色（admin/teacher/student）返回不同的首页内容。
 * DashboardService 仅做路由分发，具体数据由各角色的 Service 提供。
 */
@RestController
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/dashboard")
    @RequireRole
    public ApiResponse<Map<String, Object>> dashboard(SessionUser user) {
        return ApiResponse.ok(dashboardService.dashboard(user));
    }
}
