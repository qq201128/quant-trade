package com.quant.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 持仓信息模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Position {
    /**
     * 交易对（如：BTC-USDT）
     */
    private String symbol;
    
    /**
     * 持仓方向：LONG（做多）、SHORT（做空）
     */
    private String side;
    
    /**
     * 持仓数量
     */
    private BigDecimal quantity;
    
    /**
     * 可用数量
     */
    private BigDecimal available;
    
    /**
     * 开仓均价
     */
    private BigDecimal avgPrice;
    
    /**
     * 当前价格
     */
    private BigDecimal currentPrice;
    
    /**
     * 未实现盈亏
     */
    private BigDecimal unrealizedPnl;
    
    /**
     * 盈亏比例
     */
    private BigDecimal pnlPercentage;
    
    /**
     * 杠杆倍数
     */
    private Integer leverage;
    
    /**
     * 保证金
     */
    private BigDecimal margin;
}



