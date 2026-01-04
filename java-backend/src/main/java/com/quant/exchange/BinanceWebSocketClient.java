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
import reactor.core.publisher.Sinks;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Binance WebSocket客户端
 * 符合Binance官方WebSocket API规范
 * 
 * 参考文档: https://developers.binance.com/docs/binance-spot-api-docs/web-socket-streams
 */
@Slf4j
public class BinanceWebSocketClient {
    
    private final String apiKey;
    private final String secretKey;
    private final BinanceApiClient apiClient;
    private WebSocketClient marketDataClient;
    private WebSocketClient userDataClient;
    private final Sinks.Many<AccountInfo> accountSink = Sinks.many().multicast().onBackpressureBuffer();
    private final Sinks.Many<Map<String, Object>> marketDataSink = Sinks.many().multicast().onBackpressureBuffer();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String listenKey;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Proxy proxy;
    private long lastMessageTime = System.currentTimeMillis();  // 记录最后收到消息的时间
    private String currentUserId;  // 当前用户ID（用于回退模式）
    
    // Binance WebSocket端点
    private static final String MARKET_DATA_ENDPOINT = "wss://stream.binance.com:9443/ws/";
    private static final String USER_DATA_ENDPOINT = "wss://stream.binance.com:9443/ws/";  // 旧版，保留兼容
    private static final String WS_API_ENDPOINT = "wss://ws-api.binance.com/ws-api/v3";  // 新版 WebSocket API（不使用端口443）
    
    public BinanceWebSocketClient(String apiKey, String secretKey, BinanceApiClient apiClient) {
        this(apiKey, secretKey, apiClient, null);
    }
    
    public BinanceWebSocketClient(String apiKey, String secretKey, BinanceApiClient apiClient, Proxy proxy) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.apiClient = apiClient;
        this.proxy = proxy;
    }
    
    /**
     * 订阅账户更新（用户数据流）
     * 使用新版 WebSocket API：会话身份验证 + userDataStream.subscribe
     * 参考: https://developers.binance.com/docs/zh-CN/binance-spot-api-docs/websocket-api/user-data-stream-requests
     */
    public Flux<AccountInfo> subscribeAccountUpdates(String userId) {
        this.currentUserId = userId;  // 保存userId用于回退模式
        try {
            URI uri = new URI(WS_API_ENDPOINT);
            
            userDataClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("Binance WebSocket API连接已建立（代理状态: {}）", 
                            (proxy != null && proxy != Proxy.NO_PROXY ? "已使用" : "未使用"));
                    lastMessageTime = System.currentTimeMillis();
                    
                    // 1. 先进行会话身份验证
                    authenticate();
                }
                
                @Override
                public void onMessage(String message) {
                    lastMessageTime = System.currentTimeMillis();
                    
                    try {
                        log.debug("收到Binance WebSocket API消息: {}", message.substring(0, Math.min(200, message.length())));
                        
                        Map<String, Object> response = objectMapper.readValue(message, Map.class);
                        
                        // 处理响应消息
                        if (response.containsKey("id")) {
                            // 这是请求响应
                            handleResponse(response);
                        } else if (response.containsKey("event")) {
                            // 这是事件推送
                            handleEvent(response, userId);
                        }
                    } catch (Exception e) {
                        log.error("解析WebSocket API消息失败: {}", e.getMessage(), e);
                    }
                }
                
                @Override
                public void onClose(int code, String reason, boolean remote) {
//                    log.warn("Binance WebSocket API连接关闭: code={}, reason={}", code, reason);
                    if (remote) {
                        reconnectUserDataStream(userId);
                    }
                }
                
                @Override
                public void onError(Exception ex) {
                    log.error("Binance WebSocket API错误: {}", ex.getMessage());
                    accountSink.tryEmitError(ex);
                }
                
                @Override
                public void onWebsocketPing(org.java_websocket.WebSocket conn, Framedata f) {
                    try {
                        if (f instanceof PingFrame) {
                            PingFrame pingFrame = (PingFrame) f;
                            PongFrame pongFrame = new PongFrame(pingFrame);
                            conn.sendFrame(pongFrame);
                            lastMessageTime = System.currentTimeMillis();
                            log.debug("收到Binance WebSocket ping帧，已回复pong帧");
                        } else {
                            PongFrame pongFrame = new PongFrame();
                            conn.sendFrame(pongFrame);
                            lastMessageTime = System.currentTimeMillis();
                        }
                    } catch (Exception e) {
                        log.error("回复pong帧失败: {}", e.getMessage());
                    }
                }
                
                @Override
                public void onWebsocketPong(org.java_websocket.WebSocket conn, Framedata f) {
                    lastMessageTime = System.currentTimeMillis();
                    log.debug("收到Binance pong响应");
                }
            };
            
            // 设置代理
            if (proxy != null && proxy != Proxy.NO_PROXY) {
                try {
                    userDataClient.setProxy(proxy);
                    InetSocketAddress proxyAddr = (InetSocketAddress) proxy.address();
//                    log.info("Binance WebSocket API已设置代理: {}:{}", proxyAddr.getHostName(), proxyAddr.getPort());
                } catch (Exception e) {
                    log.error("设置WebSocket代理失败: {}", e.getMessage(), e);
                }
            } else {
                log.info("Binance WebSocket API未使用代理（直接连接）");
            }
            
