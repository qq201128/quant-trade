package com.quant.exchange;

import com.quant.model.AccountInfo;
import com.quant.model.Position;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.math.BigDecimal;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * OKX WebSocket客户端
 * 符合OKX官方WebSocket API规范
 * 
 * 参考文档: https://www.okx.com/docs-v5/en/#websocket-api
 */
@Slf4j
public class OkxWebSocketClient {
    
    private final String apiKey;
    private final String secretKey;
    private final String passphrase;
    private WebSocketClient privateClient;
    private WebSocketClient publicClient;
    private final Sinks.Many<AccountInfo> accountSink = Sinks.many().multicast().onBackpressureBuffer();
    private final Sinks.Many<Map<String, Object>> marketDataSink = Sinks.many().multicast().onBackpressureBuffer();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private boolean isLoggedIn = false;
    private long lastMessageTime = System.currentTimeMillis();
    private final Proxy proxy;
    
    // OKX WebSocket端点
    private static final String PRIVATE_ENDPOINT = "wss://ws.okx.com:8443/ws/v5/private";
    private static final String PUBLIC_ENDPOINT = "wss://ws.okx.com:8443/ws/v5/public";
    
    public OkxWebSocketClient(String apiKey, String secretKey, String passphrase) {
        this(apiKey, secretKey, passphrase, null);
    }
    
