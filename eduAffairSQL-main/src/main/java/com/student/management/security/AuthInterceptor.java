package com.student.management.security;

import java.util.Arrays;

import com.student.management.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring MVC 拦截器，拦截 /api/** 路径，完成 token 验证和角色鉴权。
 *
 * 认证流程：
 * 1. 从 Authorization 请求头提取 Bearer token
 * 2. 通过 SessionRegistry 查找并刷新会话
 * 3. 检查 @RequireRole 注解要求的角色
 * 4. 认证通过后将 SessionUser 存入 request attribute 和 ThreadLocal
 *
 * 未标注 @RequireRole 的接口默认放行（如 /api/public/landing）。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    public static final String CURRENT_USER = "currentUser";
    private final SessionRegistry sessionRegistry;

    public AuthInterceptor(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * 请求前置处理：检查 @RequireRole 注解 → 解析 token → 验证会话 → 鉴权 → 注入用户上下文。
     * 未标注 @RequireRole 的接口直接放行（如登录页、健康检查）。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireRole requireRole = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), RequireRole.class);
        if (requireRole == null) {
            requireRole = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequireRole.class);
        }
        if (requireRole == null) {
            return true;
        }

        String token = resolveToken(request);
        SessionUser user = sessionRegistry.find(token)
                .orElseThrow(() -> new ApiException(401, "登录已失效，请重新登录"));

        if (requireRole.value().length > 0 && Arrays.stream(requireRole.value()).noneMatch(user.role()::equals)) {
            throw new ApiException(403, "当前角色无权执行该操作");
        }

        request.setAttribute(CURRENT_USER, user);
        CurrentUserContext.set(user);
        return true;
    }

    /** 请求后置清理：从 ThreadLocal 移除当前用户，防止内存泄漏。 */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentUserContext.clear();
    }

    /** 从 Authorization 请求头提取 Bearer token。 */
    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return "";
    }
}
