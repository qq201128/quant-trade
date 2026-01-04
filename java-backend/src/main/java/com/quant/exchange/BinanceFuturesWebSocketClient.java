package com.quant.exchange;

import com.quant.model.AccountInfo;
import com.quant.model.Position;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.framing.Framedata;
import org.java_websocket.framing.PingFrame;
import org.java_websocket.framing.PongFrame;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Binance Futures WebSocket客户端
 * 用于订阅期货标记价格流，实时更新持仓盈亏
 * 
 * 参考文档: https://developers.binance.com/docs/derivatives/usds-margined-futures/market-data/websocket-api
 */
@Slf4j
public class BinanceFuturesWebSocketClient {
    
    // Binance Futures WebSocket端点
    private static final String FUTURES_STREAM_ENDPOINT = "wss://fstream.binance.com/ws/";
    private static final String FUTURES_COMBINED_STREAM_ENDPOINT = "wss://fstream.binance.com/stream?streams=";
    
    private WebSocketClient markPriceClient;
    private WebSocketClient userDataClient;
    private final Sinks.Many<Map<String, BigDecimal>> markPriceSink = Sinks.many().multicast().onBackpressureBuffer();
    private final Sinks.Many<AccountInfo> accountSink = Sinks.many().multicast().onBackpressureBuffer();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Proxy proxy;
    private final BinanceFuturesApiClient futuresApiClient;
    private String listenKey;
    private String currentUserId;
    
    // 存储每个交易对的标记价格 (symbol -> markPrice)
    private final Map<String, BigDecimal> markPrices = new ConcurrentHashMap<>();
    
    public BinanceFuturesWebSocketClient(Proxy proxy) {
        this.proxy = proxy;
        this.futuresApiClient = null;
    }
    
    public BinanceFuturesWebSocketClient(Proxy proxy, BinanceFuturesApiClient futuresApiClient) {
        this.proxy = proxy;
        this.futuresApiClient = futuresApiClient;
    }
    
    /**
     * 订阅所有交易对的标记价格流
     * 使用组合流订阅所有标记价格更新
     * 参考: https://developers.binance.com/docs/derivatives/usds-margined-futures/market-data/websocket-api/Mark-Price-Stream
     */
    public Flux<Map<String, BigDecimal>> subscribeAllMarkPrices() {
        try {
            // 使用组合流订阅所有标记价格
            // 格式: !markPrice@arr@1s (每秒更新一次，所有交易对的标记价格数组)
            URI uri = new URI(FUTURES_STREAM_ENDPOINT + "!markPrice@arr@1s");
            
            markPriceClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("Binance Futures标记价格流连接已建立（代理状态: {}）",
                            (proxy != null && proxy != Proxy.NO_PROXY ? "已使用" : "未使用"));
                }
                
                @Override
                public void onMessage(String message) {
                    try {
                        // 解析标记价格数组
                        ObjectMapper mapper = new ObjectMapper();
                        java.util.List<Map<String, Object>> priceList = mapper.readValue(message, 
                                mapper.getTypeFactory().constructCollectionType(java.util.List.class, Map.class));
                        
                        // 更新每个交易对的标记价格
                        Map<String, BigDecimal> updatedPrices = new HashMap<>();
                        for (Map<String, Object> priceData : priceList) {
                            String symbol = (String) priceData.get("s");
                            Object markPriceObj = priceData.get("p");
                            
                            if (symbol != null && markPriceObj != null) {
                                try {
                                    BigDecimal markPrice = new BigDecimal(markPriceObj.toString());
                                    markPrices.put(symbol, markPrice);
                                    updatedPrices.put(symbol, markPrice);
                                } catch (Exception e) {
                                    log.warn("解析标记价格失败: symbol={}, price={}, error={}", 
                                            symbol, markPriceObj, e.getMessage());
                                }
                            }
                        }
                        
                        // 如果有更新，发送到流
                        if (!updatedPrices.isEmpty()) {
                            markPriceSink.tryEmitNext(updatedPrices);
//                            log.info("更新标记价格: {} 个交易对", updatedPrices.size());
                            // 记录前几个交易对的价格，便于调试
                            updatedPrices.entrySet().stream().limit(5).forEach(entry -> 
                                log.debug("标记价格更新: {} = {}", entry.getKey(), entry.getValue())
                            );
                        }
                    } catch (Exception e) {
                        log.error("解析标记价格消息失败: {}", e.getMessage(), e);
                    }
                }
                
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.warn("Binance Futures标记价格流连接关闭: code={}, reason={}", code, reason);
                    // 尝试重连
                    if (remote) {
                        scheduler.schedule(() -> {
                            log.info("尝试重连Binance Futures标记价格流...");
                            subscribeAllMarkPrices().subscribe();
                        }, 5, TimeUnit.SECONDS);
                    }
                }
                
