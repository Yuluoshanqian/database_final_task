package com.student.management.service;

import java.util.Collections;
import java.util.Map;

import com.student.management.common.ApiException;
import com.student.management.common.BusinessTransaction;
import com.student.management.common.MapUtil;
import com.student.management.common.PasswordUtil;
import com.student.management.dto.LoginRequest;
import com.student.management.dto.LoginResponse;
import com.student.management.dto.ChangePasswordRequest;
import com.student.management.mapper.AuthMapper;
import com.student.management.security.SessionRegistry;
import com.student.management.security.SessionUser;
import org.springframework.stereotype.Service;

/**
 * 认证服务。登录时先查 users 表获取密码哈希和角色，BCrypt 验证通过后
 * 按角色查询扩展 profile（学生查 students+majors+departments，教师查 teachers+departments），
 * 最后创建 SessionUser 并生成 token 返回。
 *
 * 登录失败统一返回 "用户名或密码错误"，不区分"用户不存在"和"密码错误"，
 * 防止撞库攻击者枚举有效用户名。
 */
@Service
public class AuthService {
    private final AuthMapper authMapper;
    private final SessionRegistry sessionRegistry;
    private final TransactionAuditService auditService;

    public AuthService(AuthMapper authMapper, SessionRegistry sessionRegistry, TransactionAuditService auditService) {
        this.authMapper = authMapper;
        this.sessionRegistry = sessionRegistry;
        this.auditService = auditService;
    }

    /**
     * 登录：查用户 → 验状态 → 验密码 → 查角色扩展信息 → 创建会话。
     * 失败不分具体原因，统一返回 401 "用户名或密码错误"。
     */
    public LoginResponse login(LoginRequest request) {
        Map<String, Object> row = authMapper.findUserByUsername(request.username());
        if (row == null || !"enabled".equals(MapUtil.stringValue(row, "status"))) {
            throw new ApiException(401, "用户名或密码错误");
        }

        String passwordHash = MapUtil.stringValue(row, "passwordHash");
        if (!PasswordUtil.matches(request.password(), passwordHash)) {
            throw new ApiException(401, "用户名或密码错误");
        }

        Long userId = MapUtil.longValue(row, "id");
        String role = MapUtil.stringValue(row, "role");
        Map<String, Object> profile = switch (role) {
            case "student" -> authMapper.findStudentProfile(userId);
            case "teacher" -> authMapper.findTeacherProfile(userId);
            default -> Collections.emptyMap();
        };
        if (profile == null) {
            profile = Collections.emptyMap();
        }

        SessionUser user = new SessionUser(
                userId,
                MapUtil.stringValue(row, "username"),
                MapUtil.stringValue(row, "displayName"),
                MapUtil.stringValue(row, "email"),
                role,
                MapUtil.stringValue(row, "roleName"),
                profile
        );
        return new LoginResponse(sessionRegistry.create(user), user);
    }

    /** 登出：从 SessionRegistry 移除 token，内存和 Redis 同步删除。 */
    public void logout(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            sessionRegistry.remove(authorization.substring(7));
        }
    }

    /**
     * 修改密码：验证原密码 → 新密码最短长度校验 → BCrypt 哈希后更新。
     * 修改后用户需要重新登录（旧 token 未失效，但用户知道密码已改）。
     */
    @BusinessTransaction(businessType = "change_password", operation = "UPDATE", tableName = "users", recordIdArgIndex = 0)
    public Map<String, Object> changePassword(SessionUser user, ChangePasswordRequest request) {
        String currentHash = authMapper.passwordHashByUserId(user.id());
        if (!PasswordUtil.matches(request.oldPassword(), currentHash)) {
            throw new ApiException(400, "原密码错误");
        }
        if (request.newPassword().length() < 2) {
            throw new ApiException(400, "新密码长度不能少于 2 位");
        }
        authMapper.updatePassword(user.id(), PasswordUtil.hash(request.newPassword()));
        auditService.logStep("UPDATE", "users", user.id(), "success", "password_hash");
        return Map.of("message", "密码已修改，请重新登录");
    }
}
