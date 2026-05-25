package com.student.management.common;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.student.management.security.CurrentUserContext;
import com.student.management.security.SessionUser;
import com.student.management.service.TransactionAuditService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AOP 切面，拦截所有标注 @BusinessTransaction 的方法。
 *
 * 核心职责：
 * 1. 方法执行前：创建事务审计记录（status=started）
 * 2. 方法执行中：Service 层调用 auditService.logStep() 记录具体操作
 * 3. 方法正常返回：标记 committed
 * 4. 方法抛异常：标记 rolled_back，并记录失败原因
 *
 * 审计本身在独立事务中执行（REQUIRES_NEW），确保即使业务事务回滚，
 * 审计记录仍然持久化——这是审计真实性的关键要求。
 */
@Aspect
@Component
public class TransactionAuditAspect {
    private final TransactionAuditService auditService;
    private final PlatformTransactionManager transactionManager;

    public TransactionAuditAspect(TransactionAuditService auditService, PlatformTransactionManager transactionManager) {
        this.auditService = auditService;
        this.transactionManager = transactionManager;
    }

    /**
     * 环绕通知：拦截 @BusinessTransaction 方法，在其前后插入审计记录。
     *
     * 流程：
     * 1. 从注解提取 businessType/operation/tableName/recordId（未配置则自动推断）
     * 2. 在独立事务中写入 START 审计记录
     * 3. 在 READ_COMMITTED 事务中执行业务方法（TransactionAuditContext 传递 transactionId）
     * 4. 业务成功 → 写入 COMMIT 记录；业务失败 → 写入 ROLLBACK 记录
     *
     * BusinessInvocationException 是内部包装器：TransactionTemplate lambda 只能抛
     * RuntimeException，我们用它将任意 Throwable 传递到外层 catch 块。
     */
    @Around("@annotation(com.student.management.common.BusinessTransaction)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        BusinessTransaction businessTransaction = method.getAnnotation(BusinessTransaction.class);
        if (businessTransaction == null) {
            return joinPoint.proceed();
        }
        Object[] args = joinPoint.getArgs();
        String transactionId = UUID.randomUUID().toString();
        String businessType = valueOrDefault(businessTransaction.businessType(), toSnakeCase(method.getName()));
        String operation = normalizeOperation(valueOrDefault(businessTransaction.operation(), inferOperation(method.getName())));
        String tableName = blankToNull(businessTransaction.tableName());
        Long actorUserId = actorUserId(args);
        Long recordId = recordId(args, businessTransaction.recordIdArgIndex());

        auditService.start(transactionId, businessType, actorUserId,
                "method=" + method.getDeclaringClass().getSimpleName() + "." + method.getName());
        try {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
            return template.execute(status -> {
                TransactionAuditContext.begin(transactionId);
                try {
                    Object result = joinPoint.proceed();
                    if (TransactionAuditContext.entryCount() == 0 && tableName != null) {
                        auditService.logStep(operation, tableName, recordId, "success", successMessage(result));
                    }
                    auditService.finishCommitted(transactionId);
                    return result;
                } catch (Throwable ex) {
                    throw new BusinessInvocationException(ex);
                } finally {
                    TransactionAuditContext.clear();
                }
            });
        } catch (BusinessInvocationException ex) {
            Throwable original = ex.original();
            auditRollback(transactionId, tableName, recordId, original);
            throw original;
        } catch (RuntimeException ex) {
            auditRollback(transactionId, tableName, recordId, ex);
            throw ex;
        }
    }

    /**
     * 写入回滚审计记录。如果审计写入本身也失败了，将审计异常附加到原始异常
     * 的 suppressed 列表中，避免审计失败掩盖原始业务异常。
     */
    private void auditRollback(String transactionId, String tableName, Long recordId, Throwable failure) {
        try {
            auditService.rollback(transactionId, tableName, recordId, failureMessage(failure));
        } catch (RuntimeException auditFailure) {
            failure.addSuppressed(auditFailure);
        }
    }

    /**
     * 从方法参数中自动提取操作人 ID：优先找 SessionUser 类型的参数，
     * 找不到则从 ThreadLocal 的 CurrentUserContext 获取。
     */
    private Long actorUserId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof SessionUser user) {
                return user.id();
            }
        }
        return CurrentUserContext.get().map(SessionUser::id).orElse(null);
    }

    /**
     * 从方法参数中自动提取被操作记录的 ID。
     * 优先使用注解指定的 recordIdArgIndex，否则尝试从参数中自动识别 Number 或 String 类型。
     */
    private Long recordId(Object[] args, int index) {
        if (index < 0 || index >= args.length) {
            return null;
        }
        Object value = args[index];
        if (value instanceof SessionUser user) {
            return user.id();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 从返回值中提取业务消息（通常是 "message" 字段），否则用默认 "committed"。 */
    private String successMessage(Object result) {
        if (result instanceof Map<?, ?> map) {
            Object message = map.get("message");
            if (message != null) {
                return String.valueOf(message);
            }
        }
        return "committed";
    }

    /** 提取异常链最深处的根因消息，避免只记录表层包装异常的无意义信息。 */
    private String failureMessage(Throwable ex) {
        Throwable current = ex;
        Throwable root = ex;
        while (current != null) {
            root = current;
            current = current.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 根据方法名前缀自动推断 SQL 操作类型。
     * create → INSERT, delete/stop → DELETE, update/enable/disable/start/change → UPDATE
     */
    private String inferOperation(String methodName) {
        if (methodName.startsWith("create")) {
            return "INSERT";
        }
        if (methodName.startsWith("delete") || methodName.startsWith("stop")) {
            return "DELETE";
        }
        if (methodName.startsWith("update") || methodName.startsWith("enable") || methodName.startsWith("disable")
                || methodName.startsWith("start") || methodName.startsWith("change")) {
            return "UPDATE";
        }
        return "UPSERT";
    }

    /** 将操作名标准化为大写，非法值默认为 UPSERT。 */
    private String normalizeOperation(String operation) {
        String normalized = operation.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "START", "INSERT", "UPDATE", "DELETE", "UPSERT", "COMMIT", "ROLLBACK" -> normalized;
            default -> "UPSERT";
        };
    }

    /** camelCase 转 snake_case，用于从 Java 方法名自动生成业务类型标识。 */
    private String toSnakeCase(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i += 1) {
            char current = value.charAt(i);
            if (Character.isUpperCase(current) && i > 0) {
                builder.append('_');
            }
            builder.append(Character.toLowerCase(current));
        }
        return builder.toString();
    }

    /**
     * 内部异常包装类，用于在 TransactionTemplate 的 lambda 中传递原始异常。
     * TransactionTemplate 的 execute 方法会捕获 RuntimeException，
     * 我们需要把原始异常传递到外层以正确触发审计回滚。
     */
    private static final class BusinessInvocationException extends RuntimeException {
        private final Throwable original;

        private BusinessInvocationException(Throwable original) {
            super(original);
            this.original = original;
        }

        private Throwable original() {
            return original;
        }
    }
}
