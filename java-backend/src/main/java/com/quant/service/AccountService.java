package com.quant.service;

import com.quant.exchange.ExchangeAdapter;
import com.quant.exchange.ExchangeAdapterFactory;
import com.quant.model.AccountInfo;
import com.quant.model.ExchangeType;
import com.quant.model.Position;
import com.quant.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 账户服务
 * 管理用户账户信息，实时同步交易所数据，并通过WebSocket推送
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {
    
    private final ExchangeAdapterFactory adapterFactory;
    private final AuthService authService;
    private final ExchangeConfigService exchangeConfigService;
    private final SimpMessagingTemplate messagingTemplate;
    
    // 缓存账户信息（userId -> AccountInfo）
    private final Map<String, AccountInfo> accountCache = new ConcurrentHashMap<>();
    
    // 缓存持仓信息（userId -> List<Position>）
    private final Map<String, List<Position>> positionsCache = new ConcurrentHashMap<>();
    
    // 持仓信息最后更新时间（userId -> timestamp）
    private final Map<String, Long> positionsLastUpdate = new ConcurrentHashMap<>();
    
    // 存储每个用户的交易所适配器
    private final Map<String, ExchangeAdapter> userAdapters = new ConcurrentHashMap<>();
    
        // 持仓信息缓存有效期（毫秒），2秒内不重复获取（提高频率以实时更新盈亏）
        private static final long POSITIONS_CACHE_TTL = 2000;
    
    // 用于延迟推送的调度器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    /**
     * 初始化用户交易所连接
     */
    public Mono<Void> initializeUserExchange(String userId) {
        return Mono.fromCallable(() -> {
            User user = authService.getUserByUserId(userId);
            
            if (user.getExchangeType() == null) {
                log.warn("用户 {} 未设置交易所类型", userId);
                return null;
            }
            
            // 从交易所配置表获取配置
            com.quant.model.ExchangeConfig config = exchangeConfigService.getConfig(
                    userId, user.getExchangeType());
            
            if (config == null || config.getApiKey() == null || config.getSecretKey() == null) {
                log.warn("用户 {} 的交易所 {} 配置不完整", userId, user.getExchangeType());
                return null;
            }
            
            // 获取对应的交易所适配器
            ExchangeAdapter adapter = adapterFactory.getAdapter(user.getExchangeType());
            
            // 初始化适配器（设置API密钥）
            adapter.initialize(
                config.getApiKey(),
                config.getSecretKey(),
                config.getPassphrase()
            );
            
            // 存储适配器
            userAdapters.put(userId, adapter);
            
            // 订阅账户更新
            subscribeAccountUpdates(userId, adapter);
            
//            log.info("用户 {} 的交易所连接已初始化: {}", userId, user.getExchangeType());
            return null;
        }).then();
    }
    
    /**
     * 订阅账户更新并实时推送
     */
    private void subscribeAccountUpdates(String userId, ExchangeAdapter adapter) {
        // log.info("开始订阅账户更新: userId={}", userId);
        
        adapter.subscribeAccountUpdates(userId)
                .subscribe(
                    accountInfo -> {
                        // log.info("收到账户更新事件: userId={}, totalBalance={}", 
                        //         userId, accountInfo.getTotalBalance());
                        
                        // 获取持仓信息并合并到账户信息中（使用缓存避免频繁请求）
                        enrichAccountInfoWithPositions(userId, adapter, accountInfo)
                                .subscribe(
                                    enrichedAccountInfo -> {
                                        // 更新缓存
                                        accountCache.put(userId, enrichedAccountInfo);
                                        // 通过WebSocket推送（包含持仓信息）
                                        pushAccountInfo(userId, enrichedAccountInfo);
                                        // log.info("推送账户更新（含持仓）: userId={}, totalBalance={}", 
                                        //         userId, enrichedAccountInfo.getTotalBalance());
                                    },
                                    error -> {
                                    // 如果获取持仓失败，使用缓存的持仓信息
                                    List<Position> cachedPositions = positionsCache.get(userId);
                                    List<Position> positionsToUse = cachedPositions != null ? cachedPositions : 
                                            (accountInfo.getPositions() != null ? accountInfo.getPositions() : java.util.Collections.emptyList());
                                    
                                    // 计算所有持仓的总未实现盈亏
                                    java.math.BigDecimal totalUnrealizedPnl = positionsToUse.stream()
                                            .map(p -> p.getUnrealizedPnl() != null ? p.getUnrealizedPnl() : java.math.BigDecimal.ZERO)
                                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                                    
                                    // 计算账户权益 = 总资产 + 未实现盈亏
                                    java.math.BigDecimal equity = accountInfo.getTotalBalance() != null ? 
                                            accountInfo.getTotalBalance().add(totalUnrealizedPnl) : totalUnrealizedPnl;
                                    
                                    AccountInfo accountInfoWithCachedPositions = AccountInfo.builder()
                                            .userId(accountInfo.getUserId())
                                            .totalBalance(accountInfo.getTotalBalance())
                                            .availableBalance(accountInfo.getAvailableBalance())
                                            .frozenBalance(accountInfo.getFrozenBalance())
                                            .equity(equity)  // 账户权益 = 总资产 + 未实现盈亏
                                            .unrealizedPnl(totalUnrealizedPnl)  // 使用计算的总未实现盈亏
                                            .positions(positionsToUse)
                                            .timestamp(accountInfo.getTimestamp())
                                            .metadata(accountInfo.getMetadata())
                                            .build();
                                        
                                        accountCache.put(userId, accountInfoWithCachedPositions);
                                        pushAccountInfo(userId, accountInfoWithCachedPositions);
                                        log.warn("获取持仓信息失败，使用缓存持仓: userId={}, error={}", userId, error.getMessage());
                                    }
                                );
                    },
                    error -> {
                        log.error("账户更新订阅失败: userId={}, error={}", userId, error.getMessage(), error);
                    },
                    () -> {
                        log.warn("账户更新订阅流已结束: userId={}", userId);
                    }
                );
        
        // 连接建立后，立即推送一次当前账户信息（因为 Binance 只在账户变化时推送）
        // 延迟3秒推送，确保 WebSocket 连接已完全建立
        scheduler.schedule(() -> {
            try {
//                log.info("连接建立后主动推送一次账户信息（含持仓）: userId={}", userId);
                AccountInfo currentAccountInfo = getAccountInfo(userId);
                if (currentAccountInfo != null) {
                    // 确保包含持仓信息
                    enrichAccountInfoWithPositions(userId, adapter, currentAccountInfo)
                            .subscribe(
                                    enrichedAccountInfo -> {
                                        pushAccountInfo(userId, enrichedAccountInfo);
//                                        log.info("已推送初始账户信息（含持仓）: userId={}, totalBalance={}, 持仓数量={}",
//                                                userId, enrichedAccountInfo.getTotalBalance(),
//                                                enrichedAccountInfo.getPositions() != null ? enrichedAccountInfo.getPositions().size() : 0);
                                    },
                                    error -> {
                                        // 即使获取持仓失败，也推送账户信息
                                        pushAccountInfo(userId, currentAccountInfo);
                                        log.warn("获取持仓失败，仅推送账户信息: userId={}, error={}", userId, error.getMessage());
                                    }
                            );
                } else {
                    log.warn("无法获取账户信息，跳过初始推送: userId={}", userId);
                }
            } catch (Exception e) {
                log.error("推送初始账户信息失败: userId={}, error={}", userId, e.getMessage(), e);
            }
        }, 3, TimeUnit.SECONDS);
    }
    
    /**
     * 为账户信息添加持仓数据（带缓存，避免频繁请求）
     * 注意：即使使用缓存，也会使用实时标记价格更新盈亏
     */
    private Mono<AccountInfo> enrichAccountInfoWithPositions(String userId, ExchangeAdapter adapter, AccountInfo accountInfo) {
        long now = System.currentTimeMillis();
        Long lastUpdate = positionsLastUpdate.get(userId);
        
        // 如果缓存有效，使用缓存的持仓信息，但需要用实时标记价格更新盈亏
        if (lastUpdate != null && (now - lastUpdate) < POSITIONS_CACHE_TTL) {
            List<Position> cachedPositions = positionsCache.get(userId);
            if (cachedPositions != null) {
                log.debug("使用缓存的持仓信息，但会用实时标记价格更新盈亏: userId={}", userId);
                
                // 使用实时标记价格更新缓存的持仓盈亏（如果是Binance期货）
                List<Position> updatedPositions = updatePositionsWithRealTimePrice(cachedPositions, adapter);
                
                // 计算所有持仓的总未实现盈亏
                java.math.BigDecimal totalUnrealizedPnl = updatedPositions.stream()
                        .map(p -> p.getUnrealizedPnl() != null ? p.getUnrealizedPnl() : java.math.BigDecimal.ZERO)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                
                // 计算账户权益 = 总资产 + 未实现盈亏
                java.math.BigDecimal equity = accountInfo.getTotalBalance() != null ? 
                        accountInfo.getTotalBalance().add(totalUnrealizedPnl) : totalUnrealizedPnl;
                
                return Mono.just(AccountInfo.builder()
                        .userId(accountInfo.getUserId())
                        .totalBalance(accountInfo.getTotalBalance())
                        .availableBalance(accountInfo.getAvailableBalance())
                        .frozenBalance(accountInfo.getFrozenBalance())
                        .equity(equity)  // 账户权益 = 总资产 + 未实现盈亏
                        .unrealizedPnl(totalUnrealizedPnl)  // 使用计算的总未实现盈亏
                        .positions(updatedPositions)  // 使用更新后的持仓（含实时盈亏）
                        .timestamp(accountInfo.getTimestamp())
                        .metadata(accountInfo.getMetadata())
                        .build());
            }
        }
        
        // 缓存过期或不存在，从API获取
//        log.info("从API获取持仓信息: userId={}", userId);
        return adapter.getPositions(userId)
                .map(positions -> {
                    // 使用实时标记价格更新盈亏（如果是Binance期货）
                    positions = updatePositionsWithRealTimePrice(positions, adapter);
                    
                    // 更新缓存
                    positionsCache.put(userId, positions);
                    positionsLastUpdate.put(userId, now);
                    
//                    log.info("成功获取持仓信息: userId={}, 持仓数量={}", userId, positions.size());
                    if (positions.size() > 0) {
//                        log.info("持仓详情: {}", positions.stream()
//                                .map(p -> String.format("%s %s %s 盈亏=%s", p.getSymbol(), p.getSide(), p.getQuantity(), p.getUnrealizedPnl()))
//                                .reduce((a, b) -> a + ", " + b)
//                                .orElse("无"));
                    }
                    
                    // 计算所有持仓的总未实现盈亏
                    java.math.BigDecimal totalUnrealizedPnl = positions.stream()
                            .map(p -> p.getUnrealizedPnl() != null ? p.getUnrealizedPnl() : java.math.BigDecimal.ZERO)
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                    
//                    log.info("计算总未实现盈亏: userId={}, 持仓数量={}, 总未实现盈亏={}",
//                            userId, positions.size(), totalUnrealizedPnl);
                    
                    // 计算账户权益 = 总资产 + 未实现盈亏
                    java.math.BigDecimal equity = accountInfo.getTotalBalance() != null ? 
                            accountInfo.getTotalBalance().add(totalUnrealizedPnl) : totalUnrealizedPnl;
                    
                    // 创建新的AccountInfo，包含持仓信息和计算的总未实现盈亏
                    return AccountInfo.builder()
                            .userId(accountInfo.getUserId())
                            .totalBalance(accountInfo.getTotalBalance())
                            .availableBalance(accountInfo.getAvailableBalance())
                            .frozenBalance(accountInfo.getFrozenBalance())
                            .equity(equity)  // 账户权益 = 总资产 + 未实现盈亏
                            .unrealizedPnl(totalUnrealizedPnl)  // 使用计算的总未实现盈亏
                            .positions(positions)  // 添加持仓信息
                            .timestamp(accountInfo.getTimestamp())
                            .metadata(accountInfo.getMetadata())
                            .build();
                })
                .doOnError(error -> {
                    log.error("获取持仓信息失败: userId={}, error={}", userId, error.getMessage(), error);
                })
                .onErrorReturn(accountInfo);  // 如果获取持仓失败，返回原始账户信息
    }
    
    /**
     * 使用实时标记价格更新持仓盈亏（仅对Binance期货有效）
     */
    private List<Position> updatePositionsWithRealTimePrice(List<Position> positions, ExchangeAdapter adapter) {
        if (positions == null || positions.isEmpty()) {
            return positions;
        }
        
        // 只对Binance期货持仓进行实时价格更新
        if (adapter instanceof com.quant.exchange.BinanceAdapter) {
            com.quant.exchange.BinanceAdapter binanceAdapter = (com.quant.exchange.BinanceAdapter) adapter;
            
            // 使用公共方法获取实时标记价格
            try {
                List<Position> updatedPositions = new ArrayList<>();
                for (Position pos : positions) {
                    java.math.BigDecimal realTimeMarkPrice = binanceAdapter.getRealTimeMarkPrice(pos.getSymbol());
                    if (realTimeMarkPrice != null && realTimeMarkPrice.compareTo(java.math.BigDecimal.ZERO) > 0) {
                        // 使用实时标记价格更新
                        BigDecimal newUnrealizedPnl;
                        if ("LONG".equals(pos.getSide())) {
                            // 多仓：盈亏 = (当前价格 - 开仓价格) × 数量
                            newUnrealizedPnl = realTimeMarkPrice.subtract(pos.getAvgPrice())
                                    .multiply(pos.getQuantity());
                        } else {
                            // 空仓：盈亏 = (开仓价格 - 当前价格) × 数量
                            newUnrealizedPnl = pos.getAvgPrice().subtract(realTimeMarkPrice)
                                    .multiply(pos.getQuantity());
                        }
                        
                        // 重新计算盈亏百分比（相对于保证金）
                        // 盈亏百分比 = 未实现盈亏 / 保证金 × 100
                        java.math.BigDecimal newPnlPercentage = java.math.BigDecimal.ZERO;
                        if (pos.getMargin() != null && pos.getMargin().compareTo(java.math.BigDecimal.ZERO) > 0) {
                            // 使用保证金计算盈亏百分比（更准确）
                            newPnlPercentage = newUnrealizedPnl.divide(pos.getMargin(), 8, java.math.RoundingMode.HALF_UP)
                                    .multiply(new java.math.BigDecimal("100"));
                        } else if (pos.getAvgPrice().compareTo(java.math.BigDecimal.ZERO) > 0 && 
                                pos.getQuantity().compareTo(java.math.BigDecimal.ZERO) > 0) {
                            // 如果没有保证金，使用持仓价值计算（备用方案）
                            java.math.BigDecimal positionValue = pos.getAvgPrice().multiply(pos.getQuantity());
                            if (positionValue.compareTo(java.math.BigDecimal.ZERO) > 0) {
                                newPnlPercentage = newUnrealizedPnl.divide(positionValue, 8, java.math.RoundingMode.HALF_UP)
                                        .multiply(new java.math.BigDecimal("100"));
                            }
                        }
                        
                        Position updatedPosition = Position.builder()
                                .symbol(pos.getSymbol())
                                .side(pos.getSide())
                                .quantity(pos.getQuantity())
                                .available(pos.getAvailable())
                                .avgPrice(pos.getAvgPrice())
                                .currentPrice(realTimeMarkPrice)  // 使用实时标记价格
                                .unrealizedPnl(newUnrealizedPnl)  // 使用重新计算的盈亏
                                .pnlPercentage(newPnlPercentage)   // 使用重新计算的盈亏百分比
                                .leverage(pos.getLeverage())
                                .margin(pos.getMargin())
                                .build();
                        updatedPositions.add(updatedPosition);
                        log.debug("使用实时标记价格更新持仓: symbol={}, 实时价格={}, 盈亏={}", 
                                pos.getSymbol(), realTimeMarkPrice, newUnrealizedPnl);
                    } else {
                        // 如果没有实时价格，使用原始持仓
                        updatedPositions.add(pos);
                    }
                }
                return updatedPositions;
            } catch (Exception e) {
                log.warn("无法获取实时标记价格更新持仓: {}", e.getMessage());
            }
        }
        
        // 如果不是Binance或无法获取实时价格，返回原始持仓
        return positions;
    }
    
    /**
     * 获取账户信息（从缓存或API）
     */
    public AccountInfo getAccountInfo(String userId) {
        AccountInfo cached = accountCache.get(userId);
        if (cached != null) {
            return cached;
        }
        
        // 如果缓存中没有，从交易所API获取
        ExchangeAdapter adapter = userAdapters.get(userId);
        
        // 如果适配器不存在，尝试初始化
        if (adapter == null) {
            log.info("用户 {} 的适配器未初始化，尝试初始化...", userId);
            try {
                initializeUserExchange(userId).block(); // 同步初始化
                adapter = userAdapters.get(userId);
            } catch (Exception e) {
                log.error("初始化用户交易所失败: userId={}, error={}", userId, e.getMessage());
            }
        }
        
            if (adapter != null) {
                try {
                    log.info("从API获取账户信息: userId={}", userId);
                    AccountInfo accountInfo = adapter.getAccountInfo(userId)
                            .doOnNext(info -> {
                                log.debug("账户信息已获取: userId={}, totalBalance={}", userId, info.getTotalBalance());
                            })
                            .block(); // 同步获取
                    if (accountInfo != null) {
                        // 获取持仓并合并（会自动计算总未实现盈亏）
                        log.info("账户信息获取成功，开始获取持仓: userId={}", userId);
                        AccountInfo enrichedInfo = enrichAccountInfoWithPositions(userId, adapter, accountInfo).block();
                        if (enrichedInfo != null) {
                            // 更新缓存（包含持仓和计算的总未实现盈亏）
                            accountCache.put(userId, enrichedInfo);
                            if (enrichedInfo.getPositions() != null) {
                                log.info("账户信息（含持仓）获取成功: userId={}, 持仓数量={}, 总未实现盈亏={}",
                                        userId, enrichedInfo.getPositions().size(), enrichedInfo.getUnrealizedPnl());
                            } else {
                                log.warn("账户信息获取成功，但持仓为空: userId={}", userId);
                            }
                            return enrichedInfo;
                        }
                        return accountInfo;
                    }
                } catch (Exception e) {
                    log.error("从交易所API获取账户信息失败: userId={}, error={}", userId, e.getMessage(), e);
                }
            }

            // 默认返回空账户
            log.warn("无法获取用户 {} 的账户信息，返回空账户", userId);
            return AccountInfo.builder()
                    .userId(userId)
                    .unrealizedPnl(java.math.BigDecimal.ZERO)
                    .timestamp(System.currentTimeMillis())
                    .build();
    }
    
    /**
     * 定时刷新账户信息并推送（即使账户没有变化也推送）
     * 
     * 作用：
     * 1. 定期推送账户数据，确保前端数据实时更新
     * 2. 即使账户状态没有变化，也推送缓存的数据（轻量级推送）
     * 3. 作为WebSocket推送的补充，确保数据持续更新
     * 4. 对于期货持仓，通过定期刷新可以获取实时标记价格并更新盈亏
     * 
     * 频率：每3秒刷新一次（提高频率以实时更新期货盈亏）
     */
    @Scheduled(fixedRate = 3000) // 每3秒刷新一次（提高频率以实时更新盈亏）
    public void refreshAccountInfo() {
        if (userAdapters.isEmpty()) {
            log.debug("定时刷新：没有活跃用户");
            return;
        }

//        log.info("========== 定时刷新账户信息开始: 用户数量={} ==========", userAdapters.size());
        userAdapters.forEach((userId, adapter) -> {
            try {
//                log.info("开始定时刷新用户账户: userId={}", userId);
                
                // 强制刷新持仓信息以获取实时标记价格（即使有缓存也刷新，因为价格在变化）
                // 这样可以使用WebSocket的实时标记价格更新盈亏
                adapter.getAccountInfo(userId)
                        .flatMap(accountInfo -> {
//                            log.info("账户信息获取成功，开始获取持仓（使用实时标记价格）: userId={}", userId);
                            // 获取持仓并合并（会使用实时标记价格）
                            return enrichAccountInfoWithPositions(userId, adapter, accountInfo);
                        })
                        .subscribe(
                                enrichedAccountInfo -> {
                                    // 更新缓存
                                    accountCache.put(userId, enrichedAccountInfo);
                                    // 推送包含实时盈亏的账户信息
//                                    log.info("准备推送账户信息（含实时盈亏）: userId={}, totalBalance={}, 持仓数量={}",
//                                            userId, enrichedAccountInfo.getTotalBalance(),
//                                            enrichedAccountInfo.getPositions() != null ? enrichedAccountInfo.getPositions().size() : 0);
                                    pushAccountInfo(userId, enrichedAccountInfo);
//                                    log.info("========== 定时刷新并推送完成: userId={}, totalBalance={}, 持仓数量={} ==========",
//                                            userId, enrichedAccountInfo.getTotalBalance(),
//                                            enrichedAccountInfo.getPositions() != null ? enrichedAccountInfo.getPositions().size() : 0);
                                },
                                error -> {
                                    log.error("定时刷新账户信息失败: userId={}, error={}", userId, error.getMessage(), error);
                                    // 如果刷新失败，尝试推送缓存数据
                                    AccountInfo cachedInfo = accountCache.get(userId);
                                    if (cachedInfo != null) {
                                        pushAccountInfo(userId, cachedInfo);
                                    }
                                }
                        );
            } catch (Exception e) {
                log.error("定时刷新账户信息异常: userId={}, error={}", userId, e.getMessage(), e);
            }
        });
    }
    
    /**
     * 获取实时价格
     */
    public BigDecimal getRealTimePrice(String userId, String symbol) {
        ExchangeAdapter adapter = userAdapters.get(userId);
        if (adapter == null) {
            return null;
        }

        if (adapter instanceof com.quant.exchange.BinanceAdapter) {
            com.quant.exchange.BinanceAdapter binanceAdapter = (com.quant.exchange.BinanceAdapter) adapter;
            return binanceAdapter.getRealTimeMarkPrice(symbol);
        }

        return null;
    }

    /**
     * 获取持仓列表
     */
    public List<Position> getPositions(String userId) {
        ExchangeAdapter adapter = userAdapters.get(userId);
        
        // 如果适配器不存在，尝试初始化
        if (adapter == null) {
            log.info("用户 {} 的适配器未初始化，尝试初始化...", userId);
            try {
                initializeUserExchange(userId).block(); // 同步初始化
                adapter = userAdapters.get(userId);
            } catch (Exception e) {
                log.error("初始化用户交易所失败: userId={}, error={}", userId, e.getMessage());
            }
        }
        
        if (adapter != null) {
            try {
                return adapter.getPositions(userId)
                        .block(); // 同步获取，返回 List<Position>
            } catch (Exception e) {
                log.error("从交易所API获取持仓列表失败: userId={}, error={}", userId, e.getMessage());
            }
        }
        
        return List.of();
    }
    
    /**
     * 清除缓存（强制刷新）
     */
    public void clearCache(String userId) {
        accountCache.remove(userId);
        positionsCache.remove(userId);
        positionsLastUpdate.remove(userId);
    }
    
    /**
     * 平仓（关闭持仓，支持部分平仓，支持按保证金平仓）
     * @param userId 用户ID
     * @param symbol 交易对
     * @param side 持仓方向 (LONG/SHORT)
     * @param quantity 平仓数量，如果为null则平全部
     * @param margin 平仓保证金（USDT），如果提供则根据保证金计算数量
     */
    public boolean closePosition(String userId, String symbol, String side, 
                                  java.math.BigDecimal quantity, java.math.BigDecimal margin) {
        ExchangeAdapter adapter = userAdapters.get(userId);
        
        if (adapter == null) {
            log.warn("用户 {} 的适配器未初始化，无法平仓", userId);
            return false;
        }
        
        try {
            // 获取当前持仓信息
            List<Position> positions = adapter.getPositions(userId).block();
            Position targetPosition = null;
            
            for (Position pos : positions) {
                if (pos.getSymbol().equals(symbol) && pos.getSide().equals(side)) {
                    targetPosition = pos;
                    break;
                }
            }
            
            if (targetPosition == null) {
                log.warn("未找到持仓: userId={}, symbol={}, side={}", userId, symbol, side);
                return false;
            }
            
            // 确定平仓数量
            java.math.BigDecimal closeQuantity;
            
            // 优先检查保证金，如果提供了保证金，根据保证金计算数量
            if (margin != null && margin.compareTo(java.math.BigDecimal.ZERO) > 0) {
                // 按保证金平仓
                java.math.BigDecimal currentPrice = targetPosition.getCurrentPrice();
                Integer leverage = targetPosition.getLeverage() != null ? targetPosition.getLeverage() : 1;
                
                if (currentPrice == null || currentPrice.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    log.error("无法获取当前价格，无法根据保证金计算平仓数量: userId={}, symbol={}", userId, symbol);
                    return false;
                }
                
                // 数量 = 保证金 * 杠杆 / 当前价格
                closeQuantity = margin.multiply(new java.math.BigDecimal(leverage))
                        .divide(currentPrice, 8, java.math.RoundingMode.HALF_UP);
                
                // 确保平仓数量不超过持仓数量
                if (closeQuantity.compareTo(targetPosition.getQuantity()) > 0) {
                    log.warn("按保证金计算的平仓数量 {} 大于持仓数量 {}，使用持仓数量", closeQuantity, targetPosition.getQuantity());
                    closeQuantity = targetPosition.getQuantity();
                }
                
                log.info("按保证金平仓: userId={}, symbol={}, side={}, 保证金={} USDT, 杠杆={}, 当前价格={}, 计算数量={}", 
                        userId, symbol, side, margin, leverage, currentPrice, closeQuantity);
            } else if (quantity != null && quantity.compareTo(java.math.BigDecimal.ZERO) > 0) {
                // 按数量部分平仓
                if (quantity.compareTo(targetPosition.getQuantity()) > 0) {
                    log.warn("平仓数量 {} 大于持仓数量 {}，使用持仓数量", quantity, targetPosition.getQuantity());
                    closeQuantity = targetPosition.getQuantity();
                } else {
                    closeQuantity = quantity;
                }
                log.info("部分平仓: userId={}, symbol={}, side={}, 持仓数量={}, 平仓数量={}", 
                        userId, symbol, side, targetPosition.getQuantity(), closeQuantity);
            } else {
                // 全部平仓（既没有提供数量，也没有提供保证金）
                closeQuantity = targetPosition.getQuantity();
                log.info("全部平仓: userId={}, symbol={}, side={}, 数量={}", 
                        userId, symbol, side, closeQuantity);
            }
            
            // 如果计算出的平仓数量为0，则不执行平仓
            if (closeQuantity.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                log.warn("平仓数量为0，不执行平仓操作: userId={}, symbol={}, side={}", userId, symbol, side);
                return false;
            }
            
            // 创建平仓订单（反向订单）
            // 注意：order.side 应该设置为持仓方向（LONG或SHORT），placeOrder方法会自动转换为BUY/SELL
            com.quant.model.Order closeOrder = com.quant.model.Order.builder()
                    .symbol(symbol)
                    .side(side)  // 使用持仓方向（LONG或SHORT），placeOrder会自动转换
                    .quantity(closeQuantity)
                    .type("MARKET")  // 市价单
                    .build();
            
            // 执行平仓（阻塞等待结果）
            com.quant.model.Order result = adapter.placeOrder(closeOrder)
                    .doOnNext(order -> {
                        log.info("平仓订单已提交: orderId={}, status={}", order.getOrderId(), order.getStatus());
                    })
                    .doOnError(error -> {
                        log.error("平仓订单提交失败: error={}", error.getMessage(), error);
                    })
                    .block(); // 阻塞等待订单提交完成
            
            if (result != null && result.getOrderId() != null) {
                log.info("平仓成功: userId={}, symbol={}, side={}, quantity={}, orderId={}", 
                        userId, symbol, side, closeQuantity, result.getOrderId());
                
                // 平仓成功后清除缓存，强制刷新数据
                clearCache(userId);
                
                // 延迟1秒后推送一次账户信息，确保数据已更新
                scheduler.schedule(() -> {
                    try {
                        AccountInfo accountInfo = getAccountInfo(userId);
                        if (accountInfo != null) {
                            pushAccountInfo(userId, accountInfo);
                            log.info("平仓后推送账户信息: userId={}", userId);
                        }
                    } catch (Exception e) {
                        log.error("平仓后推送账户信息失败: userId={}, error={}", userId, e.getMessage());
                    }
                }, 1, TimeUnit.SECONDS);
                
                return true;
            } else {
                log.warn("平仓订单提交失败: 未返回订单ID");
                return false;
            }
        } catch (Exception e) {
            log.error("平仓失败: userId={}, symbol={}, side={}, error={}", 
                    userId, symbol, side, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 平仓（响应式版本，用于响应式链中调用）
     * @param userId 用户ID
     * @param symbol 交易对
     * @param side 持仓方向 (LONG/SHORT)
     * @param quantity 平仓数量，如果为null则平全部
     * @param margin 平仓保证金（USDT），如果提供则根据保证金计算数量
     */
    public Mono<Boolean> closePositionReactive(String userId, String symbol, String side, 
                                               java.math.BigDecimal quantity, java.math.BigDecimal margin) {
        ExchangeAdapter adapter = userAdapters.get(userId);
        
        if (adapter == null) {
            log.warn("用户 {} 的适配器未初始化，无法平仓", userId);
            return Mono.just(false);
        }
        
        // 获取当前持仓信息（响应式）
        return adapter.getPositions(userId)
                .flatMap(positions -> {
                    Position targetPosition = null;
                    for (Position pos : positions) {
                        if (pos.getSymbol().equals(symbol) && pos.getSide().equals(side)) {
                            targetPosition = pos;
                            break;
                        }
                    }
                    
                    if (targetPosition == null) {
                        log.warn("未找到持仓: userId={}, symbol={}, side={}", userId, symbol, side);
                        return Mono.just(false);
                    }
                    
                    // 确定平仓数量
                    Mono<java.math.BigDecimal> closeQuantityMono;
                    
                    // 优先检查保证金，如果提供了保证金，根据保证金计算数量
                    if (margin != null && margin.compareTo(java.math.BigDecimal.ZERO) > 0) {
                        // 按保证金平仓
                        java.math.BigDecimal currentPrice = targetPosition.getCurrentPrice();
                        Integer leverage = targetPosition.getLeverage() != null ? targetPosition.getLeverage() : 1;
                        
                        if (currentPrice == null || currentPrice.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                            log.error("无法获取当前价格，无法根据保证金计算平仓数量: userId={}, symbol={}", userId, symbol);
                            return Mono.just(false);
                        }
                        
                        // 计算名义价值 = 保证金 × 杠杆
                        java.math.BigDecimal notionalValue = margin.multiply(new java.math.BigDecimal(leverage));
                        
                        // 计算数量 = 名义价值 / 当前价格
                        java.math.BigDecimal calculatedQuantity = notionalValue
                                .divide(currentPrice, 8, java.math.RoundingMode.HALF_UP);
                        
                        // 确保平仓数量不超过持仓数量
                        java.math.BigDecimal closeQuantity = calculatedQuantity.compareTo(targetPosition.getQuantity()) > 0
                                ? targetPosition.getQuantity()
                                : calculatedQuantity;
                        
                        log.info("按保证金平仓: userId={}, symbol={}, side={}, 保证金={} USDT, 杠杆={}倍, 名义价值={} USDT, 当前价格={}, 计算数量={}", 
                                userId, symbol, side, margin, leverage, notionalValue, currentPrice, closeQuantity);
                        
                        closeQuantityMono = Mono.just(closeQuantity);
                    } else if (quantity != null && quantity.compareTo(java.math.BigDecimal.ZERO) > 0) {
                        // 按数量部分平仓
                        java.math.BigDecimal closeQuantity = quantity.compareTo(targetPosition.getQuantity()) > 0
                                ? targetPosition.getQuantity()
                                : quantity;
                        log.info("部分平仓: userId={}, symbol={}, side={}, 持仓数量={}, 平仓数量={}", 
                                userId, symbol, side, targetPosition.getQuantity(), closeQuantity);
                        closeQuantityMono = Mono.just(closeQuantity);
                    } else {
                        // 全部平仓（既没有提供数量，也没有提供保证金）
                        java.math.BigDecimal closeQuantity = targetPosition.getQuantity();
                        log.info("全部平仓: userId={}, symbol={}, side={}, 数量={}", 
                                userId, symbol, side, closeQuantity);
                        closeQuantityMono = Mono.just(closeQuantity);
                    }
                    
                    return closeQuantityMono
                            .flatMap(closeQuantity -> {
                                // 如果计算出的平仓数量为0，则不执行平仓
                                if (closeQuantity.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                                    log.warn("平仓数量为0，不执行平仓操作: userId={}, symbol={}, side={}", userId, symbol, side);
                                    return Mono.just(false);
                                }
                                
                                // 创建平仓订单（反向订单）
                                com.quant.model.Order closeOrder = com.quant.model.Order.builder()
                                        .symbol(symbol)
                                        .side(side)  // 使用持仓方向（LONG或SHORT），placeOrder会自动转换
                                        .quantity(closeQuantity)
                                        .type("MARKET")  // 市价单
                                        .build();
                                
                                // 执行平仓（响应式）
                                return adapter.placeOrder(closeOrder)
                                        .doOnNext(order -> {
                                            log.info("平仓订单已提交: orderId={}, status={}", order.getOrderId(), order.getStatus());
                                        })
                                        .doOnError(error -> {
                                            log.error("平仓订单提交失败: error={}", error.getMessage(), error);
                                        })
                                        .map(order -> {
                                            if (order != null && order.getOrderId() != null) {
                                                log.info("平仓成功: userId={}, symbol={}, side={}, quantity={}, orderId={}", 
                                                        userId, symbol, side, closeQuantity, order.getOrderId());
                                                
                                                // 平仓成功后清除缓存，强制刷新数据
                                                clearCache(userId);
                                                
                                                // 延迟1秒后推送一次账户信息，确保数据已更新
                                                scheduler.schedule(() -> {
                                                    try {
                                                        AccountInfo accountInfo = getAccountInfo(userId);
                                                        if (accountInfo != null) {
                                                            pushAccountInfo(userId, accountInfo);
                                                            log.info("平仓后推送账户信息: userId={}", userId);
                                                        }
                                                    } catch (Exception e) {
                                                        log.error("平仓后推送账户信息失败: userId={}, error={}", userId, e.getMessage());
                                                    }
                                                }, 1, TimeUnit.SECONDS);
                                                
                                                return true;
                                            } else {
                                                log.warn("平仓订单提交失败: 未返回订单ID");
                                                return false;
                                            }
                                        })
                                        .onErrorResume(error -> {
                                            log.error("平仓失败: userId={}, symbol={}, side={}, error={}", 
                                                    userId, symbol, side, error.getMessage(), error);
                                            return Mono.just(false);
                                        });
                            });
                })
                .onErrorResume(error -> {
                    log.error("获取持仓信息失败: userId={}, symbol={}, side={}, error={}", 
                            userId, symbol, side, error.getMessage(), error);
                    return Mono.just(false);
                });
    }
    
    /**
     * 开仓（加仓，支持按保证金加仓）
     * @param userId 用户ID
     * @param symbol 交易对
     * @param side 持仓方向 (LONG/SHORT)
     * @param quantity 开仓数量
     * @param margin 开仓保证金（USDT），如果提供则根据保证金计算数量
     */
    public boolean openPosition(String userId, String symbol, String side, 
                                java.math.BigDecimal quantity, java.math.BigDecimal margin) {
        ExchangeAdapter adapter = userAdapters.get(userId);
        
        if (adapter == null) {
            log.warn("用户 {} 的适配器未初始化，无法开仓", userId);
            return false;
        }
        
        // 确定开仓数量
        java.math.BigDecimal openQuantity;
        
        // 如果提供了保证金，优先使用保证金计算数量
        if (margin != null && margin.compareTo(java.math.BigDecimal.ZERO) > 0) {
            // 需要获取当前价格和杠杆来计算数量
            // 先尝试从现有持仓获取杠杆，如果没有持仓则从账户信息获取
            try {
                List<Position> positions = adapter.getPositions(userId).block();
                Position existingPosition = null;
                for (Position pos : positions) {
                    if (pos.getSymbol().equals(symbol) && pos.getSide().equals(side)) {
                        existingPosition = pos;
                        break;
                    }
                }
                
                java.math.BigDecimal currentPrice = null;
                Integer leverage = null;
                
                if (existingPosition != null) {
                    // 使用现有持仓的价格和杠杆
                    currentPrice = existingPosition.getCurrentPrice();
                    leverage = existingPosition.getLeverage() != null ? existingPosition.getLeverage() : 1;
                } else {
                    // 如果没有现有持仓，从账户信息中获取价格
                    AccountInfo accountInfo = getAccountInfo(userId);
                    if (accountInfo != null && accountInfo.getPositions() != null) {
                        for (Position pos : accountInfo.getPositions()) {
                            if (pos.getSymbol().equals(symbol)) {
                                currentPrice = pos.getCurrentPrice();
                                leverage = pos.getLeverage() != null ? pos.getLeverage() : 1;
                                break;
                            }
                        }
                    }
                    
                    // 如果还是获取不到价格，尝试从Binance API获取（仅限Binance）
                    if ((currentPrice == null || currentPrice.compareTo(java.math.BigDecimal.ZERO) <= 0) 
                            && adapter instanceof com.quant.exchange.BinanceAdapter) {
                        try {
                            com.quant.exchange.BinanceAdapter binanceAdapter = (com.quant.exchange.BinanceAdapter) adapter;
                            // 通过反射或直接调用获取价格（这里简化处理，使用默认杠杆）
                            if (leverage == null) {
                                leverage = 1; // 默认杠杆，实际应该从账户配置获取
                            }
                            // 注意：这里需要从市场数据获取价格，暂时使用持仓中的价格
                            // 如果还是没有，返回错误
                        } catch (Exception e) {
                            log.warn("无法从Binance API获取价格: {}", e.getMessage());
                        }
                    }
                    
                    // 如果仍然获取不到，使用默认杠杆
                    if (leverage == null) {
                        leverage = 1;
                    }
                }
                
                if (currentPrice == null || currentPrice.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    log.error("无法获取当前价格，无法根据保证金计算数量: userId={}, symbol={}", userId, symbol);
                    log.error("请确保有持仓或提供数量参数");
                    return false;
                }
                
                // 根据保证金计算开仓数量：数量 = 保证金 * 杠杆 / 当前价格
                openQuantity = margin.multiply(new java.math.BigDecimal(leverage))
                        .divide(currentPrice, 8, java.math.RoundingMode.HALF_UP);
                
                log.info("按保证金开仓: userId={}, symbol={}, side={}, 保证金={} USDT, 杠杆={}, 当前价格={}, 计算数量={}", 
                        userId, symbol, side, margin, leverage, currentPrice, openQuantity);
            } catch (Exception e) {
                log.error("根据保证金计算开仓数量失败: userId={}, symbol={}, side={}, margin={}, error={}", 
                        userId, symbol, side, margin, e.getMessage(), e);
                return false;
            }
        } else if (quantity != null && quantity.compareTo(java.math.BigDecimal.ZERO) > 0) {
            // 按数量开仓
            openQuantity = quantity;
            log.info("按数量开仓: userId={}, symbol={}, side={}, quantity={}", 
                    userId, symbol, side, quantity);
        } else {
            log.warn("开仓数量或保证金无效: userId={}, symbol={}, side={}, quantity={}, margin={}", 
                    userId, symbol, side, quantity, margin);
            return false;
        }
        
        try {
            // 创建开仓订单
            // 加仓时：LONG持仓需要BUY订单（增加多仓），SHORT持仓需要SELL订单（增加空仓）
            // 注意：这里直接使用 BUY/SELL，而不是 LONG/SHORT，因为 placeOrder 会将 LONG/SHORT 当作平仓处理
            String orderSide = "LONG".equals(side) ? "BUY" : "SELL";
            
            com.quant.model.Order openOrder = com.quant.model.Order.builder()
                    .symbol(symbol)
                    .side(orderSide)  // 直接使用 BUY/SELL，表示开仓（加仓）
                    .quantity(openQuantity)
                    .type("MARKET")  // 市价单
                    .build();
            
            log.info("创建开仓订单: userId={}, symbol={}, side={}, quantity={}, orderSide={}", 
                    userId, symbol, side, openQuantity, orderSide);
            
            // 执行开仓（阻塞等待结果）
            com.quant.model.Order result = adapter.placeOrder(openOrder)
                    .doOnNext(order -> {
                        log.info("开仓订单已提交: orderId={}, status={}", order.getOrderId(), order.getStatus());
                    })
                    .doOnError(error -> {
                        log.error("开仓订单提交失败: error={}", error.getMessage(), error);
                    })
                    .block(); // 阻塞等待订单提交完成
            
            if (result != null && result.getOrderId() != null) {
                log.info("开仓成功: userId={}, symbol={}, side={}, quantity={}, orderId={}", 
                        userId, symbol, side, openQuantity, result.getOrderId());
                
                // 开仓成功后清除缓存，强制刷新数据
                clearCache(userId);
                
                // 延迟1秒后推送一次账户信息，确保数据已更新
                scheduler.schedule(() -> {
                    try {
                        AccountInfo accountInfo = getAccountInfo(userId);
                        if (accountInfo != null) {
                            pushAccountInfo(userId, accountInfo);
                            log.info("开仓后推送账户信息: userId={}", userId);
                        }
                    } catch (Exception e) {
                        log.error("开仓后推送账户信息失败: userId={}, error={}", userId, e.getMessage());
                    }
                }, 1, TimeUnit.SECONDS);
                
                return true;
            } else {
                log.warn("开仓订单提交失败: 未返回订单ID");
                return false;
            }
        } catch (Exception e) {
            log.error("开仓失败: userId={}, symbol={}, side={}, quantity={}, error={}", 
                    userId, symbol, side, quantity, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 开仓（响应式版本，用于响应式链中调用）
     * @param userId 用户ID
     * @param symbol 交易对
     * @param side 持仓方向 (LONG/SHORT)
     * @param quantity 开仓数量（如果提供则直接使用）
     * @param margin 保证金（实际价值，USDT）
     * @param strategyType 策略类型（可选，用于确定默认杠杆）
     */
    public Mono<Boolean> openPositionReactive(String userId, String symbol, String side, 
                                             java.math.BigDecimal quantity, java.math.BigDecimal margin) {
        return openPositionReactive(userId, symbol, side, quantity, margin, null);
    }
    
    /**
     * 开仓（响应式版本，用于响应式链中调用）
     * @param userId 用户ID
     * @param symbol 交易对
     * @param side 持仓方向 (LONG/SHORT)
     * @param quantity 开仓数量（如果提供则直接使用）
     * @param margin 保证金（实际价值，USDT）
     * @param strategyType 策略类型（用于确定默认杠杆，如DUAL_DIRECTION默认50倍）
     */
    public Mono<Boolean> openPositionReactive(String userId, String symbol, String side, 
                                             java.math.BigDecimal quantity, java.math.BigDecimal margin, String strategyType) {
        ExchangeAdapter adapter = userAdapters.get(userId);
        
        if (adapter == null) {
            log.warn("用户 {} 的适配器未初始化，无法开仓", userId);
            return Mono.just(false);
        }
        
        // 确定开仓数量
        Mono<java.math.BigDecimal> openQuantityMono;
        
        // 如果提供了保证金，优先使用保证金计算数量
        if (margin != null && margin.compareTo(java.math.BigDecimal.ZERO) > 0) {
            // 需要获取当前价格和杠杆来计算数量
            // 计算公式：名义价值 = 保证金 × 杠杆，数量 = 名义价值 / 当前价格
            openQuantityMono = adapter.getPositions(userId)
                    .map(positions -> {
                        Position existingPosition = null;
                        for (Position pos : positions) {
                            if (pos.getSymbol().equals(symbol) && pos.getSide().equals(side)) {
                                existingPosition = pos;
                                break;
                            }
                        }
                        
                        java.math.BigDecimal currentPrice = null;
                        Integer leverage = null;
                        
                        if (existingPosition != null) {
                            // 使用现有持仓的价格和杠杆
                            currentPrice = existingPosition.getCurrentPrice();
                            leverage = existingPosition.getLeverage() != null ? existingPosition.getLeverage() : null;
                        } else {
                            // 尝试从其他持仓获取价格和杠杆
                            for (Position pos : positions) {
                                if (pos.getSymbol().equals(symbol)) {
                                    currentPrice = pos.getCurrentPrice();
                                    leverage = pos.getLeverage() != null ? pos.getLeverage() : null;
                                    break;
                                }
                            }
                        }
                        
                        // 如果还是获取不到价格，尝试从Binance API获取
                        if ((currentPrice == null || currentPrice.compareTo(java.math.BigDecimal.ZERO) <= 0) 
                                && adapter instanceof com.quant.exchange.BinanceAdapter) {
                            try {
                                com.quant.exchange.BinanceAdapter binanceAdapter = (com.quant.exchange.BinanceAdapter) adapter;
                                currentPrice = binanceAdapter.getRealTimeMarkPrice(symbol);
                            } catch (Exception e) {
                                log.warn("无法从Binance API获取价格: {}", e.getMessage());
                            }
                        }
                        
                        // 如果获取不到杠杆，根据策略类型使用默认值
                        if (leverage == null) {
                            if ("DUAL_DIRECTION".equals(strategyType)) {
                                // 双向策略默认杠杆50倍
                                leverage = 50;
                                log.info("双向策略未获取到杠杆，使用默认杠杆: {}倍", leverage);
                            } else {
                                // 其他策略默认1倍
                                leverage = 1;
                                log.info("未获取到杠杆，使用默认杠杆: {}倍", leverage);
                            }
                        }
                        
                        if (currentPrice == null || currentPrice.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                            throw new RuntimeException("无法获取当前价格，无法根据保证金计算数量");
                        }
                        
                        // 计算名义价值 = 保证金 × 杠杆
                        java.math.BigDecimal notionalValue = margin.multiply(new java.math.BigDecimal(leverage));
                        
                        // 计算数量 = 名义价值 / 当前价格
                        java.math.BigDecimal calculatedQuantity = notionalValue
                                .divide(currentPrice, 8, java.math.RoundingMode.HALF_UP);
                        
                        log.info("按保证金开仓: userId={}, symbol={}, side={}, 保证金={} USDT, 杠杆={}倍, 名义价值={} USDT, 当前价格={}, 计算数量={}", 
                                userId, symbol, side, margin, leverage, notionalValue, currentPrice, calculatedQuantity);
                        
                        return calculatedQuantity;
                    })
                    .onErrorResume(error -> {
                        log.error("根据保证金计算开仓数量失败: userId={}, symbol={}, side={}, margin={}, error={}", 
                                userId, symbol, side, margin, error.getMessage(), error);
                        return Mono.error(error);
                    });
        } else if (quantity != null && quantity.compareTo(java.math.BigDecimal.ZERO) > 0) {
            log.info("按数量开仓: userId={}, symbol={}, side={}, quantity={}", 
                    userId, symbol, side, quantity);
            openQuantityMono = Mono.just(quantity);
        } else {
            log.warn("开仓数量或保证金无效: userId={}, symbol={}, side={}, quantity={}, margin={}", 
                    userId, symbol, side, quantity, margin);
            return Mono.just(false);
        }
        
        // 执行开仓
        return openQuantityMono
                .flatMap(openQuantity -> {
                    String orderSide = "LONG".equals(side) ? "BUY" : "SELL";
                    
                    com.quant.model.Order openOrder = com.quant.model.Order.builder()
                            .symbol(symbol)
                            .side(orderSide)
                            .quantity(openQuantity)
                            .type("MARKET")
                            .build();
                    
                    log.info("创建开仓订单: userId={}, symbol={}, side={}, quantity={}, orderSide={}", 
                            userId, symbol, side, openQuantity, orderSide);
                    
                    return adapter.placeOrder(openOrder)
                            .doOnNext(order -> {
                                log.info("开仓订单已提交: orderId={}, status={}", order.getOrderId(), order.getStatus());
                            })
                            .doOnError(error -> {
                                log.error("开仓订单提交失败: error={}", error.getMessage(), error);
                            })
                            .map(order -> {
                                if (order != null && order.getOrderId() != null) {
                                    log.info("开仓成功: userId={}, symbol={}, side={}, quantity={}, orderId={}", 
                                            userId, symbol, side, openQuantity, order.getOrderId());
                                    
                                    clearCache(userId);
                                    
                                    scheduler.schedule(() -> {
                                        try {
                                            AccountInfo accountInfo = getAccountInfo(userId);
                                            if (accountInfo != null) {
                                                pushAccountInfo(userId, accountInfo);
                                                log.info("开仓后推送账户信息: userId={}", userId);
                                            }
                                        } catch (Exception e) {
                                            log.error("开仓后推送账户信息失败: userId={}, error={}", userId, e.getMessage());
                                        }
                                    }, 1, TimeUnit.SECONDS);
                                    
                                    return true;
                                } else {
                                    log.warn("开仓订单提交失败: 未返回订单ID");
                                    return false;
                                }
                            })
                            .onErrorReturn(false);
                })
                .onErrorReturn(false);
    }
    
    /**
     * 推送账户信息给指定用户
     */
    private void pushAccountInfo(String userId, AccountInfo accountInfo) {
        try {
            // 构建完整的目标路径
            String destination = "/user/" + userId + "/account";
//            log.info("准备推送账户信息: destination={}, userId={}, totalBalance={}, 持仓数量={}",
//                    destination, userId, accountInfo.getTotalBalance(),
//                    accountInfo.getPositions() != null ? accountInfo.getPositions().size() : 0);
            
            // 方法1：使用convertAndSendToUser推送（Spring会自动添加/user前缀）
            // 注意：第一个参数应该是Principal的name，Spring会将其转换为/user/{username}/destination
            messagingTemplate.convertAndSendToUser(
                userId,  // 这应该是Principal.getName()返回的值
                "/account",
                accountInfo
            );
            
            // 方法2：直接发送到完整路径（确保消息能够到达）
            // 注意：这需要消息代理支持/user前缀（已在WebSocketConfig中配置）
            messagingTemplate.convertAndSend(destination, accountInfo);
            
//            log.info("已尝试两种推送方式: convertAndSendToUser + convertAndSend");
//
//            log.info("推送账户信息到WebSocket完成: userId={}, destination={}, totalBalance={}, 持仓数量={}",
//                    userId, destination, accountInfo.getTotalBalance(),
//                    accountInfo.getPositions() != null ? accountInfo.getPositions().size() : 0);
        } catch (Exception e) {
            log.error("推送账户信息失败: userId={}, error={}", userId, e.getMessage(), e);
        }
    }
}

