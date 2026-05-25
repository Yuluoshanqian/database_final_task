package com.student.management.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色权限注解，标记 Controller 方法或类需要的角色。
 * 例：@RequireRole("admin") 表示仅管理员可访问。
 *
 * 可放在类上（该 Controller 所有方法生效），也可单独放在方法上覆盖类级别配置。
 * 空数组 {} 表示仅需登录即可访问（不限制具体角色）。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    String[] value() default {};
}
