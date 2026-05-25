package com.student.management.security;

import java.util.Optional;

/**
 * 当前请求用户的 ThreadLocal 持有者。
 * 在 AuthInterceptor.preHandle() 中设置，在 afterCompletion() 中清除。
 * 供 Service 层（特别是 AOP 切面）在不方便通过参数传递 SessionUser 时使用。
 *
 * 使用 ThreadLocal 而非 request attribute 的原因：
 * TransactionAuditAspect 等非 Web 层组件无法直接访问 HttpServletRequest。
 */
public final class CurrentUserContext {
    private static final ThreadLocal<SessionUser> CURRENT = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(SessionUser user) {
        CURRENT.set(user);
    }

    public static Optional<SessionUser> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void clear() {
        CURRENT.remove();
    }
}
