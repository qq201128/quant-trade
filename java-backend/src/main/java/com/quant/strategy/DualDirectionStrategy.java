package com.quant.strategy;

import com.quant.model.StrategyRequest;
import com.quant.model.StrategyResponse;
import com.quant.model.StrategyType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 双向策略实现
 * 同时持有多空仓位，通过价差获利
 */
@Slf4j
@Component
public class DualDirectionStrategy implements BaseStrategy {
    
    @Override
    public StrategyResponse execute(StrategyRequest request) {
        log.info("执行双向策略: {}", request.getStrategyName());
        
        // 双向策略逻辑
        // 1. 同时开多空仓位
        // 2. 通过价差变化获利
        // 3. 动态调整多空比例
        
        Double currentPrice = (Double) request.getMarketData().get("price");
        if (currentPrice == null) {
            return StrategyResponse.defaultResponse();
        }
        
        // 获取当前持仓
        Map<String, Object> position = request.getPosition();
        Double longQuantity = position != null ? (Double) position.get("longQuantity") : 0.0;
        Double shortQuantity = position != null ? (Double) position.get("shortQuantity") : 0.0;
        
        // 双向策略：保持多空平衡，根据市场情况调整
        String signal = "HOLD";
        double positionRatio = 0.0;
        
        // 如果多空不平衡，进行调整
        if (longQuantity < shortQuantity) {
            signal = "BUY";
            positionRatio = 0.5; // 增加多头
        } else if (longQuantity > shortQuantity) {
            signal = "SELL";
            positionRatio = 0.0; // 减少多头（或增加空头）
        }
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("longQuantity", longQuantity);
        metadata.put("shortQuantity", shortQuantity);
        metadata.put("strategy", "DualDirectionStrategy");
        
        return StrategyResponse.builder()
                .signal(signal)
                .position(BigDecimal.valueOf(positionRatio))
                .confidence(BigDecimal.valueOf(0.6))
                .metadata(metadata)
                .build();
    }
    
    @Override
    public StrategyType getStrategyType() {
        return StrategyType.DUAL_DIRECTION;
    }
    
    @Override
    public String getStrategyName() {
        return "双向策略";
    }
}



