package com.quant.repository;

import com.quant.model.ClosePositionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 平仓记录Repository
 */
@Repository
public interface ClosePositionRecordRepository extends JpaRepository<ClosePositionRecord, Long> {
    
    /**
     * 根据用户ID查询平仓记录
     */
    List<ClosePositionRecord> findByUserIdOrderByCreatedAtDesc(String userId);
    
    /**
     * 根据用户ID和交易对查询平仓记录
     */
    List<ClosePositionRecord> findByUserIdAndSymbolOrderByCreatedAtDesc(String userId, String symbol);
    
    /**
     * 根据用户ID和平仓类型查询平仓记录
     */
    List<ClosePositionRecord> findByUserIdAndCloseTypeOrderByCreatedAtDesc(String userId, String closeType);
    
    /**
     * 根据用户ID和时间范围查询平仓记录
     */
    @Query("SELECT c FROM ClosePositionRecord c WHERE c.userId = :userId " +
           "AND c.createdAt >= :startTime AND c.createdAt <= :endTime " +
           "ORDER BY c.createdAt DESC")
    List<ClosePositionRecord> findByUserIdAndTimeRange(
            @Param("userId") String userId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * 统计用户的总盈亏
     */
    @Query("SELECT COALESCE(SUM(c.realizedPnl), 0) FROM ClosePositionRecord c WHERE c.userId = :userId")
    BigDecimal sumRealizedPnlByUserId(@Param("userId") String userId);
    
    /**
     * 统计用户在指定交易对的总盈亏
     */
    @Query("SELECT COALESCE(SUM(c.realizedPnl), 0) FROM ClosePositionRecord c " +
           "WHERE c.userId = :userId AND c.symbol = :symbol")
    BigDecimal sumRealizedPnlByUserIdAndSymbol(
            @Param("userId") String userId,
            @Param("symbol") String symbol
    );
}

