package com.quant.strategy;

// 已禁用：策略类型枚举中已注释掉GRID
/*
import com.quant.model.StrategyRequest;
import com.quant.model.StrategyResponse;
import com.quant.model.StrategyType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

网格策略实现
在价格区间内设置买卖网格，自动低买高卖

@Slf4j
@Component
public class GridStrategy implements BaseStrategy {
    
    @Override
    public StrategyResponse execute(StrategyRequest request) {
        log.info("执行网格策略: {}", request.getStrategyName());
        
        // 网格策略逻辑
        // 1. 获取当前价格和网格参数
        // 2. 计算网格区间
        // 3. 判断当前价格在哪个网格
        // 4. 生成买卖信号
        
        // 示例实现
        Double currentPrice = (Double) request.getMarketData().get("price");
        Integer gridCount = (Integer) request.getStrategyParams().getOrDefault("gridCount", 10);
        Double gridLower = (Double) request.getStrategyParams().get("gridLower");
        Double gridUpper = (Double) request.getStrategyParams().get("gridUpper");
        
        if (currentPrice == null || gridLower == null || gridUpper == null) {
            return StrategyResponse.defaultResponse();
        }
        
        // 计算网格步长
        double gridStep = (gridUpper - gridLower) / gridCount;
        
        // 判断当前价格在哪个网格
        int currentGrid = (int) ((currentPrice - gridLower) / gridStep);
        
        // 网格策略：在网格下沿买入，上沿卖出
        String signal = "HOLD";
        double position = 0.0;
        
        if (currentPrice <= gridLower + gridStep * currentGrid) {
            signal = "BUY";
            position = 0.3; // 买入30%仓位
        } else if (currentPrice >= gridLower + gridStep * (currentGrid + 1)) {
            signal = "SELL";
            position = 0.0; // 卖出
        }
        
        return StrategyResponse.builder()
                .signal(signal)
                .position(BigDecimal.valueOf(position))
                .targetPrice(BigDecimal.valueOf(gridLower + gridStep * (currentGrid + 1)))
                .confidence(BigDecimal.valueOf(0.7))
                .build();
    }
    
    @Override
    public StrategyType getStrategyType() {
        return StrategyType.GRID;
    }
    
    @Override
    public String getStrategyName() {
        return "网格策略";
    }
}
*/



