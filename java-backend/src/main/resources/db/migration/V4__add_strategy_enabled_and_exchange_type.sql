-- 为策略配置表添加启用状态和交易所类型字段
-- 注意：MySQL不支持IF NOT EXISTS，如果字段已存在会报错，需要手动处理
ALTER TABLE strategy_configs 
ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '策略是否启用',
ADD COLUMN exchange_type VARCHAR(20) COMMENT '交易所类型：BINANCE/OKX等';

-- 为enabled字段添加索引，方便查询启用的策略
CREATE INDEX idx_enabled ON strategy_configs(enabled);

