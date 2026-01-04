package com.quant.strategy;

import com.quant.model.StrategyType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 策略工厂
 * 根据策略类型创建对应的策略实例
 */
@Component
@RequiredArgsConstructor
public class StrategyFactory {
    
    private final Map<String, BaseStrategy> strategies;
    
    /**
     * 根据策略类型获取策略实例
     */
    public BaseStrategy getStrategy(StrategyType strategyType) {
        String beanName = strategyType.getCode().toLowerCase() + "Strategy";
        BaseStrategy strategy = strategies.get(beanName);
        
        if (strategy == null) {
            throw new IllegalArgumentException("不支持的策略类型: " + strategyType);
        }
        
        return strategy;
    }
}



