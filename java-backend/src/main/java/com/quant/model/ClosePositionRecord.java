package com.quant.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 平仓记录实体类
 * 记录每次平仓的详细信息，包括手动平仓和策略平仓
 */
@Entity
@Table(name = "close_position_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClosePositionRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 用户ID
     */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;
    
    /**
     * 交易对（如：BTCUSDT）
     */
    @Column(nullable = false, length = 50)
    private String symbol;
    
    /**
     * 持仓方向：LONG（做多）、SHORT（做空）
     */
    @Column(nullable = false, length = 10)
    private String side;
    
    /**
     * 平仓数量
     */
    @Column(name = "close_quantity", nullable = false, precision = 20, scale = 8)
    private BigDecimal closeQuantity;
    
    /**
     * 平仓价格
     */
    @Column(name = "close_price", precision = 20, scale = 8)
    private BigDecimal closePrice;
    
    /**
     * 开仓均价
     */
    @Column(name = "avg_price", precision = 20, scale = 8)
    private BigDecimal avgPrice;
    
    /**
     * 杠杆倍数
     */
    @Column
    private Integer leverage;
    
    /**
     * 平仓使用的保证金（USDT）
     */
    @Column(precision = 20, scale = 8)
    private BigDecimal margin;
    
    /**
     * 已实现盈亏（USDT）
     */
    @Column(name = "realized_pnl", precision = 20, scale = 8)
    private BigDecimal realizedPnl;
    
    /**
     * 盈亏百分比
     */
    @Column(name = "pnl_percentage", precision = 10, scale = 4)
    private BigDecimal pnlPercentage;
    
    /**
     * 平仓类型：MANUAL（手动平仓）、STRATEGY（策略平仓）
     */
    @Column(name = "close_type", nullable = false, length = 20)
    private String closeType;
    
    /**
     * 策略名称（策略平仓时记录）
     */
    @Column(name = "strategy_name", length = 100)
    private String strategyName;
    
    /**
     * 订单ID
     */
    @Column(name = "order_id", length = 100)
    private String orderId;
    
    /**
     * 平仓时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}

