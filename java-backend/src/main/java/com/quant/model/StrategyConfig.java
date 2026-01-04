package com.quant.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 策略配置实体类
 * 每个用户每个策略的独立配置
 */
@Entity
@Table(name = "strategy_configs", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "strategy_name"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 用户ID
     */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;
    
    /**
     * 策略名称
     */
    @Column(name = "strategy_name", nullable = false, length = 100)
    private String strategyName;
    
    /**
     * 策略类型
     */
    @Column(name = "strategy_type", length = 50)
    private String strategyType;
    
    /**
     * 配置参数（JSON格式存储）
     * 包含：symbols（交易对列表）、其他策略参数
     */
    @Column(name = "config_params", columnDefinition = "TEXT")
    private String configParams;

    /**
     * 策略是否启用
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    /**
     * 交易所类型
     */
    @Column(name = "exchange_type", length = 20)
    private String exchangeType;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