                @Override
                public void onError(Exception ex) {
                    log.error("Binance Futures标记价格流错误: {}", ex.getMessage(), ex);
                    markPriceSink.tryEmitError(ex);
                }
                
                @Override
                public void onWebsocketPing(org.java_websocket.WebSocket conn, Framedata f) {
                    try {
                        if (f instanceof PingFrame) {
                            PongFrame pongFrame = new PongFrame((PingFrame) f);
                            conn.sendFrame(pongFrame);
                        } else {
                            PongFrame pongFrame = new PongFrame();
                            conn.sendFrame(pongFrame);
                        }
                    } catch (Exception e) {
                        log.error("回复pong帧失败: {}", e.getMessage());
                    }
                }
                
                @Override
                public void onWebsocketPong(org.java_websocket.WebSocket conn, Framedata f) {
                    // 收到pong响应
                }
            };
            
            // 设置代理
            if (proxy != null && proxy != Proxy.NO_PROXY) {
                try {
                    markPriceClient.setProxy(proxy);
                    InetSocketAddress proxyAddr = (InetSocketAddress) proxy.address();
                    log.info("Binance Futures WebSocket已设置代理: {}:{}", 
                            proxyAddr.getHostName(), proxyAddr.getPort());
                } catch (Exception e) {
                    log.error("设置WebSocket代理失败: {}", e.getMessage(), e);
                }
            } else {
                log.info("Binance Futures WebSocket未使用代理（直接连接）");
            }
            
            // 连接
            log.info("正在连接Binance Futures标记价格流: {}", uri);
            markPriceClient.connect();
            
        } catch (Exception e) {
            log.error("连接Binance Futures标记价格流失败: {}", e.getMessage(), e);
            markPriceSink.tryEmitError(e);
        }
        
        return markPriceSink.asFlux();
    }
    
    /**
     * 获取指定交易对的标记价格
     */
    public BigDecimal getMarkPrice(String symbol) {
        return markPrices.get(symbol);
    }
    
    /**
     * 获取所有标记价格
     */
    public Map<String, BigDecimal> getAllMarkPrices() {
        return new HashMap<>(markPrices);
    }
    
    /**
     * 订阅用户数据流（账户和持仓更新）
     * 参考: https://developers.binance.com/docs/derivatives/usds-margined-futures/user-data-streams
     * 
     * @param userId 用户ID
     * @param listenKey 通过BinanceFuturesApiClient.createListenKey()获取的listenKey
     */
    public Flux<AccountInfo> subscribeUserDataStream(String userId, String listenKey) {
        this.currentUserId = userId;
        this.listenKey = listenKey;
        
        try {
            URI uri = new URI(FUTURES_STREAM_ENDPOINT + listenKey);
            
            userDataClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("Binance Futures用户数据流连接已建立（代理状态: {}）",
                            (proxy != null && proxy != Proxy.NO_PROXY ? "已使用" : "未使用"));
                }
                
                @Override
                public void onMessage(String message) {
                    try {
                        log.debug("收到Binance Futures用户数据流消息: {}", message.substring(0, Math.min(200, message.length())));
                        
                        Map<String, Object> data = objectMapper.readValue(message, Map.class);
                        String eventType = (String) data.get("e");
                        
                        if ("ACCOUNT_UPDATE".equals(eventType)) {
                            log.debug("收到Binance Futures账户更新事件");
                            AccountInfo accountInfo = parseAccountUpdate(data, userId);
                            if (accountInfo != null) {
                                accountSink.tryEmitNext(accountInfo);
                            }
                        } else if ("ORDER_TRADE_UPDATE".equals(eventType)) {
                            log.debug("收到Binance Futures订单/成交更新事件");
                            // 订单更新可能影响账户余额，也发送账户更新
                            // 可以通过REST API获取最新账户信息，或等待ACCOUNT_UPDATE事件
                        }
                    } catch (Exception e) {
                        log.error("解析Binance Futures用户数据流消息失败: {}", e.getMessage(), e);
                    }
                }
                
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.warn("Binance Futures用户数据流连接关闭: code={}, reason={}", code, reason);
                    if (remote && futuresApiClient != null && listenKey != null) {
                        // 尝试重连
                        scheduler.schedule(() -> {
                            log.info("尝试重连Binance Futures用户数据流...");
                            reconnectUserDataStream(userId);
                        }, 5, TimeUnit.SECONDS);
                    }
                }
                
                @Override
                public void onError(Exception ex) {
                    log.error("Binance Futures用户数据流错误: {}", ex.getMessage(), ex);
                    accountSink.tryEmitError(ex);
                }
                
                @Override
                public void onWebsocketPing(org.java_websocket.WebSocket conn, Framedata f) {
                    try {
                        if (f instanceof PingFrame) {
                            PongFrame pongFrame = new PongFrame((PingFrame) f);
                            conn.sendFrame(pongFrame);
                        } else {
                            PongFrame pongFrame = new PongFrame();
                            conn.sendFrame(pongFrame);
                        }
                    } catch (Exception e) {
                        log.error("回复pong帧失败: {}", e.getMessage());
                    }
                }
                
                @Override
                public void onWebsocketPong(org.java_websocket.WebSocket conn, Framedata f) {
                    // 收到pong响应
                }
            };
            
            // 设置代理
            if (proxy != null && proxy != Proxy.NO_PROXY) {
                try {
                    userDataClient.setProxy(proxy);
                    InetSocketAddress proxyAddr = (InetSocketAddress) proxy.address();
                    log.info("Binance Futures用户数据流WebSocket已设置代理: {}:{}", 
                            proxyAddr.getHostName(), proxyAddr.getPort());
                } catch (Exception e) {
                    log.error("设置WebSocket代理失败: {}", e.getMessage(), e);
                }
            }
            
            // 连接
            log.info("正在连接Binance Futures用户数据流: {}", uri);
            userDataClient.connect();
            
            // 定期延长listenKey有效期（每30分钟）
            if (futuresApiClient != null && listenKey != null) {
                scheduler.scheduleAtFixedRate(() -> {
                    futuresApiClient.keepAliveListenKey(listenKey)
                            .subscribe(
                                    v -> log.debug("延长Binance Futures listenKey成功"),
                                    error -> log.error("延长Binance Futures listenKey失败: {}", error.getMessage())
                            );
                }, 30, 30, TimeUnit.MINUTES);
            }
            
        } catch (Exception e) {
            log.error("连接Binance Futures用户数据流失败: {}", e.getMessage(), e);
            accountSink.tryEmitError(e);
        }
        
        return accountSink.asFlux();
    }
    
    /**
     * 重连用户数据流
     */
    private void reconnectUserDataStream(String userId) {
        if (futuresApiClient != null) {
            futuresApiClient.createListenKey()
                    .flatMap(newListenKey -> {
                        this.listenKey = newListenKey;
                        return Mono.just(newListenKey);
                    })
                    .subscribe(
                            newListenKey -> {
                                log.info("重新创建listenKey成功，重连用户数据流");
                                subscribeUserDataStream(userId, newListenKey).subscribe();
                            },
                            error -> {
                                log.error("重新创建listenKey失败: {}", error.getMessage());
                                // 延迟后重试
                                scheduler.schedule(() -> reconnectUserDataStream(userId), 30, TimeUnit.SECONDS);
                            }
                    );
        }
    }
    
    /**
     * 解析ACCOUNT_UPDATE事件
     * 参考: https://developers.binance.com/docs/derivatives/usds-margined-futures/user-data-streams/event-account-update
     */
    @SuppressWarnings("unchecked")
    private AccountInfo parseAccountUpdate(Map<String, Object> data, String userId) {
        try {
            Map<String, Object> accountData = (Map<String, Object>) data.get("a");
            if (accountData == null) {
                log.warn("ACCOUNT_UPDATE事件中没有a字段");
                return null;
            }
            
            // 解析账户余额信息
            List<Map<String, Object>> balances = (List<Map<String, Object>>) accountData.get("B");
            BigDecimal totalWalletBalance = BigDecimal.ZERO;
            BigDecimal availableBalance = BigDecimal.ZERO;
            BigDecimal frozenBalance = BigDecimal.ZERO;
            
            if (balances != null) {
                for (Map<String, Object> balance : balances) {
                    String asset = (String) balance.get("a");
                    String walletBalanceStr = (String) balance.get("wb");
                    String availableBalanceStr = (String) balance.get("cw");
                    
                    if ("USDT".equals(asset)) {
                        totalWalletBalance = new BigDecimal(walletBalanceStr);
                        availableBalance = new BigDecimal(availableBalanceStr);
                        frozenBalance = totalWalletBalance.subtract(availableBalance);
                        if (frozenBalance.compareTo(BigDecimal.ZERO) < 0) {
                            frozenBalance = BigDecimal.ZERO;
                        }
                        break;
                    }
                }
            }
            
            // 解析持仓信息
            List<Map<String, Object>> positionsData = (List<Map<String, Object>>) accountData.get("P");
            List<Position> positions = new ArrayList<>();
            BigDecimal totalUnrealizedProfit = BigDecimal.ZERO;
            
            if (positionsData != null) {
                for (Map<String, Object> posData : positionsData) {
                    String symbol = (String) posData.get("s");
                    String positionAmtStr = (String) posData.getOrDefault("pa", "0");
                    BigDecimal positionAmt = new BigDecimal(positionAmtStr);
                    
                    // 只处理有持仓的
                    if (positionAmt.compareTo(BigDecimal.ZERO) != 0) {
                        String entryPriceStr = (String) posData.getOrDefault("ep", "0");
                        String markPriceStr = (String) posData.getOrDefault("mp", "0");
                        String unrealizedPnlStr = (String) posData.getOrDefault("up", "0");
                        String leverageStr = (String) posData.getOrDefault("l", "1");
                        String isolatedMarginStr = (String) posData.getOrDefault("iw", "0");
                        String positionSide = (String) posData.getOrDefault("ps", "BOTH");
                        
                        BigDecimal entryPrice = new BigDecimal(entryPriceStr);
                        BigDecimal markPrice = new BigDecimal(markPriceStr);
                        BigDecimal unrealizedPnl = new BigDecimal(unrealizedPnlStr);
                        BigDecimal leverage = new BigDecimal(leverageStr);
                        BigDecimal isolatedMargin = new BigDecimal(isolatedMarginStr);
                        
                        totalUnrealizedProfit = totalUnrealizedProfit.add(unrealizedPnl);
                        
                        // 计算盈亏百分比
                        BigDecimal pnlPercentage = BigDecimal.ZERO;
                        if (isolatedMargin.compareTo(BigDecimal.ZERO) > 0) {
                            pnlPercentage = unrealizedPnl.divide(isolatedMargin, 8, BigDecimal.ROUND_HALF_UP)
                                    .multiply(new BigDecimal("100"));
                        }
                        
                        // 确定持仓方向
                        String side = "LONG";
                        if (positionAmt.compareTo(BigDecimal.ZERO) < 0) {
                            side = "SHORT";
                        } else if ("SHORT".equals(positionSide)) {
                            side = "SHORT";
                        } else if ("LONG".equals(positionSide)) {
                            side = "LONG";
                        }
                        
                        Position position = Position.builder()
                                .symbol(symbol)
                                .side(side)
                                .quantity(positionAmt.abs())
                                .available(positionAmt.abs())
                                .avgPrice(entryPrice)
                                .currentPrice(markPrice)
                                .unrealizedPnl(unrealizedPnl)
                                .pnlPercentage(pnlPercentage)
                                .leverage(leverage.intValue())
                                .margin(isolatedMargin)
                                .build();
                        
                        positions.add(position);
                    }
                }
            }
            
            // 账户权益 = 总钱包余额 + 未实现盈亏
            BigDecimal equity = totalWalletBalance.add(totalUnrealizedProfit);
            
            AccountInfo accountInfo = AccountInfo.builder()
                    .userId(userId)
                    .totalBalance(totalWalletBalance)
                    .availableBalance(availableBalance)
                    .frozenBalance(frozenBalance)
                    .equity(equity)
                    .unrealizedPnl(totalUnrealizedProfit)
                    .positions(positions)
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            log.debug("解析Binance Futures账户更新: 总钱包余额={}, 可用余额={}, 未实现盈亏={}, 持仓数量={}",
                    totalWalletBalance, availableBalance, totalUnrealizedProfit, positions.size());
            
            return accountInfo;
        } catch (Exception e) {
            log.error("解析Binance Futures账户更新失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 关闭连接
     */
    public void close() {
        if (markPriceClient != null) {
            markPriceClient.close();
        }
        if (userDataClient != null) {
            userDataClient.close();
        }
        if (futuresApiClient != null && listenKey != null) {
            futuresApiClient.deleteListenKey(listenKey)
                    .subscribe(
                            v -> log.info("删除Binance Futures listenKey成功"),
                            error -> log.error("删除Binance Futures listenKey失败: {}", error.getMessage())
                    );
        }
        scheduler.shutdown();
    }
}

