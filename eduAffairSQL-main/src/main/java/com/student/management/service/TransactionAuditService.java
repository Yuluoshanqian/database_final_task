package com.student.management.service;

import java.util.List;
import java.util.Locale;

import com.student.management.common.TransactionAuditContext;
import com.student.management.mapper.TransactionAuditMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 事务审计日志的写入服务。
 *
 * 事务传播策略：
 * - start() 和 rollback() 使用 REQUIRES_NEW：确保审计记录独立于业务事务持久化
 * - logStep() 和 finishCommitted() 使用 MANDATORY：强制在已有事务中运行
 *
 * 补偿机制：
 * compensateStaleStartedTransactions() 每 5 分钟清理超过 10 分钟仍为 started 状态
 * 的事务记录，标记为 failed——应对进程崩溃导致的事务记录残留。
 */
@Service
public class TransactionAuditService {
    private static final Logger log = LoggerFactory.getLogger(TransactionAuditService.class);
    private static final int COMPENSATION_MINUTES = 10;
    private static final int COMPENSATION_BATCH_SIZE = 100;

    private final TransactionAuditMapper mapper;
    private final PlatformTransactionManager transactionManager;

    public TransactionAuditService(TransactionAuditMapper mapper, PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.transactionManager = transactionManager;
    }

    /**
     * 创建事务审计记录（status=started），独立事务确保即使业务回滚审计记录也持久化。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void start(String transactionId, String businessType, Long actorUserId, String message) {
        mapper.insertTransaction(transactionId, businessType, actorUserId);
        mapper.insertEntry(transactionId, "START", null, null, "started", message);
    }

    /**
     * 记录单步操作日志。从 TransactionAuditContext 获取当前事务 ID，
     * 必须在已有事务中运行（MANDATORY），由 Service 层在业务方法内调用。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void logStep(String operation, String tableName, Long recordId, String status, String message) {
        String transactionId = TransactionAuditContext.transactionId().orElse(null);
        if (transactionId == null) {
            return;
        }
        mapper.insertEntry(transactionId, normalizeOperation(operation), tableName, recordId, normalizeStatus(status), message);
        TransactionAuditContext.markEntryWritten();
    }

    /**
     * 标记事务成功提交。在 AOP 切面的 TransactionTemplate lambda 内调用，
     * 必须与业务操作在同一个事务中（MANDATORY）。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void finishCommitted(String transactionId) {
        mapper.finishTransaction(transactionId, "committed");
        mapper.insertEntry(transactionId, "COMMIT", null, null, "success", "committed");
    }

    /**
     * 标记事务回滚。使用独立事务（REQUIRES_NEW）确保即使业务事务已回滚，
     * 回滚审计记录仍能写入。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rollback(String transactionId, String tableName, Long recordId, String message) {
        mapper.finishTransaction(transactionId, "rolled_back");
        mapper.insertEntry(transactionId, "ROLLBACK", tableName, recordId, "rolled_back", message);
    }

    @Scheduled(initialDelayString = "${app.audit.compensation-initial-delay-ms:60000}",
            fixedDelayString = "${app.audit.compensation-delay-ms:300000}")
    public void compensateStaleStartedTransactions() {
        try {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            template.executeWithoutResult(status -> {
                List<String> transactionIds = mapper.staleStartedTransactionIds(COMPENSATION_MINUTES, COMPENSATION_BATCH_SIZE);
                for (String transactionId : transactionIds) {
                    int updated = mapper.finishStartedTransaction(transactionId, "failed");
                    if (updated > 0) {
                        mapper.insertEntry(transactionId, "ROLLBACK", null, null, "failed",
                                "compensated stale started transaction after " + COMPENSATION_MINUTES + " minutes");
                    }
                }
            });
        } catch (RuntimeException ex) {
            log.warn("Failed to compensate stale transaction audit records", ex);
        }
    }

    private String normalizeOperation(String operation) {
        String normalized = operation == null ? "" : operation.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "START", "INSERT", "UPDATE", "DELETE", "UPSERT", "COMMIT", "ROLLBACK" -> normalized;
            default -> "UPSERT";
        };
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "started", "success", "failed", "rolled_back" -> normalized;
            default -> "success";
        };
    }
}