    public OkxWebSocketClient(String apiKey, String secretKey, String passphrase, Proxy proxy) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.passphrase = passphrase;
        this.proxy = proxy;
    }
    
    /**
     * 订阅账户更新（私有频道）
     * 需要先登录，然后订阅账户频道
     */
    public Flux<AccountInfo> subscribeAccountUpdates(String userId) {
        try {
            URI uri = new URI(PRIVATE_ENDPOINT);
            
            privateClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("OKX私有频道WebSocket连接已建立");
                    // 先登录
                    login();
                }
                
                @Override
                public void onMessage(String message) {
                    lastMessageTime = System.currentTimeMillis();
                    
                    try {
                        // 处理ping响应
                        if ("pong".equals(message)) {
                            log.debug("收到OKX pong响应");
                            return;
                        }
                        
                        Map<String, Object> data = objectMapper.readValue(message, Map.class);
                        
                        // 处理登录响应
                        if (data.containsKey("event")) {
                            String event = (String) data.get("event");
                            if ("login".equals(event)) {
                                String code = (String) data.get("code");
                                if ("0".equals(code)) {
                                    isLoggedIn = true;
                                    log.info("OKX登录成功");
                                    // 登录成功后订阅账户频道
                                    subscribeAccountChannel();
                                } else {
                                    log.error("OKX登录失败: {}", data.get("msg"));
                                    accountSink.tryEmitError(new RuntimeException("登录失败: " + data.get("msg")));
                                }
                            }
                            return;
                        }
                        
                        // 处理订阅响应
                        if (data.containsKey("arg")) {
                            Map<String, Object> arg = (Map<String, Object>) data.get("arg");
                            String channel = (String) arg.get("channel");
                            
                            if ("account".equals(channel)) {
                                // 账户更新
                                if (data.containsKey("data")) {
                                    List<Map<String, Object>> dataList = (List<Map<String, Object>>) data.get("data");
                                    if (!dataList.isEmpty()) {
                                        AccountInfo accountInfo = parseAccountUpdate(dataList.get(0), userId);
                                        accountSink.tryEmitNext(accountInfo);
                                    }
                                }
                            } else if ("positions".equals(channel)) {
                                // 持仓更新
                                if (data.containsKey("data")) {
                                    List<Map<String, Object>> dataList = (List<Map<String, Object>>) data.get("data");
                                    // 可以在这里处理持仓更新
                                    log.debug("收到持仓更新: {}", dataList.size());
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error("解析OKX消息失败: {}", e.getMessage());
                    }
                }
                
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.warn("OKX私有频道连接关闭: code={}, reason={}", code, reason);
                    isLoggedIn = false;
                    // 尝试重连
                    if (remote) {
                        reconnectPrivate(userId);
                    }
                }
                
                @Override
                public void onError(Exception ex) {
                    log.error("OKX私有频道错误: {}", ex.getMessage());
                    accountSink.tryEmitError(ex);
                }
            };
            
            // 设置代理（如果配置了）
            if (proxy != null) {
                privateClient.setProxy(proxy);
            }
            
            privateClient.connect();
            
            // 启动ping定时器（每30秒检查一次，如果30秒内没有收到消息则发送ping）
            scheduler.scheduleAtFixedRate(() -> {
                long now = System.currentTimeMillis();
                if (now - lastMessageTime > 30000) {
                    sendPing();
                }
            }, 30, 30, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            log.error("连接OKX私有频道失败: {}", e.getMessage());
            accountSink.tryEmitError(e);
        }
        
        return accountSink.asFlux();
    }
    
    /**
     * 登录
     * OKX WebSocket登录需要Unix时间戳（秒）
     */
    private void login() {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String message = timestamp + "GET" + "/users/self/verify";
            String sign = generateSign(message);
            
            Map<String, Object> loginMsg = new HashMap<>();
            loginMsg.put("op", "login");
            List<Map<String, String>> args = new ArrayList<>();
            Map<String, String> loginArgs = new HashMap<>();
            loginArgs.put("apiKey", apiKey);
            loginArgs.put("passphrase", passphrase);
            loginArgs.put("timestamp", timestamp);
            loginArgs.put("sign", sign);
            args.add(loginArgs);
            loginMsg.put("args", args);
            
            String loginJson = objectMapper.writeValueAsString(loginMsg);
            privateClient.send(loginJson);
            log.info("发送OKX登录请求");
        } catch (Exception e) {
            log.error("发送登录请求失败: {}", e.getMessage());
        }
    }
    
    /**
     * 订阅账户频道
     */
    private void subscribeAccountChannel() {
        try {
            Map<String, Object> subscribeMsg = new HashMap<>();
            subscribeMsg.put("op", "subscribe");
            List<Map<String, String>> args = new ArrayList<>();
            Map<String, String> accountArg = new HashMap<>();
            accountArg.put("channel", "account");
            accountArg.put("ccy", "USDT"); // 可以订阅特定币种，或订阅所有
            args.add(accountArg);
            subscribeMsg.put("args", args);
            
            String subscribeJson = objectMapper.writeValueAsString(subscribeMsg);
            privateClient.send(subscribeJson);
            log.info("订阅OKX账户频道");
        } catch (Exception e) {
            log.error("订阅账户频道失败: {}", e.getMessage());
        }
    }
    
    /**
     * 订阅持仓频道
     */
    public void subscribePositionsChannel(String instType) {
        if (!isLoggedIn) {
            log.warn("未登录，无法订阅持仓频道");
            return;
        }
        
        try {
            Map<String, Object> subscribeMsg = new HashMap<>();
            subscribeMsg.put("op", "subscribe");
            List<Map<String, String>> args = new ArrayList<>();
            Map<String, String> positionArg = new HashMap<>();
            positionArg.put("channel", "positions");
            if (instType != null) {
                positionArg.put("instType", instType);
            }
            args.add(positionArg);
            subscribeMsg.put("args", args);
            
            String subscribeJson = objectMapper.writeValueAsString(subscribeMsg);
            privateClient.send(subscribeJson);
            log.info("订阅OKX持仓频道: instType={}", instType);
        } catch (Exception e) {
            log.error("订阅持仓频道失败: {}", e.getMessage());
        }
    }
    
    /**
     * 订阅市场数据（公共频道）
     */
    public Flux<Map<String, Object>> subscribeMarketData(String symbol) {
        try {
            URI uri = new URI(PUBLIC_ENDPOINT);
            
            publicClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("OKX公共频道连接已建立");
                    subscribeTicker(symbol);
                }
                
                @Override
                public void onMessage(String message) {
                    lastMessageTime = System.currentTimeMillis();
                    
                    try {
                        if ("pong".equals(message)) {
                            return;
                        }
                        
                        Map<String, Object> data = objectMapper.readValue(message, Map.class);
                        marketDataSink.tryEmitNext(data);
                    } catch (Exception e) {
                        log.error("解析市场数据失败: {}", e.getMessage());
                    }
                }
                
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.warn("OKX公共频道连接关闭: code={}, reason={}", code, reason);
                }
                
                @Override
                public void onError(Exception ex) {
                    log.error("OKX公共频道错误: {}", ex.getMessage());
                    marketDataSink.tryEmitError(ex);
                }
            };
            
            // 设置代理（如果配置了）
            if (proxy != null) {
                publicClient.setProxy(proxy);
            }
            
            publicClient.connect();
            
        } catch (Exception e) {
            log.error("连接OKX公共频道失败: {}", e.getMessage());
            marketDataSink.tryEmitError(e);
        }
        
        return marketDataSink.asFlux();
    }
    
    /**
     * 订阅ticker数据
     */
    private void subscribeTicker(String symbol) {
        try {
            Map<String, Object> subscribeMsg = new HashMap<>();
            subscribeMsg.put("op", "subscribe");
            List<Map<String, String>> args = new ArrayList<>();
            Map<String, String> tickerArg = new HashMap<>();
            tickerArg.put("channel", "tickers");
            tickerArg.put("instId", symbol);
            args.add(tickerArg);
            subscribeMsg.put("args", args);
            
            String subscribeJson = objectMapper.writeValueAsString(subscribeMsg);
            publicClient.send(subscribeJson);
            log.info("订阅OKX ticker: {}", symbol);
        } catch (Exception e) {
            log.error("订阅ticker失败: {}", e.getMessage());
        }
    }
    
    /**
     * 发送ping
     */
    private void sendPing() {
        if (privateClient != null && privateClient.isOpen()) {
            privateClient.send("ping");
            log.debug("发送OKX ping");
        }
        if (publicClient != null && publicClient.isOpen()) {
            publicClient.send("ping");
        }
    }
    
    /**
     * 生成签名
     */
    private String generateSign(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("生成签名失败", e);
        }
    }
    
    /**
     * 解析账户更新
     */
    private AccountInfo parseAccountUpdate(Map<String, Object> data, String userId) {
        // OKX账户数据结构
        String ccy = (String) data.get("ccy");
        BigDecimal totalEq = new BigDecimal((String) data.getOrDefault("totalEq", "0"));
        BigDecimal availEq = new BigDecimal((String) data.getOrDefault("availEq", "0"));
        
        // 解析余额详情
        List<Map<String, Object>> details = (List<Map<String, Object>>) data.get("details");
        BigDecimal totalBalance = BigDecimal.ZERO;
        BigDecimal availableBalance = BigDecimal.ZERO;
        BigDecimal frozenBalance = BigDecimal.ZERO;
        
        if (details != null) {
            for (Map<String, Object> detail : details) {
                if ("USDT".equals(detail.get("ccy"))) {
                    totalBalance = new BigDecimal((String) detail.getOrDefault("eq", "0"));
                    availableBalance = new BigDecimal((String) detail.getOrDefault("availBal", "0"));
                    frozenBalance = new BigDecimal((String) detail.getOrDefault("frozenBal", "0"));
                    break;
                }
            }
        }
        
        return AccountInfo.builder()
                .userId(userId)
                .totalBalance(totalBalance)
                .availableBalance(availableBalance)
                .frozenBalance(frozenBalance)
                .equity(totalEq)
                .unrealizedPnl(BigDecimal.ZERO)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 重连私有频道
     */
    private void reconnectPrivate(String userId) {
        scheduler.schedule(() -> {
            log.info("尝试重连OKX私有频道...");
            subscribeAccountUpdates(userId).subscribe();
        }, 5, TimeUnit.SECONDS);
    }
    
    /**
     * 关闭连接
     */
    public void close() {
        if (privateClient != null) {
            privateClient.close();
        }
        if (publicClient != null) {
            publicClient.close();
        }
        scheduler.shutdown();
    }
}
