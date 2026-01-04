package com.quant.service;

import com.quant.model.StrategyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 风险控制器
 * Java层统一风控，确保策略信号的安全性
 */
@Slf4j
@Service
public class RiskController {
    
    @Value("${risk.control.enabled:false}")
    private boolean riskControlEnabled;
    
    @Value("${risk.control.max-position:0.3}")
    private BigDecimal maxPosition;
    
    @Value("${risk.control.min-confidence:0.6}")
    private BigDecimal minConfidence;
    
    /**
     * 验证策略信号是否通过风控
     */
    public boolean validateSignal(StrategyResponse response) {
        // 如果风控被禁用，直接通过
        if (!riskControlEnabled) {
            log.debug("风控检查已禁用，直接通过");
            return true;
        }
        
        // 1. 检查置信度
        if (response.getConfidence().compareTo(minConfidence) < 0) {
            log.warn("策略置信度过低: {}", response.getConfidence());
            return false;
        }
        
        // 2. 检查仓位限制
        if (response.getPosition().compareTo(maxPosition) > 0) {
            log.warn("建议仓位超过限制: {}", response.getPosition());
            return false;
        }
        
        // 3. 检查止损止盈合理性
        if (response.getStopLoss() != null && response.getTakeProfit() != null) {
            BigDecimal riskRewardRatio = calculateRiskRewardRatio(response);
            if (riskRewardRatio.compareTo(new BigDecimal("1.5")) < 0) {
                log.warn("风险收益比不合理: {}", riskRewardRatio);
                return false;
            }
        }
        
        // 4. 其他风控规则...
        
        return true;
    }
    
    private BigDecimal calculateRiskRewardRatio(StrategyResponse response) {
        // 计算风险收益比
        // 简化示例
        return new BigDecimal("2.0");
    }
}

