package com.student.management.common;

import java.util.Optional;

/**
 * ThreadLocal 持有当前事务的审计上下文。
 * 使 TransactionAuditService.logStep() 无需显式传递 transactionId，
 * 自动从当前线程上下文获取，简化 Service 层的审计代码。
 */
public final class TransactionAuditContext {
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private TransactionAuditContext() {
    }

    public static void begin(String transactionId) {
        CURRENT.set(new State(transactionId));
    }

    public static Optional<String> transactionId() {
        State state = CURRENT.get();
        return state == null ? Optional.empty() : Optional.of(state.transactionId);
    }

    public static void markEntryWritten() {
        State state = CURRENT.get();
        if (state != null) {
            state.entryCount += 1;
        }
    }

    public static int entryCount() {
        State state = CURRENT.get();
        return state == null ? 0 : state.entryCount;
    }

    /**
     * 必须在线程结束时调用 remove() 防止内存泄漏。
     */
    public static void clear() {
        CURRENT.remove();
    }

    private static final class State {
        private final String transactionId;
        private int entryCount;

        private State(String transactionId) {
            this.transactionId = transactionId;
        }
    }
}
