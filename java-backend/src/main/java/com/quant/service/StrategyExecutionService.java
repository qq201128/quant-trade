package com.quant.service;

import com.quant.model.StrategyConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 策略执行服务
 * 负责管理策略的执行循环
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyExecutionService {

    private final TradingEngine tradingEngine;
    private final StrategyManager strategyManager;
    private final StrategyConfigService strategyConfigService;

    // 存储运行中的策略：userId -> strategyName -> isRunning
    private final Map<String, Map<String, AtomicBoolean>> runningStrategies = new ConcurrentHashMap<>();

    /**
     * 服务启动时恢复已启用的策略
     */
    @PostConstruct
    public void restoreEnabledStrategies() {
        // 延迟执行，等待其他服务初始化完成
        new Thread(() -> {
            try {
                Thread.sleep(10000); // 等待10秒
                doRestoreStrategies();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void doRestoreStrategies() {
        log.info("开始恢复已启用的策略...");
        try {
            List<StrategyConfig> enabledStrategies = strategyConfigService.getEnabledStrategies();
            if (enabledStrategies == null || enabledStrategies.isEmpty()) {
                log.info("没有需要恢复的策略");
                return;
            }

            for (StrategyConfig config : enabledStrategies) {
                try {
                    log.info("恢复策略: userId={}, strategyName={}, strategyType={}",
                            config.getUserId(), config.getStrategyName(), config.getStrategyType());
                    startStrategy(config.getUserId(), config.getStrategyName(),
                            config.getStrategyType(), config.getExchangeType());
                } catch (Exception e) {
                    log.error("恢复策略失败: userId={}, strategyName={}, error={}",
                            config.getUserId(), config.getStrategyName(), e.getMessage());
                }
            }
            log.info("策略恢复完成，共恢复 {} 个策略", enabledStrategies.size());
        } catch (Exception e) {
            log.error("恢复策略过程出错: {}", e.getMessage(), e);
        }
    }

    /**
     * 启动策略
     */
    public boolean startStrategy(String userId, String strategyName, String strategyType, String exchangeType) {
        log.info("启动策略: userId={}, strategyName={}, strategyType={}, exchangeType={}",
                userId, strategyName, strategyType, exchangeType);

        // 检查Python策略服务是否可用
        if (!checkPythonServiceAvailable()) {
            log.error("Python策略服务不可用，无法启动策略");
            return false;
        }

        // 获取或创建用户的策略映射
        Map<String, AtomicBoolean> userStrategies = runningStrategies.computeIfAbsent(
                userId, k -> new ConcurrentHashMap<>());

        // 检查策略是否已在运行
        AtomicBoolean isRunning = userStrategies.get(strategyName);
        if (isRunning != null && isRunning.get()) {
            log.warn("策略已在运行: userId={}, strategyName={}", userId, strategyName);
            return false;
        }

        // 标记策略为运行中
        userStrategies.put(strategyName, new AtomicBoolean(true));

        // 更新数据库状态
        strategyConfigService.enableStrategy(userId, strategyName, exchangeType);

        // 启动策略执行循环（异步执行）
        executeStrategyLoop(userId, strategyName, strategyType, exchangeType);

        return true;
    }
    
    /**
     * 停止策略
     */
    public boolean stopStrategy(String userId, String strategyName) {
        log.info("停止策略: userId={}, strategyName={}", userId, strategyName);

        Map<String, AtomicBoolean> userStrategies = runningStrategies.get(userId);
        if (userStrategies == null) {
            // 即使内存中没有，也更新数据库状态
            strategyConfigService.disableStrategy(userId, strategyName);
            return false;
        }

        AtomicBoolean isRunning = userStrategies.get(strategyName);
        if (isRunning == null || !isRunning.get()) {
            log.warn("策略未在运行: userId={}, strategyName={}", userId, strategyName);
            // 即使内存中没有，也更新数据库状态
            strategyConfigService.disableStrategy(userId, strategyName);
            return false;
        }

        // 停止策略
        isRunning.set(false);
        userStrategies.remove(strategyName);

        // 更新数据库状态
        strategyConfigService.disableStrategy(userId, strategyName);

        // 如果用户没有其他运行中的策略，移除用户映射
        if (userStrategies.isEmpty()) {
            runningStrategies.remove(userId);
        }

        return true;
    }
    
    /**
     * 检查策略是否在运行
     */
    public boolean isStrategyRunning(String userId, String strategyName) {
        Map<String, AtomicBoolean> userStrategies = runningStrategies.get(userId);
        if (userStrategies == null) {
            return false;
        }
        
        AtomicBoolean isRunning = userStrategies.get(strategyName);
        return isRunning != null && isRunning.get();
    }
    
    /**
     * 策略执行循环（异步执行）
     */
    @Async
    public void executeStrategyLoop(String userId, String strategyName, String strategyType, String exchangeType) {
        log.info("开始策略执行循环: userId={}, strategyName={}, strategyType={}", 
                userId, strategyName, strategyType);
        
        // 获取策略运行状态
        Map<String, AtomicBoolean> userStrategies = runningStrategies.get(userId);
        if (userStrategies == null) {
            return;
        }
        
        AtomicBoolean isRunning = userStrategies.get(strategyName);
        if (isRunning == null) {
            return;
        }
        
        // 策略执行循环（每30秒执行一次）
        while (isRunning.get()) {
            try {
                log.debug("执行策略: userId={}, strategyName={}", userId, strategyName);
                
                // 从策略配置中获取交易对列表
                List<String> symbols = getStrategySymbols(userId, strategyName);
                if (symbols == null || symbols.isEmpty()) {
                    // 如果没有配置，使用默认交易对
                    symbols = java.util.Arrays.asList("BTC/USDT");
                    log.warn("策略未配置交易对，使用默认: BTC/USDT");
                }
                
                // 将策略名称转换为Python服务能识别的格式
                String pythonStrategyName = convertToPythonStrategyName(strategyName, strategyType);
                
                // 对每个交易对执行策略
                for (String symbol : symbols) {
                    // 转换交易对格式：BTC/USDT -> BTCUSDT (Binance永续合约格式)
                    String normalizedSymbol = normalizeSymbol(symbol);
                    
                    // 执行交易流程（传递userId）
                    tradingEngine.executeTrading(userId, normalizedSymbol, pythonStrategyName)
                            .doOnSuccess(v -> log.debug("策略执行成功: userId={}, strategyName={}, symbol={}", 
                                    userId, strategyName, symbol))
                            .doOnError(error -> log.error("策略执行失败: userId={}, strategyName={}, error={}", 
                                    userId, strategyName, error.getMessage()))
                            .block(); // 等待执行完成
                }
                
                // 等待10秒后再次执行
                Thread.sleep(10000);
                
            } catch (InterruptedException e) {
                log.info("策略执行循环被中断: userId={}, strategyName={}", userId, strategyName);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("策略执行循环异常: userId={}, strategyName={}, error={}", 
                        userId, strategyName, e.getMessage(), e);
                
                // 发生异常时等待10秒后重试
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        log.info("策略执行循环结束: userId={}, strategyName={}", userId, strategyName);
    }
    
    /**
     * 检查Python策略服务是否可用
     */
    private boolean checkPythonServiceAvailable() {
        try {
            // 调用Python服务的健康检查接口
            return strategyManager.healthCheck()
                    .block(java.time.Duration.ofSeconds(2));
        } catch (Exception e) {
            log.warn("Python策略服务健康检查失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 将策略名称转换为Python服务能识别的格式
     */
    private String convertToPythonStrategyName(String strategyName, String strategyType) {
        // 根据策略类型映射到Python策略名称
        if ("DUAL_DIRECTION".equals(strategyType)) {
            return "dual_direction_strategy";
        }
        // 已禁用的策略类型
        // else if ("GRID".equals(strategyType)) {
        //     return "grid_strategy";
        // } else if ("NORMAL".equals(strategyType)) {
        //     return "ma_strategy"; // 默认使用均线策略
        // }
        
        // 默认返回小写下划线格式
        return strategyName.toLowerCase().replaceAll("\\s+", "_") + "_strategy";
    }
    
    
    /**
     * 从策略配置中获取交易对列表
     */
    @SuppressWarnings("unchecked")
    private List<String> getStrategySymbols(String userId, String strategyName) {
        Map<String, Object> config = strategyConfigService.getStrategyConfig(userId, strategyName);
        if (config == null) {
            return null;
        }
        
        // 支持两种格式：
        // 1. symbols: ["BTC/USDT", "ETH/USDT"]
        // 2. symbol: "BTC/USDT" (单个交易对)
        Object symbolsObj = config.get("symbols");
        if (symbolsObj instanceof List) {
            return (List<String>) symbolsObj;
        }
        
        Object symbolObj = config.get("symbol");
        if (symbolObj instanceof String) {
            return java.util.Arrays.asList((String) symbolObj);
        }
        
        return null;
    }
    
    /**
     * 标准化交易对格式
     * 将用户输入的格式转换为交易所API需要的格式
     * 
     * 支持的输入格式：
     * - BTC/USDT -> BTCUSDT (Binance永续合约)
     * - BTCUSDT -> BTCUSDT (已经是正确格式)
     * - BTC-USDT -> BTCUSDT (OKX格式转换为Binance格式)
     * 
     * @param symbol 原始交易对
     * @return 标准化后的交易对
     */
    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return symbol;
        }
        
        // 移除所有分隔符（斜杠、横线等），转换为大写
        String normalized = symbol.replaceAll("[/-]", "").toUpperCase();
        
        log.debug("交易对格式转换: {} -> {}", symbol, normalized);
        return normalized;
    }
}

