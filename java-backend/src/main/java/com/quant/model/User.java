package com.quant.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户实体类
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 用户ID（唯一标识，用于业务逻辑）
     */
    @Column(unique = true, nullable = false, length = 50)
    private String userId;
    
    /**
     * 用户名
     */
    @Column(unique = true, nullable = false, length = 50)
    private String username;
    
    /**
     * 密码（加密存储）
     */
    @Column(nullable = false, length = 255)
    private String password;
    
    /**
     * 邮箱
     */
    @Column(unique = true, length = 100)
    private String email;
    
    /**
     * 手机号
     */
    @Column(length = 20)
    private String phone;
    
    /**
     * 选择的交易所：OKX 或 BINANCE
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ExchangeType exchangeType;
    
    /**
     * 交易所API Key（加密存储）
     */
    @Column(length = 255)
    private String apiKey;
    
    /**
     * 交易所Secret Key（加密存储）
     */
    @Column(length = 255)
    private String secretKey;
    
    /**
     * 交易所Passphrase（OKX需要，加密存储）
     */
    @Column(length = 255)
    private String passphrase;
    
    /**
     * 是否启用
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;
    
    /**
     * 创建时间
     */
    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    /**
     * 更新时间
     */
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