//            log.info("正在连接Binance WebSocket API: {}", uri);
            userDataClient.connect();
            
        } catch (Exception e) {
            log.error("连接Binance WebSocket API失败: {}", e.getMessage(), e);
            accountSink.tryEmitError(e);
        }
        
        return accountSink.asFlux();
    }
    
    /**
     * 会话身份验证
     * 参考: https://developers.binance.com/docs/zh-CN/binance-spot-api-docs/websocket-api/request-format#session-authentication
     */
    private void authenticate() {
        try {
            long timestamp = System.currentTimeMillis();
            String signature = generateSignature("timestamp=" + timestamp);
            
            Map<String, Object> authRequest = new HashMap<>();
            authRequest.put("id", "auth_" + System.currentTimeMillis());
            authRequest.put("method", "session.authenticate");
            Map<String, Object> params = new HashMap<>();
            params.put("apiKey", apiKey);
            params.put("timestamp", timestamp);
            params.put("signature", signature);
            authRequest.put("params", params);
            
            String requestJson = objectMapper.writeValueAsString(authRequest);
            userDataClient.send(requestJson);
//            log.info("发送Binance WebSocket API身份验证请求");
        } catch (Exception e) {
            log.error("发送身份验证请求失败: {}", e.getMessage(), e);
            accountSink.tryEmitError(e);
        }
    }
    
    /**
     * 订阅用户数据流
     */
    private void subscribeUserDataStream() {
        try {
            Map<String, Object> subscribeRequest = new HashMap<>();
            subscribeRequest.put("id", "subscribe_" + System.currentTimeMillis());
            subscribeRequest.put("method", "userDataStream.subscribe");
            subscribeRequest.put("params", new HashMap<>());
            
            String requestJson = objectMapper.writeValueAsString(subscribeRequest);
            userDataClient.send(requestJson);
            log.info("发送Binance用户数据流订阅请求");
        } catch (Exception e) {
            log.error("发送订阅请求失败: {}", e.getMessage(), e);
            accountSink.tryEmitError(e);
        }
    }
    
    /**
     * 请求账户信息（使用 account.get API）
     * 参考: https://developers.binance.com/docs/zh-CN/binance-spot-api-docs/websocket-api/account-requests#account-get
     */
    private void requestAccountInfo() {
        try {
            long timestamp = System.currentTimeMillis();
            String signature = generateSignature("timestamp=" + timestamp);
            
            Map<String, Object> request = new HashMap<>();
            request.put("id", "account_get_" + System.currentTimeMillis());
            request.put("method", "account.get");
            Map<String, Object> params = new HashMap<>();
            params.put("timestamp", timestamp);
            params.put("signature", signature);
            request.put("params", params);
            
            String requestJson = objectMapper.writeValueAsString(request);
            userDataClient.send(requestJson);
            log.info("发送Binance账户信息请求 (account.get)");
        } catch (Exception e) {
            log.error("发送账户信息请求失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 从 account.get 响应中解析账户信息
     */
    private AccountInfo parseAccountInfoFromResult(Map<String, Object> result, String userId) {
        try {
            // 解析账户余额
            List<Map<String, Object>> balances = (List<Map<String, Object>>) result.get("balances");
            if (balances == null) {
                log.warn("账户信息中没有balances字段");
                return AccountInfo.builder()
                        .userId(userId)
                        .timestamp(System.currentTimeMillis())
                        .build();
            }
            
            BigDecimal totalBalance = BigDecimal.ZERO;
            BigDecimal availableBalance = BigDecimal.ZERO;
            BigDecimal frozenBalance = BigDecimal.ZERO;
            List<Position> positions = new ArrayList<>();
            
            // 解析余额信息
            for (Map<String, Object> balance : balances) {
                String asset = (String) balance.get("asset");
                Object freeObj = balance.get("free");
                Object lockedObj = balance.get("locked");
                
                BigDecimal free = freeObj != null ? new BigDecimal(freeObj.toString()) : BigDecimal.ZERO;
                BigDecimal locked = lockedObj != null ? new BigDecimal(lockedObj.toString()) : BigDecimal.ZERO;
                
                if ("USDT".equals(asset)) {
                    availableBalance = free;
                    frozenBalance = locked;
                    totalBalance = free.add(locked);
                }
                
                // 如果有持仓，添加到positions（这里需要根据实际业务逻辑判断）
                if (free.compareTo(BigDecimal.ZERO) > 0 || locked.compareTo(BigDecimal.ZERO) > 0) {
                    // 简化处理：这里需要根据实际业务逻辑判断是否为持仓
                }
            }
            
            return AccountInfo.builder()
                    .userId(userId)
                    .totalBalance(totalBalance)
                    .availableBalance(availableBalance)
                    .frozenBalance(frozenBalance)
                    .equity(totalBalance)
                    .unrealizedPnl(BigDecimal.ZERO)
                    .positions(positions)
                    .timestamp(System.currentTimeMillis())
                    .build();
        } catch (Exception e) {
            log.error("解析账户信息失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 处理响应消息
     */
    private void handleResponse(Map<String, Object> response) {
        String id = (String) response.get("id");
        Object statusObj = response.get("status");
        Integer status = null;
        if (statusObj instanceof Integer) {
            status = (Integer) statusObj;
        } else if (statusObj instanceof Number) {
            status = ((Number) statusObj).intValue();
        }
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        Object error = response.get("error");
        
        log.info("处理Binance WebSocket API响应: id={}, status={}, error={}", id, status, error);
        
        if (id != null && id.startsWith("auth_")) {
            // 身份验证响应
            if (status != null && status == 200) {
                log.info("Binance WebSocket API身份验证成功");
                // 身份验证成功后，订阅用户数据流
                subscribeUserDataStream();
            } else {
                String errorMsg = error != null ? error.toString() : "未知错误";
                log.error("Binance WebSocket API身份验证失败: status={}, error={}, result={}", status, errorMsg, result);
                // 404 错误可能表示端点不存在，回退到旧的 listenKey 方式
                log.warn("WebSocket API认证失败，尝试使用旧的listenKey方式");
                fallbackToListenKeyMode();
            }
        } else if (id != null && id.startsWith("subscribe_")) {
            // 订阅响应
            if (status != null && status == 200) {
                Integer subscriptionId = result != null ? (Integer) result.get("subscriptionId") : null;
                log.info("Binance用户数据流订阅成功: subscriptionId={}", subscriptionId);
                // 订阅成功后，立即请求账户信息
                requestAccountInfo();
            } else {
                String errorMsg = error != null ? error.toString() : "未知错误";
                log.error("Binance用户数据流订阅失败: status={}, error={}, result={}", status, errorMsg, result);
            }
        } else if (id != null && id.startsWith("account_get_")) {
            // 账户信息请求响应
            if (status != null && status == 200 && result != null) {
                log.info("收到Binance账户信息响应");
                AccountInfo accountInfo = parseAccountInfoFromResult(result, currentUserId);
                if (accountInfo != null) {
                    accountSink.tryEmitNext(accountInfo);
                }
            } else {
                String errorMsg = error != null ? error.toString() : "未知错误";
                log.error("获取账户信息失败: status={}, error={}", status, errorMsg);
            }
        }
    }
    
    /**
     * 回退到旧的 listenKey 方式（如果新的 WebSocket API 不可用）
     */
    private void fallbackToListenKeyMode() {
//        log.info("回退到旧的listenKey方式连接Binance用户数据流");
        // 关闭当前的 WebSocket API 连接
        if (userDataClient != null) {
            try {
                userDataClient.close();
            } catch (Exception e) {
                log.debug("关闭WebSocket API连接时出错: {}", e.getMessage());
            }
        }
        
        // 使用旧的 listenKey 方式
        apiClient.createUserDataStream()
                .subscribe(
                    key -> {
                        this.listenKey = key;
                        connectUserDataStreamLegacy(key);
                    },
                    error -> {
                        log.error("创建listenKey失败: {}", error.getMessage());
                        accountSink.tryEmitError(error);
                    }
                );
    }
    
    /**
     * 使用旧的 listenKey 方式连接（作为备用方案）
     */
    private void connectUserDataStreamLegacy(String listenKey) {
        try {
            URI uri = new URI(USER_DATA_ENDPOINT + listenKey);
//            log.info("使用旧的listenKey方式连接: {}", uri);
            
            userDataClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
//                    log.info("Binance用户数据流连接已建立（listenKey方式）");
                    lastMessageTime = System.currentTimeMillis();
                    
                    // 启动定时延长listenKey（每30分钟）
                    scheduler.scheduleAtFixedRate(() -> {
                        if (userDataClient != null && userDataClient.isOpen()) {
                            apiClient.keepAliveUserDataStream(listenKey)
                                    .subscribe(
                                        null,
                                        error -> log.error("延长listenKey失败: {}", error.getMessage())
                                    );
                        }
                    }, 30, 30, TimeUnit.MINUTES);
                }
                
                @Override
                public void onMessage(String message) {
                    lastMessageTime = System.currentTimeMillis();
                    
                    try {
                        if ("ping".equalsIgnoreCase(message.trim())) {
                            userDataClient.send("pong");
                            return;
                        }
                        
                        Map<String, Object> data = objectMapper.readValue(message, Map.class);
                        String eventType = (String) data.get("e");
                        
                        if ("outboundAccountPosition".equals(eventType)) {
                            log.info("收到Binance账户更新消息 (listenKey方式)");
                            AccountInfo accountInfo = parseAccountUpdate(data, currentUserId);
                            accountSink.tryEmitNext(accountInfo);
                        }
                    } catch (Exception e) {
                        log.error("解析消息失败: {}", e.getMessage());
                    }
                }
                
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.warn("Binance用户数据流连接关闭: code={}, reason={}", code, reason);
                }
                
                @Override
                public void onError(Exception ex) {
                    log.error("Binance用户数据流错误: {}", ex.getMessage());
                }
                
                @Override
                public void onWebsocketPing(org.java_websocket.WebSocket conn, Framedata f) {
                    try {
                        if (f instanceof PingFrame) {
                            PongFrame pongFrame = new PongFrame((PingFrame) f);
                            conn.sendFrame(pongFrame);
                            lastMessageTime = System.currentTimeMillis();
                        }
                    } catch (Exception e) {
                        log.error("回复pong失败: {}", e.getMessage());
                    }
                }
            };
            
            if (proxy != null && proxy != Proxy.NO_PROXY) {
                userDataClient.setProxy(proxy);
            }
            
            userDataClient.connect();
        } catch (Exception e) {
            log.error("连接失败: {}", e.getMessage());
            accountSink.tryEmitError(e);
        }
    }
    
    /**
     * 处理事件推送
     * 事件格式: {"subscriptionId": 0, "event": {...}}
     */
    private void handleEvent(Map<String, Object> response, String userId) {
        Map<String, Object> event = (Map<String, Object>) response.get("event");
        if (event == null) {
            return;
        }
        
        String eventType = (String) event.get("e");
        
        if ("outboundAccountPosition".equals(eventType)) {
            log.info("收到Binance账户更新事件 (outboundAccountPosition)");
            AccountInfo accountInfo = parseAccountUpdate(event, userId);
            accountSink.tryEmitNext(accountInfo);
        } else if ("balanceUpdate".equals(eventType)) {
            log.info("收到Binance余额更新事件");
            // 可以在这里处理余额更新
        } else if ("executionReport".equals(eventType)) {
            log.debug("收到Binance订单执行报告");
        } else if ("eventStreamTerminated".equals(eventType)) {
            log.warn("Binance事件流已终止");
        } else {
            log.debug("收到Binance其他类型事件: eventType={}", eventType);
        }
    }
    
    /**
     * 生成签名（用于身份验证）
     */
    private String generateSignature(String data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKeySpec = 
                new javax.crypto.spec.SecretKeySpec(secretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("生成签名失败", e);
        }
    }
    
    /**
     * 连接用户数据流（旧版方式，已废弃，保留作为备用）
     * 新版本使用 WebSocket API 方式，不需要 listenKey
     */
    @Deprecated
    private void connectUserDataStream(String userId, String listenKey) {
        try {
            URI uri = new URI(USER_DATA_ENDPOINT + listenKey);
            
            // 如果使用代理，需要在创建 WebSocketClient 时传入代理
            // 但 java-websocket 库的 WebSocketClient 构造函数不支持直接传入 Proxy
            // 所以我们需要先创建客户端，然后使用 setProxy() 方法
            userDataClient = new WebSocketClient(uri) {
                    @Override
                    public void onOpen(ServerHandshake handshake) {
                        log.info("Binance用户数据流连接已建立（代理状态: {}）",
                                (proxy != null && proxy != Proxy.NO_PROXY ? "已使用" : "未使用"));
                        lastMessageTime = System.currentTimeMillis();
                        
                        // 连接建立后，立即触发一次账户信息查询并推送
                        // 因为 Binance 用户数据流只在账户变化时推送，初始连接不会立即推送
//                        log.info("连接建立，等待Binance推送账户更新（账户有变化时会自动推送）");
                        
                        // 启动定时延长listenKey（每30分钟）
                        scheduler.scheduleAtFixedRate(() -> {
                            if (userDataClient != null && userDataClient.isOpen()) {
                                apiClient.keepAliveUserDataStream(listenKey)
                                        .subscribe(
                                            null,
                                            error -> log.error("延长listenKey失败: {}", error.getMessage())
                                        );
                            }
                        }, 30, 30, TimeUnit.MINUTES);
                        
                        // 启动主动ping定时器（每60秒发送一次ping，保持连接活跃）
                        // Binance WebSocket 需要定期发送 ping 来保持连接
                        scheduler.scheduleAtFixedRate(() -> {
                            if (userDataClient != null && userDataClient.isOpen()) {
                                try {
                                    // 检查是否长时间未收到消息（超过60秒）
                                    long timeSinceLastMessage = System.currentTimeMillis() - lastMessageTime;
                                    if (timeSinceLastMessage > 60000) {
                                        // 发送ping帧保持连接
                                        userDataClient.sendPing();
                                        log.debug("主动发送Binance WebSocket ping（距离上次消息{}ms）", timeSinceLastMessage);
                                    }
                                } catch (Exception e) {
                                    log.error("发送ping失败: {}", e.getMessage());
                                }
                            }
                        }, 60, 60, TimeUnit.SECONDS);
                        
                        // 启动定期请求账户信息的定时器（每10秒请求一次）
                        // 使用 account.get API 主动获取账户信息，而不是等待事件推送
                        scheduler.scheduleAtFixedRate(() -> {
                            if (userDataClient != null && userDataClient.isOpen()) {
                                try {
                                    requestAccountInfo();
                                    log.debug("定期请求账户信息 (account.get)");
                                } catch (Exception e) {
                                    log.error("定期请求账户信息失败: {}", e.getMessage());
                                }
                            }
                        }, 10, 10, TimeUnit.SECONDS);
                    }
                    
                    @Override
                    public void onMessage(String message) {
                        lastMessageTime = System.currentTimeMillis();
                        
                        try {
                            // 检查是否是ping/pong文本消息（Binance可能使用文本消息）
                            if ("ping".equalsIgnoreCase(message.trim())) {
                                // 回复pong
                                userDataClient.send("pong");
                                log.debug("收到Binance ping文本消息，已回复pong");
                                return;
                            }
                            
                            // 记录收到的原始消息（前200字符）
                            String messagePreview = message.length() > 200 ? 
                                    message.substring(0, 200) + "..." : message;
//                            log.info("收到Binance WebSocket消息: {}", messagePreview);
                            
                            Map<String, Object> data = objectMapper.readValue(message, Map.class);
                            String eventType = (String) data.get("e");
                            
                            if ("outboundAccountPosition".equals(eventType)) {
                                // 账户余额更新
//                                log.info("收到Binance账户更新消息 (outboundAccountPosition)");
                                AccountInfo accountInfo = parseAccountUpdate(data, userId);
                                accountSink.tryEmitNext(accountInfo);
                            } else if ("executionReport".equals(eventType)) {
                                // 订单执行报告
                                log.info("收到Binance订单执行报告: {}", data);
                            } else if (eventType != null) {
                                log.info("收到Binance其他类型消息: eventType={}, data={}", eventType, data);
                            } else {
                                // 可能是其他格式的消息
                                log.info("收到Binance未知格式消息: {}", messagePreview);
                            }
                        } catch (Exception e) {
                            log.error("解析用户数据流消息失败: {}", e.getMessage(), e);
                        }
                    }
                    
                    @Override
                    public void onClose(int code, String reason, boolean remote) {
                        log.warn("Binance用户数据流连接关闭: code={}, reason={}", code, reason);
                        // 尝试重连
                        if (remote) {
                            reconnectUserDataStream(userId);
                        }
                    }
                    
                    @Override
                    public void onError(Exception ex) {
                        log.error("Binance用户数据流错误: {}", ex.getMessage());
                        accountSink.tryEmitError(ex);
                    }
                    
                    @Override
                    public void onWebsocketPing(org.java_websocket.WebSocket conn, Framedata f) {
                        // 处理WebSocket协议的ping帧，回复pong帧
                        try {
                            // 将 Framedata 转换为 PingFrame，然后创建对应的 PongFrame
                            if (f instanceof PingFrame) {
                                PingFrame pingFrame = (PingFrame) f;
                                // 创建pong帧，使用ping帧的payload
                                PongFrame pongFrame = new PongFrame(pingFrame);
                                conn.sendFrame(pongFrame);
                                lastMessageTime = System.currentTimeMillis();
                                log.debug("收到Binance WebSocket ping帧，已回复pong帧");
                            } else {
                                // 如果不是PingFrame，创建一个空的PongFrame
                                PongFrame pongFrame = new PongFrame();
                                conn.sendFrame(pongFrame);
                                lastMessageTime = System.currentTimeMillis();
                                log.debug("收到Binance WebSocket ping帧（非标准类型），已回复pong帧");
                            }
                        } catch (Exception e) {
                            log.error("回复pong帧失败: {}", e.getMessage());
                        }
                    }
                    
                    @Override
                    public void onWebsocketPong(org.java_websocket.WebSocket conn, Framedata f) {
                        // 收到pong响应，连接正常
                        lastMessageTime = System.currentTimeMillis();
                        log.debug("收到Binance pong响应");
                    }
                };
            
            // 设置代理（如果配置了）
            // 注意：setProxy() 必须在 connect() 之前调用
            if (proxy != null && proxy != Proxy.NO_PROXY) {
                try {
                    userDataClient.setProxy(proxy);
                    InetSocketAddress proxyAddr = (InetSocketAddress) proxy.address();
                    log.info("Binance WebSocket已设置代理: {}:{}", proxyAddr.getHostName(), proxyAddr.getPort());
                } catch (Exception e) {
                    log.error("设置WebSocket代理失败: {}", e.getMessage(), e);
                }
            } else {
                log.info("Binance WebSocket未使用代理（直接连接）");
            }
            
            // 连接 WebSocket（必须在设置代理之后）
            log.info("正在连接Binance WebSocket: {}", uri);
            userDataClient.connect();
            
        } catch (Exception e) {
            log.error("连接Binance用户数据流失败: {}", e.getMessage());
            accountSink.tryEmitError(e);
        }
    }
    
    /**
     * 订阅市场数据
     * 支持订阅ticker、depth、trade等
     */
    public Flux<Map<String, Object>> subscribeMarketData(String symbol) {
        try {
            // Binance流名称格式：symbol@streamType
            // 例如：btcusdt@ticker, btcusdt@depth, btcusdt@trade
            String streamName = symbol.toLowerCase() + "@ticker";
            URI uri = new URI(MARKET_DATA_ENDPOINT + streamName);
            
            marketDataClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("Binance市场数据流连接已建立: {}", streamName);
                }
                
                @Override
                public void onMessage(String message) {
                    try {
                        Map<String, Object> data = objectMapper.readValue(message, Map.class);
                        marketDataSink.tryEmitNext(data);
                    } catch (Exception e) {
                        log.error("解析市场数据失败: {}", e.getMessage());
                    }
                }
                
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.warn("Binance市场数据流连接关闭: code={}, reason={}", code, reason);
                }
                
                @Override
                public void onError(Exception ex) {
                    log.error("Binance市场数据流错误: {}", ex.getMessage());
                    marketDataSink.tryEmitError(ex);
                }
                
                @Override
                public void onWebsocketPing(org.java_websocket.WebSocket conn, Framedata f) {
                    // 处理ping帧，自动回复pong
                    conn.sendFrame(f);
                }
            };
            
            // 设置代理（如果配置了）
            if (proxy != null) {
                marketDataClient.setProxy(proxy);
            }
            
            marketDataClient.connect();
            
        } catch (Exception e) {
            log.error("连接Binance市场数据流失败: {}", e.getMessage());
            marketDataSink.tryEmitError(e);
        }
        
        return marketDataSink.asFlux();
    }
    
    /**
     * 解析账户更新消息
     * 参考: https://developers.binance.com/docs/binance-spot-api-docs/user-data-stream
     */
    private AccountInfo parseAccountUpdate(Map<String, Object> data, String userId) {
        List<Map<String, Object>> balances = (List<Map<String, Object>>) data.get("B");
        
        BigDecimal totalBalance = BigDecimal.ZERO;
        BigDecimal availableBalance = BigDecimal.ZERO;
        BigDecimal frozenBalance = BigDecimal.ZERO;
        List<Position> positions = new ArrayList<>();
        
        // 解析余额信息
        for (Map<String, Object> balance : balances) {
            String asset = (String) balance.get("a");
            BigDecimal free = new BigDecimal((String) balance.get("f"));
            BigDecimal locked = new BigDecimal((String) balance.get("l"));
            
            if ("USDT".equals(asset)) {
                availableBalance = free;
                frozenBalance = locked;
                totalBalance = free.add(locked);
            }
            
            // 如果有持仓，添加到positions
            if (free.compareTo(BigDecimal.ZERO) > 0 || locked.compareTo(BigDecimal.ZERO) > 0) {
                // 这里需要根据实际业务逻辑判断是否为持仓
                // 简化处理
            }
        }
        
        return AccountInfo.builder()
                .userId(userId)
                .totalBalance(totalBalance)
                .availableBalance(availableBalance)
                .frozenBalance(frozenBalance)
                .equity(totalBalance)
                .unrealizedPnl(BigDecimal.ZERO)
                .positions(positions)
                .timestamp((Long) data.get("E"))
                .build();
    }
    
    /**
     * 重连用户数据流
     */
    private void reconnectUserDataStream(String userId) {
        scheduler.schedule(() -> {
            log.info("尝试重连Binance WebSocket API...");
            // 关闭旧连接
            if (userDataClient != null) {
                try {
                    userDataClient.close();
                } catch (Exception e) {
                    log.debug("关闭旧连接时出错: {}", e.getMessage());
                }
            }
            // 重新订阅
            subscribeAccountUpdates(userId).subscribe();
        }, 5, TimeUnit.SECONDS);
    }
    
    /**
     * 关闭连接
     */
    public void close() {
        if (userDataClient != null) {
            userDataClient.close();
        }
        if (marketDataClient != null) {
            marketDataClient.close();
        }
        if (listenKey != null) {
            apiClient.deleteUserDataStream(listenKey).subscribe();
        }
        scheduler.shutdown();
    }
}
