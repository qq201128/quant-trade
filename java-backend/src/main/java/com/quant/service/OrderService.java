package com.quant.service;

import com.quant.model.StrategyResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * 订单服务
 * 负责实际交易订单的执行
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final AccountService accountService;
    private final ProfitCountService profitCountService;

    // 开仓冷却期缓存：Key = userId:symbol:side, Value = 上次开仓时间戳
    private final java.util.Map<String, Long> lastOpenTimeCache = new java.util.concurrent.ConcurrentHashMap<>();
    // 开仓冷却期（毫秒）：30秒内不允许同一方向重复开仓
    private static final long OPEN_COOLDOWN_MS = 30_000;
    
    /**
     * 执行交易订单
     */
    public Mono<String> executeOrder(String userId, String symbol, StrategyResponse response) {
//        log.info("执行订单: userId={}, symbol={}, signal={}, position={}",
//                userId, symbol, response.getSignal(), response.getPosition());
        
        // 如果userId为空，无法执行订单
        if (userId == null || userId.isEmpty()) {
            log.warn("userId为空，无法执行订单: symbol={}, signal={}", symbol, response.getSignal());
            return Mono.just("SKIP_NO_USER");
        }
        
        // 检查信号
        String signal = response.getSignal();
        if ("HOLD".equals(signal)) {
            log.debug("策略信号为HOLD，不执行订单");
            return Mono.just("SKIP_HOLD");
        }
        
        // 从metadata中获取margin（开仓/补仓金额）
        java.util.Map<String, Object> metadata = response.getMetadata();
        BigDecimal margin = null;
        if (metadata != null) {
            log.debug("策略响应metadata内容: {}", metadata);
            if (metadata.containsKey("margin")) {
                Object marginObj = metadata.get("margin");
                log.debug("margin对象类型: {}, 值: {}", marginObj != null ? marginObj.getClass().getName() : "null", marginObj);
                if (marginObj instanceof Number) {
                    margin = BigDecimal.valueOf(((Number) marginObj).doubleValue());
                    log.info("从策略响应中获取开仓金额: {} USDT", margin);
                } else {
                    log.warn("margin不是Number类型: {}", marginObj);
                }
            } else {
                log.debug("metadata中不包含margin字段");
            }
        } else {
            log.warn("策略响应metadata为null");
        }
        
        // 兜底逻辑：如果是双向策略的开仓/补仓信号（BUY/SELL且position=0.5），但没有margin，使用默认值
        if (margin == null && !"HOLD".equals(signal) && !"DUAL_OPEN".equals(signal)) {
            BigDecimal positionRatio = response.getPosition();
            boolean isClosePosition = positionRatio != null && positionRatio.compareTo(BigDecimal.ONE) >= 0;
            
            if (!isClosePosition && positionRatio != null && positionRatio.compareTo(new BigDecimal("0.5")) == 0) {
                // 双向策略的补仓/开仓操作，默认使用0.5U
                margin = new BigDecimal("0.5");
                log.info("策略未提供margin，使用默认值: {} USDT (signal={}, position={})", margin, signal, positionRatio);
            }
        }
        
        // 处理双向开仓（DUAL_OPEN信号）
        if ("DUAL_OPEN".equals(signal)) {
            // 双向策略初始开仓：需要同时开多空两个仓位
            if (margin != null && margin.compareTo(BigDecimal.ZERO) > 0) {
                // 检查双向开仓冷却期
                String longCooldownKey = userId + ":" + symbol + ":LONG";
                String shortCooldownKey = userId + ":" + symbol + ":SHORT";
                Long lastLongOpenTime = lastOpenTimeCache.get(longCooldownKey);
                Long lastShortOpenTime = lastOpenTimeCache.get(shortCooldownKey);
                long now = System.currentTimeMillis();
                if ((lastLongOpenTime != null && (now - lastLongOpenTime) < OPEN_COOLDOWN_MS) ||
                    (lastShortOpenTime != null && (now - lastShortOpenTime) < OPEN_COOLDOWN_MS)) {
                    log.info("双向开仓冷却期内，跳过开仓: userId={}, symbol={}", userId, symbol);
                    return Mono.just("SKIP_COOLDOWN");
                }

                log.info("双向策略初始开仓: userId={}, symbol={}, margin={} USDT（每个方向）",
                        userId, symbol, margin);

                // 同时开多空两个仓位（响应式），双向策略使用50倍杠杆
                Mono<Boolean> longMono = accountService.openPositionReactive(userId, symbol, "LONG", null, margin, "DUAL_DIRECTION");
                Mono<Boolean> shortMono = accountService.openPositionReactive(userId, symbol, "SHORT", null, margin, "DUAL_DIRECTION");

                return Mono.zip(longMono, shortMono)
                        .map(tuple -> {
                            boolean longSuccess = tuple.getT1();
                            boolean shortSuccess = tuple.getT2();

                            // 更新冷却期缓存
                            long currentTime = System.currentTimeMillis();
                            if (longSuccess) {
                                lastOpenTimeCache.put(longCooldownKey, currentTime);
                            }
                            if (shortSuccess) {
                                lastOpenTimeCache.put(shortCooldownKey, currentTime);
                            }

                            if (longSuccess && shortSuccess) {
                                return "DUAL_ORDER_SUCCESS_" + currentTime;
                            } else {
                                log.warn("双向开仓部分失败: userId={}, symbol={}, longSuccess={}, shortSuccess={}",
                                        userId, symbol, longSuccess, shortSuccess);
                                return "DUAL_ORDER_PARTIAL_" + currentTime;
                            }
                        });
            } else {
                log.warn("双向开仓但未提供margin: userId={}, symbol={}", userId, symbol);
                return Mono.just("SKIP_NO_MARGIN");
            }
        }
        
        // 检查是否是平仓操作（position_ratio = 1.0 表示全部平仓）
        BigDecimal positionRatio = response.getPosition();
        boolean isClosePosition = positionRatio != null && positionRatio.compareTo(BigDecimal.ONE) >= 0;
        
        if (isClosePosition) {
            // 平仓操作：根据signal确定持仓方向
            String side = null;
            if ("BUY".equals(signal)) {
                side = "SHORT";  // BUY信号平空头 = 关闭SHORT持仓
            } else if ("SELL".equals(signal)) {
                side = "LONG";   // SELL信号平多头 = 关闭LONG持仓
            }
            
            if (side == null) {
                log.warn("无法确定平仓方向: signal={}", signal);
                return Mono.just("SKIP_INVALID_SIGNAL");
            }
            
            log.info("执行平仓操作: userId={}, symbol={}, side={}, positionRatio={}", 
                    userId, symbol, side, positionRatio);
            
            // 从metadata中获取策略名称
            String strategyName = null;
            if (metadata != null && metadata.containsKey("strategy")) {
                strategyName = String.valueOf(metadata.get("strategy"));
            }
            
            // 调用AccountService平仓（响应式版本，全部平仓，不指定数量，传递策略名称）
            return accountService.closePositionReactive(userId, symbol, side, null, null, strategyName)
                    .map(success -> {
                        if (success) {
                            return "CLOSE_ORDER_SUCCESS_" + System.currentTimeMillis();
                        } else {
                            return "CLOSE_ORDER_FAILED_" + System.currentTimeMillis();
                        }
                    });
        }
        
        // 开仓/补仓操作：需要margin
        // 确定持仓方向
        String side = null;
        if ("BUY".equals(signal)) {
            side = "LONG";  // 买入 = 开多仓
        } else if ("SELL".equals(signal)) {
            side = "SHORT";  // 卖出 = 开空仓
        }

        if (side == null) {
            log.warn("无法确定持仓方向: signal={}", signal);
            return Mono.just("SKIP_INVALID_SIGNAL");
        }

        // 开仓冷却期检查：防止同一方向短时间内重复开仓（解决持仓数据延迟导致的重复开仓问题）
        String cooldownKey = userId + ":" + symbol + ":" + side;
        Long lastOpenTime = lastOpenTimeCache.get(cooldownKey);
        long now = System.currentTimeMillis();
        if (lastOpenTime != null && (now - lastOpenTime) < OPEN_COOLDOWN_MS) {
            long remainingSeconds = (OPEN_COOLDOWN_MS - (now - lastOpenTime)) / 1000;
            log.info("开仓冷却期内，跳过开仓: userId={}, symbol={}, side={}, 剩余冷却时间={}秒",
                    userId, symbol, side, remainingSeconds);
            return Mono.just("SKIP_COOLDOWN");
        }
        
        // 如果提供了margin，使用保证金开仓
        if (margin != null && margin.compareTo(BigDecimal.ZERO) > 0) {
            log.info("使用保证金开仓: userId={}, symbol={}, side={}, margin={} USDT", 
                    userId, symbol, side, margin);
            
            // 从metadata中判断是否是双向策略
            String strategyType = null;
            if (metadata != null && metadata.containsKey("strategy")) {
                String strategy = String.valueOf(metadata.get("strategy"));
                if ("DualDirectionStrategy".equals(strategy)) {
                    strategyType = "DUAL_DIRECTION";
                }
            }
            
            // 判断是否是补仓操作（positionRatio < 1.0 且已有持仓）
            // 声明为final以便在lambda中使用
            final String finalSide = side;
            final boolean isAddPosition = positionRatio != null && positionRatio.compareTo(BigDecimal.ONE) < 0;
            final boolean isRebalance = metadata != null
                    && "REBALANCE".equals(String.valueOf(metadata.get("addPositionType")));
            
            // 调用AccountService按保证金开仓（响应式），传递策略类型
            final String finalCooldownKey = cooldownKey;
            return accountService.openPositionReactive(userId, symbol, side, null, margin, strategyType)
                    .flatMap(success -> {
                        if (success) {
                            // 开仓成功，更新冷却期缓存
                            lastOpenTimeCache.put(finalCooldownKey, System.currentTimeMillis());
                            // 如果是补仓操作，增加补仓次数
                            if (isAddPosition && !isRebalance && profitCountService != null) {
                                return profitCountService.incrementAddCount(userId, symbol, finalSide)
                                        .thenReturn("ORDER_SUCCESS_" + System.currentTimeMillis());
                            }
                            return Mono.just("ORDER_SUCCESS_" + System.currentTimeMillis());
                        } else {
                            return Mono.just("ORDER_FAILED_" + System.currentTimeMillis());
                        }
                    });
        } else {
            // 如果没有提供margin，无法执行开仓
            log.warn("策略响应中未提供margin，无法执行按金额开仓: userId={}, symbol={}, signal={}, positionRatio={}", 
                    userId, symbol, signal, positionRatio);
            return Mono.just("SKIP_NO_MARGIN");
        }
    }
}
