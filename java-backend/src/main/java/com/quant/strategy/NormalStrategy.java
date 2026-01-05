package com.quant.strategy;

// 已禁用：策略类型枚举中已注释掉NORMAL
/*
import com.quant.model.StrategyRequest;
import com.quant.model.StrategyResponse;
import com.quant.model.StrategyType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

普通策略实现
调用Python策略服务执行传统策略（技术指标、机器学习等）

@Slf4j
@Component
public class NormalStrategy implements BaseStrategy {
    
    private final com.quant.service.StrategyManager strategyManager;
    
    public NormalStrategy(com.quant.service.StrategyManager strategyManager) {
        this.strategyManager = strategyManager;
    }
    
    @Override
    public StrategyResponse execute(StrategyRequest request) {
        log.info("执行普通策略: {}", request.getStrategyName());
        // 调用Python策略服务
        return strategyManager.executeStrategy(request).block();
    }
    
    @Override
    public StrategyType getStrategyType() {
        return StrategyType.NORMAL;
    }
    
    @Override
    public String getStrategyName() {
        return "普通策略";
    }
}
*/



