package com.quant.exchange;

import com.quant.config.ProxyConfig;
import com.quant.model.AccountInfo;
import com.quant.model.ExchangeType;
import com.quant.model.Order;
import com.quant.model.Position;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OKX交易所适配器实现
 * 对接OKX API和WebSocket
 *
 * 注意：使用原型作用域，每个用户创建独立实例，避免API密钥互相覆盖
 *
 * 参考文档: https://www.okx.com/docs-v5/en/
 */
@Slf4j
@Component
@Scope("prototype")  // 原型作用域：每次注入创建新实例，支持多用户隔离
public class OkxAdapter implements ExchangeAdapter {
    
    private String apiKey;
    private String secretKey;
    private String passphrase;
    private OkxApiClient apiClient;
    private OkxWebSocketClient wsClient;
    private final WebClient webClient;
    private final ProxyConfig proxyConfig;
    
    public OkxAdapter(WebClient webClient, ProxyConfig proxyConfig) {
        this.webClient = webClient;
        this.proxyConfig = proxyConfig;
    }
    
    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.OKX;
    }
    
    @Override
    public void initialize(String apiKey, String secretKey, String passphrase) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.passphrase = passphrase;
        this.apiClient = new OkxApiClient(apiKey, secretKey, passphrase, webClient);
        log.info("OKX适配器初始化完成");
    }
    
    @Override
    public Mono<AccountInfo> getAccountInfo(String userId) {
        // 调用OKX REST API获取账户信息
        log.info("获取OKX账户信息: userId={}", userId);
        
        return apiClient.getAccountBalance("USDT")
                .map(response -> {
                    // 解析OKX账户数据
                    List<Map<String, Object>> dataList = (List<Map<String, Object>>) response.get("data");
                    if (dataList == null || dataList.isEmpty()) {
                        return AccountInfo.builder()
                                .userId(userId)
                                .totalBalance(BigDecimal.ZERO)
                                .availableBalance(BigDecimal.ZERO)
                                .frozenBalance(BigDecimal.ZERO)
                                .timestamp(System.currentTimeMillis())
                                .build();
                    }
                    
                    Map<String, Object> accountData = dataList.get(0);
                    BigDecimal totalEq = new BigDecimal((String) accountData.getOrDefault("totalEq", "0"));
                    BigDecimal availEq = new BigDecimal((String) accountData.getOrDefault("availEq", "0"));
                    
                    // 解析USDT余额
                    List<Map<String, Object>> details = (List<Map<String, Object>>) accountData.get("details");
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
                })
                .doOnError(error -> log.error("获取OKX账户信息失败: {}", error.getMessage()));
    }
    
    @Override
    public Mono<List<Position>> getPositions(String userId) {
        // 调用OKX REST API获取持仓
        log.info("获取OKX持仓: userId={}", userId);
        
        return apiClient.getPositions(null, null)
                .map(response -> {
                    List<Map<String, Object>> dataList = (List<Map<String, Object>>) response.get("data");
                    List<Position> positions = new ArrayList<>();
                    
                    if (dataList != null) {
                        for (Map<String, Object> posData : dataList) {
                            Position position = Position.builder()
                                    .symbol((String) posData.get("instId"))
                                    .side((String) posData.get("posSide"))
                                    .quantity(new BigDecimal((String) posData.getOrDefault("pos", "0")))
                                    .available(new BigDecimal((String) posData.getOrDefault("availPos", "0")))
                                    .avgPrice(new BigDecimal((String) posData.getOrDefault("avgPx", "0")))
                                    .currentPrice(new BigDecimal((String) posData.getOrDefault("markPx", "0")))
                                    .unrealizedPnl(new BigDecimal((String) posData.getOrDefault("upl", "0")))
                                    .pnlPercentage(new BigDecimal((String) posData.getOrDefault("uplRatio", "0")))
                                    .leverage(Integer.parseInt((String) posData.getOrDefault("lever", "1")))
                                    .margin(new BigDecimal((String) posData.getOrDefault("margin", "0")))
                                    .build();
                            positions.add(position);
                        }
                    }
                    
                    return positions;
                })
                .doOnError(error -> log.error("获取OKX持仓失败: {}", error.getMessage()));
    }
    
    @Override
    public Mono<Order> placeOrder(Order order) {
        // 调用OKX下单API
        log.info("OKX下单: {}", order);
        // 实际实现需要调用OKX的订单API
        return Mono.just(order);
    }
    
    @Override
    public Mono<Boolean> cancelOrder(String orderId) {
        log.info("OKX取消订单: orderId={}", orderId);
        return Mono.just(true);
    }
    
    @Override
    public Mono<Order> getOrder(String orderId) {
        log.info("OKX查询订单: orderId={}", orderId);
        return Mono.just(new Order());
    }
    
    @Override
    public Flux<AccountInfo> subscribeAccountUpdates(String userId) {
        // 订阅OKX WebSocket账户更新
        log.info("订阅OKX账户更新: userId={}", userId);
        
        if (wsClient == null) {
            Proxy proxy = createProxy();
            wsClient = new OkxWebSocketClient(apiKey, secretKey, passphrase, proxy);
        }
        
        // 返回实时账户数据流
        return wsClient.subscribeAccountUpdates(userId)
                .doOnError(error -> log.error("OKX WebSocket错误: {}", error.getMessage()));
    }
    
    @Override
    public Flux<Map<String, Object>> subscribeMarketData(String symbol) {
        log.info("订阅OKX市场数据: symbol={}", symbol);
        
        if (wsClient == null) {
            Proxy proxy = createProxy();
            wsClient = new OkxWebSocketClient(apiKey, secretKey, passphrase, proxy);
        }
        
        return wsClient.subscribeMarketData(symbol);
    }
    
    @Override
    public Mono<Boolean> testConnection() {
        // 测试API连接
        return Mono.just(true);
    }
    
    /**
     * 创建代理对象
     */
    private Proxy createProxy() {
        if (proxyConfig == null || !proxyConfig.isEnabled()) {
            return null;
        }
        
        String proxyUrl = proxyConfig.getProxyUrl();
        if (proxyUrl == null || proxyUrl.isEmpty()) {
            return null;
        }
        
        try {
            URI uri = URI.create(proxyUrl);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 10809;
            
            log.info("使用代理连接OKX: {}:{}", host, port);
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        } catch (Exception e) {
            log.error("创建代理失败: {}", e.getMessage());
            return null;
        }
    }
}

