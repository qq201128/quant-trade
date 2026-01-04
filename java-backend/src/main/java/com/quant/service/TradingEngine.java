package com.quant.service;

import com.quant.model.StrategyRequest;
import com.quant.model.StrategyResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * 交易引擎
 * 核心交易逻辑控制
 * 
 * 流程：
 * 1. 收集市场数据
 * 2. 调用Python策略获取信号
 * 3. 风险控制检查
 * 4. 执行交易订单
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradingEngine {
    
    private final StrategyManager strategyManager;
    private final RiskController riskController;
    private final OrderService orderService;
    private final AccountService accountService;
    private final ProfitCountService profitCountService;
    
    // 缓存上一次的盈利状态，避免重复计数
    // Key: userId:symbol:side, Value: 是否达到50%盈利
    private final java.util.Map<String, Boolean> previousProfit50Status = new java.util.concurrent.ConcurrentHashMap<>();
    
    /**
     * 执行交易流程
     */
    public Mono<Void> executeTrading(String symbol, String strategyName) {
        return executeTrading(null, symbol, strategyName);
    }
    
    /**
     * 执行交易流程（带userId）
     */
    public Mono<Void> executeTrading(String userId, String symbol, String strategyName) {
        log.info("开始执行交易流程: userId={}, symbol={}, strategyName={}", userId, symbol, strategyName);
        
        // 0. 先尝试获取持仓信息，确保网络连接正常
        // 如果获取持仓失败（网络错误），直接返回，不执行策略，避免误开仓/补仓
        try {
            java.util.Map<String, Object> positionInfo = getCurrentPosition(userId, symbol);
            
            // 检查是否成功获取持仓信息
            // 如果获取失败，positionInfo会包含错误标志
            Boolean positionFetchSuccess = (Boolean) positionInfo.get("_fetchSuccess");
            if (positionFetchSuccess != null && !positionFetchSuccess) {
                log.error("获取持仓信息失败，停止执行交易流程，避免误开仓/补仓: userId={}, symbol={}", userId, symbol);
                return Mono.empty();
            }
            
            // 检查价格数据是否有效
            java.util.Map<String, Object> marketData = fetchMarketData(userId, symbol);
            Double price = (Double) marketData.get("price");
            if (price == null || price <= 0) {
                log.error("无法获取有效价格数据，停止执行交易流程: userId={}, symbol={}, price={}", userId, symbol, price);
                return Mono.empty();
            }
            
        } catch (Exception e) {
            log.error("获取持仓或市场数据时发生异常，停止执行交易流程，避免误开仓/补仓: userId={}, symbol={}, error={}", 
                    userId, symbol, e.getMessage(), e);
            return Mono.empty();
        }
        
        // 1. 构建策略请求（此时已经确认数据获取成功）
        StrategyRequest request = StrategyRequest.builder()
                .strategyName(strategyName)
                .symbol(symbol)
                .marketData(fetchMarketData(userId, symbol))
                .strategyParams(getStrategyParams(strategyName))
                .position(getCurrentPosition(userId, symbol))
                .account(getAccountInfo())
                .build();
        
        // 2. 调用Python策略
        return strategyManager.executeStrategy(request)
                .flatMap(response -> {
                    // 3. 风险控制检查
                    if (!riskController.validateSignal(response)) {
                        log.warn("策略信号未通过风控检查: {}", response.getSignal());
                        return Mono.empty();
                    }
                    
                    // 4. 执行订单
                    return orderService.executeOrder(userId, symbol, response)
                            .doOnSuccess(orderId -> 
                                log.info("订单执行成功: orderId={}", orderId))
                            .doOnError(error -> 
                                log.error("订单执行失败: {}", error.getMessage()))
                            .then();
                })
                .onErrorResume(error -> {
                    log.error("交易流程执行失败: {}", error.getMessage());
                    return Mono.empty();
                });
    }
    
    // 辅助方法
    private java.util.Map<String, Object> fetchMarketData(String userId, String symbol) {
        BigDecimal price = BigDecimal.ZERO;

        // 从持仓或交易所API获取真实价格
        if (userId != null && accountService != null) {
            try {
                com.quant.model.AccountInfo accountInfo = accountService.getAccountInfo(userId);
                if (accountInfo != null && accountInfo.getPositions() != null) {
                    for (com.quant.model.Position pos : accountInfo.getPositions()) {
                        if (pos.getSymbol().equals(symbol) && pos.getCurrentPrice() != null
                                && pos.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0) {
                            price = pos.getCurrentPrice();
                            log.debug("从持仓获取价格: symbol={}, price={}", symbol, price);
                            break;
                        }
                    }
                }

                // 如果没有持仓价格，尝试从交易所API获取
                if (price.compareTo(BigDecimal.ZERO) <= 0) {
                    BigDecimal apiPrice = accountService.getRealTimePrice(userId, symbol);
                    if (apiPrice != null && apiPrice.compareTo(BigDecimal.ZERO) > 0) {
                        price = apiPrice;
                        log.debug("从API获取价格: symbol={}, price={}", symbol, price);
                    }
                }
            } catch (Exception e) {
                log.warn("获取实时价格失败: userId={}, symbol={}, error={}", userId, symbol, e.getMessage());
            }
        }

        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("无法获取实时价格: userId={}, symbol={}", userId, symbol);
        }

        return java.util.Map.of(
            "price", price.doubleValue(),
            "volume", 1000000,
            "timestamp", System.currentTimeMillis()
        );
    }
    
    private java.util.Map<String, Object> getStrategyParams(String strategyName) {
        // 从配置获取策略参数
        return java.util.Map.of("ma_period", 20);
    }
    
    private java.util.Map<String, Object> getCurrentPosition(String userId, String symbol) {
        // 从AccountService获取真实持仓信息
        if (userId != null && accountService != null) {
            try {
                com.quant.model.AccountInfo accountInfo = accountService.getAccountInfo(userId);
                
                // 检查是否成功获取账户信息
                if (accountInfo == null) {
                    log.error("获取账户信息返回null，可能网络连接失败: userId={}, symbol={}", userId, symbol);
                    java.util.Map<String, Object> errorPosition = new java.util.HashMap<>();
                    errorPosition.put("_fetchSuccess", false);
                    errorPosition.put("_error", "账户信息获取失败");
                    return errorPosition;
                }
                
                if (accountInfo.getPositions() != null) {
                    // 初始化多空持仓信息
                    BigDecimal longQuantity = BigDecimal.ZERO;
                    BigDecimal shortQuantity = BigDecimal.ZERO;
                    BigDecimal longOpenRate = BigDecimal.ZERO;
                    BigDecimal shortOpenRate = BigDecimal.ZERO;
                    BigDecimal longProfitPct = BigDecimal.ZERO;
                    BigDecimal shortProfitPct = BigDecimal.ZERO;
                    Integer longLeverage = 0;
                    Integer shortLeverage = 0;
                    Integer longProfitCount = 0;
                    Integer shortProfitCount = 0;
                    Integer longAddCount = 0;
                    Integer shortAddCount = 0;
                    
                    // 查找指定交易对的所有持仓（可能同时有多空两个方向）
                    for (com.quant.model.Position pos : accountInfo.getPositions()) {
                        if (pos.getSymbol().equals(symbol)) {
                            if ("LONG".equals(pos.getSide())) {
                                longQuantity = pos.getQuantity() != null ? pos.getQuantity() : BigDecimal.ZERO;
                                longOpenRate = pos.getAvgPrice() != null ? pos.getAvgPrice() : BigDecimal.ZERO;
                                // 获取盈利百分比（从Position对象中获取，这是相对于保证金的盈亏百分比）
                                longProfitPct = pos.getPnlPercentage() != null ? pos.getPnlPercentage() : BigDecimal.ZERO;
                                // 获取杠杆倍数
                                longLeverage = pos.getLeverage() != null ? pos.getLeverage() : 0;
                            } else if ("SHORT".equals(pos.getSide())) {
                                shortQuantity = pos.getQuantity() != null ? pos.getQuantity() : BigDecimal.ZERO;
                                shortOpenRate = pos.getAvgPrice() != null ? pos.getAvgPrice() : BigDecimal.ZERO;
                                // 获取盈利百分比（从Position对象中获取，这是相对于保证金的盈亏百分比）
                                shortProfitPct = pos.getPnlPercentage() != null ? pos.getPnlPercentage() : BigDecimal.ZERO;
                                // 获取杠杆倍数
                                shortLeverage = pos.getLeverage() != null ? pos.getLeverage() : 0;
                            }
                        }
                    }
                    
                    // 从Redis获取盈利次数和补仓次数，并处理盈利达到50%的情况
                    if (userId != null && profitCountService != null) {
                        try {
                            // 计算实际盈亏百分比（乘以杠杆）
                            BigDecimal longProfitPctWithLeverage = longProfitPct.multiply(
                                    new BigDecimal(longLeverage > 0 ? longLeverage : 50));
                            BigDecimal shortProfitPctWithLeverage = shortProfitPct.multiply(
                                    new BigDecimal(shortLeverage > 0 ? shortLeverage : 50));
                            
                            // 检查是否达到50%盈利（使用乘以杠杆后的百分比）
                            boolean longReached50 = longQuantity.compareTo(BigDecimal.ZERO) > 0 
                                    && longProfitPctWithLeverage.compareTo(new BigDecimal("50.0")) >= 0;
                            boolean shortReached50 = shortQuantity.compareTo(BigDecimal.ZERO) > 0 
                                    && shortProfitPctWithLeverage.compareTo(new BigDecimal("50.0")) >= 0;
                            
                            // 获取之前的盈利状态（从缓存中获取）
                            String longKey = userId + ":" + symbol + ":LONG";
                            String shortKey = userId + ":" + symbol + ":SHORT";
                            Boolean previousLongReached50 = previousProfit50Status.getOrDefault(longKey, false);
                            Boolean previousShortReached50 = previousProfit50Status.getOrDefault(shortKey, false);
                            
                            // 处理盈利达到50%的情况（只有当从<50%变为>=50%时，才增加计数）
                            if (longReached50 && !previousLongReached50) {
                                profitCountService.handleProfitReached50(userId, symbol, "LONG", false, true)
                                        .block(); // 同步等待
                                log.info("检测到多头盈利达到50%: userId={}, symbol={}, profitPct={}%", 
                                        userId, symbol, longProfitPctWithLeverage);
                            }
                            if (shortReached50 && !previousShortReached50) {
                                profitCountService.handleProfitReached50(userId, symbol, "SHORT", false, true)
                                        .block(); // 同步等待
                                log.info("检测到空头盈利达到50%: userId={}, symbol={}, profitPct={}%", 
                                        userId, symbol, shortProfitPctWithLeverage);
                            }
                            
                            // 更新缓存
                            previousProfit50Status.put(longKey, longReached50);
                            previousProfit50Status.put(shortKey, shortReached50);
                            
                            // 获取盈利次数和补仓次数
                            java.util.Map<String, Integer> longCounts = profitCountService.getCounts(userId, symbol, "LONG")
                                    .block(); // 同步等待
                            java.util.Map<String, Integer> shortCounts = profitCountService.getCounts(userId, symbol, "SHORT")
                                    .block(); // 同步等待
                            
                            if (longCounts != null) {
                                longProfitCount = longCounts.getOrDefault("profitCount", 0);
                                longAddCount = longCounts.getOrDefault("addCount", 0);
                            }
                            if (shortCounts != null) {
                                shortProfitCount = shortCounts.getOrDefault("profitCount", 0);
                                shortAddCount = shortCounts.getOrDefault("addCount", 0);
                            }
                        } catch (Exception e) {
                            log.warn("从Redis获取盈利次数失败: userId={}, symbol={}, error={}", userId, symbol, e.getMessage());
                        }
                    }
                    
                    // 构建返回的持仓信息
                    java.util.Map<String, Object> positionMap = new java.util.HashMap<>();
                    positionMap.put("longQuantity", longQuantity);
                    positionMap.put("shortQuantity", shortQuantity);
                    positionMap.put("longOpenRate", longOpenRate);
                    positionMap.put("shortOpenRate", shortOpenRate);
                    positionMap.put("longProfitPct", longProfitPct);  // 盈利百分比（相对于保证金）
                    positionMap.put("shortProfitPct", shortProfitPct);  // 盈利百分比（相对于保证金）
                    positionMap.put("longLeverage", longLeverage);  // 杠杆倍数
                    positionMap.put("shortLeverage", shortLeverage);  // 杠杆倍数
                    positionMap.put("longProfitCount", longProfitCount);  // 盈利次数（暂时为0，需要从策略参数中获取）
                    positionMap.put("shortProfitCount", shortProfitCount);  // 盈利次数（暂时为0，需要从策略参数中获取）
                    positionMap.put("longAddCount", longAddCount);  // 补仓次数（暂时为0，需要从策略参数中获取）
                    positionMap.put("shortAddCount", shortAddCount);  // 补仓次数（暂时为0，需要从策略参数中获取）
                    positionMap.put("quantity", longQuantity.add(shortQuantity)); // 总持仓数量
                    // 平均价格（如果有持仓）
                    if (longQuantity.compareTo(BigDecimal.ZERO) > 0 || shortQuantity.compareTo(BigDecimal.ZERO) > 0) {
                        if (longQuantity.compareTo(BigDecimal.ZERO) > 0) {
                            positionMap.put("avgPrice", longOpenRate);
                        } else {
                            positionMap.put("avgPrice", shortOpenRate);
                        }
                    } else {
                        positionMap.put("avgPrice", BigDecimal.ZERO);
                    }
                    
                    log.debug("获取持仓信息: userId={}, symbol={}, longQuantity={}, shortQuantity={}, longOpenRate={}, shortOpenRate={}", 
                            userId, symbol, longQuantity, shortQuantity, longOpenRate, shortOpenRate);
                    
                    // 标记成功获取
                    positionMap.put("_fetchSuccess", true);
                    return positionMap;
                } else {
                    // 账户信息存在但持仓列表为null，可能是网络错误
                    log.warn("账户信息存在但持仓列表为null，可能网络连接异常: userId={}, symbol={}", userId, symbol);
                    java.util.Map<String, Object> errorPosition = new java.util.HashMap<>();
                    errorPosition.put("_fetchSuccess", false);
                    errorPosition.put("_error", "持仓列表获取失败");
                    return errorPosition;
                }
            } catch (org.springframework.web.reactive.function.client.WebClientRequestException e) {
                // 网络连接错误（如代理连接失败）
                log.error("获取持仓信息时发生网络连接错误，停止交易流程: userId={}, symbol={}, error={}", 
                        userId, symbol, e.getMessage());
                java.util.Map<String, Object> errorPosition = new java.util.HashMap<>();
                errorPosition.put("_fetchSuccess", false);
                errorPosition.put("_error", "网络连接错误: " + e.getMessage());
                return errorPosition;
            } catch (Exception e) {
                // 其他异常
                log.error("获取持仓信息失败: userId={}, symbol={}, error={}", userId, symbol, e.getMessage(), e);
                java.util.Map<String, Object> errorPosition = new java.util.HashMap<>();
                errorPosition.put("_fetchSuccess", false);
                errorPosition.put("_error", "获取持仓信息异常: " + e.getMessage());
                return errorPosition;
            }
        }
        
        // 默认返回空持仓（但标记为成功，因为可能是真的没有持仓）
        java.util.Map<String, Object> emptyPosition = new java.util.HashMap<>();
        emptyPosition.put("longQuantity", BigDecimal.ZERO);
        emptyPosition.put("shortQuantity", BigDecimal.ZERO);
        emptyPosition.put("longOpenRate", BigDecimal.ZERO);
        emptyPosition.put("shortOpenRate", BigDecimal.ZERO);
        emptyPosition.put("quantity", BigDecimal.ZERO);
        emptyPosition.put("avgPrice", BigDecimal.ZERO);
        emptyPosition.put("_fetchSuccess", true);  // 标记为成功（可能是真的没有持仓）
        return emptyPosition;
    }
    
    private java.util.Map<String, Object> getAccountInfo() {
        // 获取账户信息
        return java.util.Map.of("balance", 100000.0, "available", 100000.0);
    }
}

