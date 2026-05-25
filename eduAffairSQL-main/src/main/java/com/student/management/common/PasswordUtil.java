package com.student.management.common;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt 密码工具类。
 * strength=12 是安全性与性能的折中选择（2^12 轮哈希），
 * 新用户初始密码为工号/学号，修改密码时调用此工具重新哈希存储。
 */
public final class PasswordUtil {
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(12);

    private PasswordUtil() {
    }

    public static String hash(String raw) {
        return ENCODER.encode(raw);
    }

    public static boolean matches(String raw, String hash) {
        return raw != null && hash != null && ENCODER.matches(raw, hash);
    }
}
