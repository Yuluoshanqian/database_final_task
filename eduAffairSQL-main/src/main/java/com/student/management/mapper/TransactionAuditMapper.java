package com.student.management.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 事务审计日志的数据访问层。
 *
 * staleStartedTransactionIds 查询用于补偿机制：
 * 找出超过指定分钟数仍为 started 状态的僵尸事务记录，由 TransactionAuditService 标记为 failed。
 * finishStartedTransaction 带条件更新（AND final_status = 'started'），防止并发补偿冲突。
 */
@Mapper
public interface TransactionAuditMapper {
    @Insert("""
            INSERT INTO transactions(transaction_id, business_type, actor_user_id, started_at, final_status)
            VALUES(#{transactionId}, #{businessType}, #{actorUserId}, CURRENT_TIMESTAMP, 'started')
            """)
    int insertTransaction(@Param("transactionId") String transactionId,
                          @Param("businessType") String businessType,
                          @Param("actorUserId") Long actorUserId);

    @Insert("""
            INSERT INTO transaction_log_entries(transaction_id, operation, table_name, record_id, status, message)
            VALUES(#{transactionId}, #{operation}, #{tableName}, #{recordId}, #{status}, #{message})
            """)
    int insertEntry(@Param("transactionId") String transactionId,
                    @Param("operation") String operation,
                    @Param("tableName") String tableName,
                    @Param("recordId") Long recordId,
                    @Param("status") String status,
                    @Param("message") String message);

    @Update("""
            UPDATE transactions
               SET ended_at = CURRENT_TIMESTAMP,
                   final_status = #{finalStatus}
             WHERE transaction_id = #{transactionId}
            """)
    int finishTransaction(@Param("transactionId") String transactionId,
                          @Param("finalStatus") String finalStatus);

    @Update("""
            UPDATE transactions
               SET ended_at = CURRENT_TIMESTAMP,
                   final_status = #{finalStatus}
             WHERE transaction_id = #{transactionId}
               AND final_status = 'started'
            """)
    int finishStartedTransaction(@Param("transactionId") String transactionId,
                                 @Param("finalStatus") String finalStatus);

    @Select("""
            SELECT transaction_id
             FROM transactions
             WHERE final_status = 'started'
               AND started_at < TIMESTAMPADD(MINUTE, (0 - #{olderThanMinutes}), CURRENT_TIMESTAMP)
             ORDER BY started_at
             LIMIT #{limit}
            """)
    List<String> staleStartedTransactionIds(@Param("olderThanMinutes") int olderThanMinutes,
                                            @Param("limit") int limit);
}
