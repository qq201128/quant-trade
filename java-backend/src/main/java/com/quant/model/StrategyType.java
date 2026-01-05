package com.quant.model;

/**
 * 策略类型枚举
 */
public enum StrategyType {
    /**
     * 普通策略：基于技术指标、机器学习等的传统策略
     */
//    NORMAL("NORMAL", "普通策略", "基于技术指标、机器学习等的传统策略"),
    
    /**
     * 网格策略：在价格区间内设置买卖网格
     */
//    GRID("GRID", "网格策略", "在价格区间内设置买卖网格，自动低买高卖"),
    
    /**
     * 双向策略：同时持有多空仓位
     */
    DUAL_DIRECTION("DUAL_DIRECTION", "双向策略", "同时持有多空仓位，通过价差获利");
    
    /**
     * 套利策略：跨交易所套利
     */
//    ARBITRAGE("ARBITRAGE", "套利策略", "跨交易所套利，利用价差获利"),
    
    /**
     * 做市策略：提供流动性
     */
//    MARKET_MAKING("MARKET_MAKING", "做市策略", "提供流动性，通过买卖价差获利");
    
    private final String code;
    private final String name;
    private final String description;
    
    StrategyType(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
}



