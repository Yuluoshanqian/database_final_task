package com.student.management.security;

import java.util.Map;

/**
 * 当前登录用户的会话信息，是认证和授权的核心数据结构。
 * 使用 Java 17 record，不可变且线程安全，可以被缓存到 Redis 和 ConcurrentHashMap。
 *
 * profile 存储角色特有的扩展信息：
 * - student: studentId, studentNo, majorId, majorName, departmentName, admissionYear
 * - teacher: teacherId, teacherNo, departmentName, title
 * - admin: 空 Map
 */
public record SessionUser(
        Long id,
        String username,
        String displayName,
        String email,
        String role,
        String roleName,
        Map<String, Object> profile
) {
}
