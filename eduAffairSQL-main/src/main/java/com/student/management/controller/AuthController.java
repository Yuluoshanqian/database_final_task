package com.student.management.controller;

import com.student.management.common.ApiResponse;
import com.student.management.dto.LoginRequest;
import com.student.management.dto.LoginResponse;
import com.student.management.dto.ChangePasswordRequest;
import com.student.management.security.RequireRole;
import com.student.management.security.SessionUser;
import com.student.management.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器。登录、登出接口无需鉴权，其他接口需要登录（@RequireRole 不带参数表示仅需登录）。
 * /api/me 通过 SessionUser 参数自动注入当前用户信息。
 */
@RestController
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 健康检查，无需登录，用于负载均衡器或监控探针。 */
    @GetMapping("/api/health")
    public ApiResponse<Object> health() {
        return ApiResponse.ok(java.util.Map.of("status", "ok"));
    }

    /** 登录：验证用户名密码，返回 token + 用户信息。 */
    @PostMapping("/api/auth/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    /** 登出：从 SessionRegistry 移除 token。 */
    @PostMapping("/api/auth/logout")
    @RequireRole
    public ApiResponse<Object> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(authorization);
        return ApiResponse.ok(java.util.Map.of("message", "已退出"));
    }

    /** 获取当前登录用户信息，SessionUser 由参数解析器自动注入。 */
    @GetMapping("/api/me")
    @RequireRole
    public ApiResponse<SessionUser> me(SessionUser user) {
        return ApiResponse.ok(user);
    }

    /** 修改密码：需验证原密码，修改后用户需重新登录（旧 token 不会自动失效）。 */
    @PostMapping("/api/auth/password")
    @RequireRole
    public ApiResponse<Object> changePassword(SessionUser user, @Valid @RequestBody ChangePasswordRequest request) {
        return ApiResponse.ok(authService.changePassword(user, request));
    }
}
