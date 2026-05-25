package com.student.management.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import com.student.management.common.ApiResponse;
import com.student.management.mapper.CommonMapper;
import com.student.management.security.RequireRole;
import com.student.management.security.SessionUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公共接口控制器。
 * /api/public/landing 无需登录（登录页展示用），
 * /api/catalog 需要登录（前端全局状态初始化用）。
 *
 * CommonController 直接注入 CommonMapper 而非通过 Service 层，
 * 因为这些是纯只读查询，无业务逻辑，无需事务审计。
 */
@RestController
public class CommonController {
    private final CommonMapper commonMapper;

    public CommonController(CommonMapper commonMapper) {
        this.commonMapper = commonMapper;
    }

    @GetMapping("/api/public/landing")
    public ApiResponse<Map<String, Object>> landing() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("currentSemester", commonMapper.currentSemester());
        data.put("selectionSemester", commonMapper.selectionSemester());
        data.put("gradingSemester", commonMapper.gradingSemester());
        data.put("notices", commonMapper.listRecentNotices("student", 6));
        return ApiResponse.ok(data);
    }

    @GetMapping("/api/catalog")
    @RequireRole
    public ApiResponse<Map<String, Object>> catalog(SessionUser user) {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> currentSemester = commonMapper.currentSemester();
        Map<String, Object> selectionSemester = commonMapper.selectionSemester();
        Map<String, Object> gradingSemester = commonMapper.gradingSemester();
        data.put("semesters", commonMapper.semesters());
        data.put("currentSemester", currentSemester);
        data.put("selectionSemester", selectionSemester);
        data.put("gradingSemester", gradingSemester);
        data.put("selectionOpen", selectionSemester != null);
        data.put("gradingOpen", gradingSemester != null);
        data.put("notices", commonMapper.listNotices(user.role()));
        return ApiResponse.ok(data);
    }
}
