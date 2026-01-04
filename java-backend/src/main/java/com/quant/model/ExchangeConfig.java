package com.quant.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 交易所配置实体类
 * 每个用户每个交易所的独立配置
 */
@Entity
@Table(name = "exchange_configs", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "exchange_type"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 用户ID
     */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;
    
    /**
     * 交易所类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "exchange_type", nullable = false, length = 20)
    private ExchangeType exchangeType;
    
    /**
     * API Key（加密存储）
     */
    @Column(name = "api_key", length = 255)
    private String apiKey;
    
    /**
     * Secret Key（加密存储）
     */
    @Column(name = "secret_key", length = 255)
    private String secretKey;
    
    /**
     * Passphrase（OKX需要，加密存储）
     */
    @Column(name = "passphrase", length = 255)
    private String passphrase;
    
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



