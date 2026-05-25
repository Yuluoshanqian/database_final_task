package com.student.management.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要事务审计的业务方法。
 * 被 {@link TransactionAuditAspect} 拦截，自动记录到 transactions 和
 * transaction_log_entries 表，实现可追溯的操作审计。
 *
 * 可放在方法或类上。属性均可选——省略时 AOP 切面会从方法名自动推断。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface BusinessTransaction {
    /** 业务类型标识，如 "select_course"，省略则从方法名转 snake_case */
    String businessType() default "";

    /** 操作类型：INSERT/UPDATE/DELETE/UPSERT，省略则根据方法名前缀推断 */
    String operation() default "";

    /** 操作的目标表名，省略则留空 */
    String tableName() default "";

    /** 方法参数中 recordId 的索引位置，-1 表示自动查找 */
    int recordIdArgIndex() default -1;
}
