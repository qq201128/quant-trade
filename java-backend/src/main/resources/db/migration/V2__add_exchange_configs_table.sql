-- 创建交易所配置表，支持每个用户每个交易所的独立配置
CREATE TABLE IF NOT EXISTS exchange_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL COMMENT '用户ID',
    exchange_type VARCHAR(20) NOT NULL COMMENT '交易所类型：OKX/BINANCE',
    api_key VARCHAR(255) COMMENT 'API Key（加密）',
    secret_key VARCHAR(255) COMMENT 'Secret Key（加密）',
    passphrase VARCHAR(255) COMMENT 'Passphrase（加密，仅OKX需要）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_exchange (user_id, exchange_type),
    INDEX idx_user_id (user_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易所配置表';



