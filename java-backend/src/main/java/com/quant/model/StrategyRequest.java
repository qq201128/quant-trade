package com.quant.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 策略请求模型
 * 从Java传递到Python的数据结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyRequest {
    /**
     * 策略名称
     */
    private String strategyName;
    
    /**
     * 交易标的（股票代码、期货合约等）
     */
    private String symbol;
    
    /**
     * 市场数据（K线、Tick等）
     */
    private Map<String, Object> marketData;
    
    /**
     * 策略参数（可配置的策略参数）
     */
    private Map<String, Object> strategyParams;
    
    /**
     * 当前持仓信息
     */
    private Map<String, Object> position;
    
    /**
     * 账户信息
     */
    private Map<String, Object> account;
}



