-- 创建策略配置表，支持每个用户每个策略的独立配置
CREATE TABLE IF NOT EXISTS strategy_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL COMMENT '用户ID',
    strategy_name VARCHAR(100) NOT NULL COMMENT '策略名称',
    strategy_type VARCHAR(50) COMMENT '策略类型：NORMAL/GRID/DUAL_DIRECTION',
    config_params TEXT COMMENT '配置参数（JSON格式），包含交易对列表和其他参数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_strategy (user_id, strategy_name),
    INDEX idx_user_id (user_id),
    INDEX idx_strategy_name (strategy_name),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='策略配置表';

