package com.quant.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 策略响应模型
 * 从Python返回给Java的数据结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyResponse {
    /**
     * 交易信号：BUY, SELL, HOLD
     */
    private String signal;
    
    /**
     * 建议仓位（0.0 - 1.0）
     */
    private BigDecimal position;
    
    /**
     * 目标价格
     */
    private BigDecimal targetPrice;
    
    /**
     * 止损价格
     */
    private BigDecimal stopLoss;
    
    /**
     * 止盈价格
     */
    private BigDecimal takeProfit;
    
    /**
     * 策略置信度（0.0 - 1.0）
     */
    private BigDecimal confidence;
    
    /**
     * 额外信息（指标值、分析结果等）
     */
    private Map<String, Object> metadata;
    
    /**
     * 错误信息（如果有）
     */
    private String error;
    
    /**
     * 默认响应（策略调用失败时使用）
     */
    public static StrategyResponse defaultResponse() {
        return StrategyResponse.builder()
                .signal("HOLD")
                .position(BigDecimal.ZERO)
                .confidence(BigDecimal.ZERO)
                .error("策略服务不可用")
                .build();
    }
}



