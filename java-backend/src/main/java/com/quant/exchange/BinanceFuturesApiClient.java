package com.quant.exchange;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * Binance Futures REST API客户端
 * 用于获取期货账户信息、持仓等
 * 
 * 参考文档: https://developers.binance.com/docs/derivatives/usds-margined-futures/account/rest-api/Account-Information-V2
 */
@Slf4j
public class BinanceFuturesApiClient {
    
    private static final String BASE_URL = "https://fapi.binance.com";
    private final String apiKey;
    private final String secretKey;
    private final WebClient webClient;
    
    public BinanceFuturesApiClient(String apiKey, String secretKey, WebClient webClient) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.webClient = webClient;
    }
    
    /**
     * 获取期货账户信息
     * 参考: https://developers.binance.com/docs/derivatives/usds-margined-futures/account/rest-api/Account-Information-V2
     */
    public Mono<Map<String, Object>> getAccountInfo() {
        long timestamp = System.currentTimeMillis();
        String queryString = "timestamp=" + timestamp;
        String signature = generateSignature(queryString);
        
        String fullQueryString = queryString + "&signature=" + signature;
        
        log.debug("Binance Futures API请求账户信息: timestamp={}", timestamp);
        
        return webClient.get()
                .uri(BASE_URL + "/fapi/v2/account?" + fullQueryString)
                .header("X-MBX-APIKEY", apiKey)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnError(error -> {
                    log.error("获取Binance期货账户信息失败: {}", error.getMessage());
                    log.error("请检查: 1) API Key是否正确 2) Secret Key是否正确 3) API Key是否有期货交易权限");
                });
    }
    
    /**
     * 获取持仓信息
     * 参考: https://developers.binance.com/docs/derivatives/usds-margined-futures/account/rest-api/Position-Information-V2
     * 注意：此接口返回的是数组，不是对象
     */
    public Mono<java.util.List<Map<String, Object>>> getPositions() {
        long timestamp = System.currentTimeMillis();
        String queryString = "timestamp=" + timestamp;
        String signature = generateSignature(queryString);
        
        String fullQueryString = queryString + "&signature=" + signature;
        
        log.debug("Binance Futures API请求持仓信息: timestamp={}", timestamp);
        
        return webClient.get()
                .uri(BASE_URL + "/fapi/v2/positionRisk?" + fullQueryString)
                .header("X-MBX-APIKEY", apiKey)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<java.util.List<Map<String, Object>>>() {})
                .doOnError(error -> {
                    log.error("获取Binance期货持仓信息失败: {}", error.getMessage());
                });
    }
    
    /**
     * 获取交易所信息（包含交易对精度）
     * 参考: https://developers.binance.com/docs/derivatives/usds-margined-futures/market-data/rest-api/Exchange-Information
     */
    public Mono<Map<String, Object>> getExchangeInfo() {
        return webClient.get()
                .uri(BASE_URL + "/fapi/v1/exchangeInfo")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnError(error -> {
                    log.error("获取Binance期货交易所信息失败: {}", error.getMessage());
                });
    }
    
    /**
     * 下单（期货）
     * 参考: https://developers.binance.com/docs/derivatives/usds-margined-futures/trade/rest-api/New-Order
     * 
     * @param symbol 交易对
     * @param side 订单方向 (BUY/SELL)
     * @param type 订单类型 (MARKET/LIMIT等)
     * @param quantity 数量（已格式化的数量字符串）
     * @param positionSide 持仓方向 (LONG/SHORT/BOTH)，平仓时必需
     * @param reduceOnly 是否只减仓，平仓时应该为true
     */
    public Mono<Map<String, Object>> placeOrder(String symbol, String side, String type, String quantity, 
                                                 String positionSide, Boolean reduceOnly) {
        long timestamp = System.currentTimeMillis();
        
        // 构建查询参数
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("symbol=").append(symbol);
        queryBuilder.append("&side=").append(side);
        queryBuilder.append("&type=").append(type);
        queryBuilder.append("&quantity=").append(quantity);
        
        // 添加持仓方向（平仓时必需）
        // 注意：在双向持仓模式下，必须指定positionSide，但不接受reduceOnly参数
        if (positionSide != null && !positionSide.isEmpty()) {
            queryBuilder.append("&positionSide=").append(positionSide);
        }
        
        // 注意：根据Binance API文档，在双向持仓模式下不接受reduceOnly参数
        // 只有在单向持仓模式下才需要reduceOnly参数
        // 由于当前账户可能是双向持仓模式，所以不添加reduceOnly参数
        
        queryBuilder.append("&timestamp=").append(timestamp);
        
        String queryString = queryBuilder.toString();
        String signature = generateSignature(queryString);
        
        String fullQueryString = queryString + "&signature=" + signature;
        
        log.info("Binance期货下单: symbol={}, side={}, type={}, quantity={}, positionSide={}, reduceOnly={}", 
                symbol, side, type, quantity, positionSide, reduceOnly);
        
        return webClient.post()
                .uri(BASE_URL + "/fapi/v1/order?" + fullQueryString)
                .header("X-MBX-APIKEY", apiKey)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnNext(response -> {
                    log.info("Binance期货下单成功: orderId={}, status={}", 
                            response.get("orderId"), response.get("status"));
                })
                .doOnError(error -> {
                    log.error("Binance期货下单失败: {}", error.getMessage());
                    if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                        org.springframework.web.reactive.function.client.WebClientResponseException ex = 
                                (org.springframework.web.reactive.function.client.WebClientResponseException) error;
                        String responseBody = ex.getResponseBodyAsString();
                        log.error("Binance API错误响应: {}", responseBody);
                    }
                    log.error("请检查: 1) API Key是否有期货交易权限 2) 账户余额是否充足 3) 订单参数是否正确 4) positionSide和reduceOnly参数");
                });
    }
    
    /**
     * 创建用户数据流listenKey（用于WebSocket订阅）
     * 参考: https://developers.binance.com/docs/derivatives/usds-margined-futures/user-data-streams/start-user-data-stream
     */
    public Mono<String> createListenKey() {
        log.debug("创建Binance期货用户数据流listenKey");
        
        return webClient.post()
                .uri(BASE_URL + "/fapi/v1/listenKey")
                .header("X-MBX-APIKEY", apiKey)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(response -> (String) response.get("listenKey"))
                .doOnNext(listenKey -> log.info("创建Binance期货用户数据流listenKey成功: {}", listenKey))
                .doOnError(error -> log.error("创建Binance期货用户数据流listenKey失败: {}", error.getMessage()));
    }
    
    /**
     * 延长用户数据流listenKey有效期
     * 参考: https://developers.binance.com/docs/derivatives/usds-margined-futures/user-data-streams/keepalive-user-data-stream
     */
    public Mono<Void> keepAliveListenKey(String listenKey) {
        log.debug("延长Binance期货用户数据流listenKey有效期: {}", listenKey);
        
        return webClient.put()
                .uri(BASE_URL + "/fapi/v1/listenKey?listenKey=" + listenKey)
                .header("X-MBX-APIKEY", apiKey)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.debug("延长Binance期货用户数据流listenKey成功"))
                .doOnError(error -> log.error("延长Binance期货用户数据流listenKey失败: {}", error.getMessage()));
    }
    
    /**
     * 删除用户数据流listenKey
     * 参考: https://developers.binance.com/docs/derivatives/usds-margined-futures/user-data-streams/close-user-data-stream
     */
    public Mono<Void> deleteListenKey(String listenKey) {
        log.debug("删除Binance期货用户数据流listenKey: {}", listenKey);
        
        return webClient.delete()
                .uri(BASE_URL + "/fapi/v1/listenKey?listenKey=" + listenKey)
                .header("X-MBX-APIKEY", apiKey)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("删除Binance期货用户数据流listenKey成功"))
                .doOnError(error -> log.error("删除Binance期货用户数据流listenKey失败: {}", error.getMessage()));
    }
    
    /**
     * 生成签名
     * Binance要求使用HMAC SHA256，签名结果必须是十六进制字符串（小写）
     */
    private String generateSignature(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            
            // 转换为十六进制字符串（小写）
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("生成签名失败", e);
        }
    }
}

