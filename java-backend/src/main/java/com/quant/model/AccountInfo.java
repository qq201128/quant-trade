package com.quant.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 账户信息模型
 * 用于WebSocket实时推送
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountInfo {
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 总资产（USDT）
     */
    private BigDecimal totalBalance;
    
    /**
     * 可用余额（USDT）
     */
    private BigDecimal availableBalance;
    
    /**
     * 冻结余额（USDT）
     */
    private BigDecimal frozenBalance;
    
    /**
     * 持仓列表
     */
    private List<Position> positions;
    
    /**
     * 账户权益（总资产 + 未实现盈亏）
     */
    private BigDecimal equity;
    
    /**
     * 未实现盈亏
     */
    private BigDecimal unrealizedPnl;
    
    /**
     * 更新时间戳
     */
    private Long timestamp;
    
    /**
     * 额外信息
     */
    private Map<String, Object> metadata;
}



