package com.quant.service;

import com.quant.model.ClosePositionRecord;
import com.quant.model.Order;
import com.quant.model.Position;
import com.quant.repository.ClosePositionRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 平仓记录服务
 * 负责记录每次平仓的详细信息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClosePositionRecordService {
    
    private final ClosePositionRecordRepository repository;
    
    /**
     * 记录平仓信息
     * 
     * @param userId 用户ID
     * @param symbol 交易对
     * @param side 持仓方向（LONG/SHORT）
     * @param closeQuantity 平仓数量
     * @param targetPosition 平仓前的持仓信息（用于获取开仓均价、杠杆等信息）
     * @param closeOrder 平仓订单（用于获取订单ID和平仓价格）
     * @param closeType 平仓类型：MANUAL（手动平仓）、STRATEGY（策略平仓）
     * @param strategyName 策略名称（策略平仓时记录，可选）
     */
    @Transactional
    public void recordClosePosition(String userId, String symbol, String side,
                                   BigDecimal closeQuantity, Position targetPosition,
                                   Order closeOrder, String closeType, String strategyName) {
        try {
            // 获取平仓价格（优先使用订单价格，其次使用持仓当前价格）
            BigDecimal closePrice = null;
            if (closeOrder != null && closeOrder.getPrice() != null) {
                closePrice = closeOrder.getPrice();
            } else if (targetPosition != null && targetPosition.getCurrentPrice() != null) {
                closePrice = targetPosition.getCurrentPrice();
            }
            
            // 获取开仓均价
            BigDecimal avgPrice = targetPosition != null ? targetPosition.getAvgPrice() : null;
            
            // 获取杠杆
            Integer leverage = targetPosition != null ? targetPosition.getLeverage() : null;
            
            // 计算平仓使用的保证金
            // 如果平仓数量等于持仓数量，使用持仓的保证金
            // 否则按比例计算：保证金 = 持仓保证金 × (平仓数量 / 持仓数量)
            BigDecimal margin = null;
            if (targetPosition != null && targetPosition.getMargin() != null) {
                if (targetPosition.getQuantity() != null && 
                    targetPosition.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    if (closeQuantity.compareTo(targetPosition.getQuantity()) >= 0) {
                        // 全部平仓，使用全部保证金
                        margin = targetPosition.getMargin();
                    } else {
                        // 部分平仓，按比例计算
                        margin = targetPosition.getMargin()
                                .multiply(closeQuantity)
                                .divide(targetPosition.getQuantity(), 8, RoundingMode.HALF_UP);
                    }
                }
            }
            
            // 计算已实现盈亏
            BigDecimal realizedPnl = null;
            if (closePrice != null && avgPrice != null && closeQuantity != null) {
                if ("LONG".equals(side)) {
                    // 多仓：盈亏 = (平仓价格 - 开仓均价) × 数量
                    realizedPnl = closePrice.subtract(avgPrice).multiply(closeQuantity);
                } else if ("SHORT".equals(side)) {
                    // 空仓：盈亏 = (开仓均价 - 平仓价格) × 数量
                    realizedPnl = avgPrice.subtract(closePrice).multiply(closeQuantity);
                }
            }
            
            // 计算盈亏百分比（相对于保证金）
            BigDecimal pnlPercentage = null;
            if (realizedPnl != null && margin != null && margin.compareTo(BigDecimal.ZERO) > 0) {
                pnlPercentage = realizedPnl.divide(margin, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }
            
            // 获取订单ID
            String orderId = closeOrder != null ? closeOrder.getOrderId() : null;
            
            // 创建平仓记录
            ClosePositionRecord record = ClosePositionRecord.builder()
                    .userId(userId)
                    .symbol(symbol)
                    .side(side)
                    .closeQuantity(closeQuantity)
                    .closePrice(closePrice)
                    .avgPrice(avgPrice)
                    .leverage(leverage)
                    .margin(margin)
                    .realizedPnl(realizedPnl)
                    .pnlPercentage(pnlPercentage)
                    .closeType(closeType)
                    .strategyName(strategyName)
                    .orderId(orderId)
                    .build();
            
            // 保存记录
            repository.save(record);
            
            log.info("平仓记录已保存: userId={}, symbol={}, side={}, closeQuantity={}, " +
                    "realizedPnl={}, closeType={}, strategyName={}",
                    userId, symbol, side, closeQuantity, realizedPnl, closeType, strategyName);
        } catch (Exception e) {
            log.error("保存平仓记录失败: userId={}, symbol={}, side={}, error={}",
                    userId, symbol, side, e.getMessage(), e);
            // 不抛出异常，避免影响平仓流程
        }
    }
    
    /**
     * 查询用户的平仓记录
     */
    public java.util.List<ClosePositionRecord> getClosePositionRecords(String userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    
    /**
     * 查询用户在指定交易对的平仓记录
     */
    public java.util.List<ClosePositionRecord> getClosePositionRecords(String userId, String symbol) {
        return repository.findByUserIdAndSymbolOrderByCreatedAtDesc(userId, symbol);
    }
    
    /**
     * 统计用户的总盈亏
     */
    public BigDecimal getTotalRealizedPnl(String userId) {
        return repository.sumRealizedPnlByUserId(userId);
    }
    
    /**
     * 统计用户在指定交易对的总盈亏
     */
    public BigDecimal getTotalRealizedPnl(String userId, String symbol) {
        return repository.sumRealizedPnlByUserIdAndSymbol(userId, symbol);
    }
}

