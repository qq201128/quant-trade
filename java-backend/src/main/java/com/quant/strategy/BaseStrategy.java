package com.quant.strategy;

import com.quant.model.StrategyResponse;
import com.quant.model.StrategyRequest;

/**
 * 策略基类
 * 所有策略类型都应实现此接口
 */
public interface BaseStrategy {
    
    /**
     * 执行策略
     */
    StrategyResponse execute(StrategyRequest request);
    
    /**
     * 获取策略类型
     */
    com.quant.model.StrategyType getStrategyType();
    
    /**
     * 策略名称
     */
    String getStrategyName();
}



