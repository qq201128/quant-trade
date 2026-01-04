package com.quant.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Binance REST API客户端
 * 用于获取账户信息、创建用户数据流等
 * 
 * 参考文档: https://developers.binance.com/docs/binance-spot-api-docs
 */
@Slf4j
public class BinanceApiClient {
    
    private static final String BASE_URL = "https://api.binance.com";
    private final String apiKey;
    private final String secretKey;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public BinanceApiClient(String apiKey, String secretKey, WebClient webClient) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.webClient = webClient;
    }
    
    /**
     * 创建用户数据流listenKey
     * 参考: https://developers.binance.com/docs/binance-spot-api-docs/user-data-stream
     */
    public Mono<String> createUserDataStream() {
        return webClient.post()
                .uri(BASE_URL + "/api/v3/userDataStream")
                .header("X-MBX-APIKEY", apiKey)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(response -> (String) response.get("listenKey"))
                .doOnNext(listenKey -> log.info("创建Binance用户数据流listenKey: {}", listenKey))
                .doOnError(error -> log.error("创建用户数据流失败: {}", error.getMessage()));
    }
    
    /**
     * 延长用户数据流listenKey有效期
     */
    public Mono<Void> keepAliveUserDataStream(String listenKey) {
        return webClient.put()
                .uri(BASE_URL + "/api/v3/userDataStream?listenKey=" + listenKey)
                .header("X-MBX-APIKEY", apiKey)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(error -> log.error("延长用户数据流失败: {}", error.getMessage()));
    }
    
    /**
     * 删除用户数据流listenKey
     */
    public Mono<Void> deleteUserDataStream(String listenKey) {
        return webClient.delete()
                .uri(BASE_URL + "/api/v3/userDataStream?listenKey=" + listenKey)
                .header("X-MBX-APIKEY", apiKey)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(error -> log.error("删除用户数据流失败: {}", error.getMessage()));
    }
    
    /**
     * 获取账户信息
     * 需要签名认证
     */
    public Mono<Map<String, Object>> getAccountInfo() {
        long timestamp = System.currentTimeMillis();
        String queryString = "timestamp=" + timestamp;
        String signature = generateSignature(queryString);
        
        // 构建完整的查询字符串（包含签名）
        String fullQueryString = queryString + "&signature=" + signature;
        
        log.debug("Binance API请求: timestamp={}, signature={}", timestamp, signature);
        
        return webClient.get()
                .uri(BASE_URL + "/api/v3/account?" + fullQueryString)
                .header("X-MBX-APIKEY", apiKey)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnError(error -> {
                    log.error("获取账户信息失败: {}", error.getMessage());
                    if (apiKey != null && apiKey.length() > 10) {
                        log.error("API Key前缀: {}", apiKey.substring(0, 10) + "...");
                    } else {
                        log.error("API Key为空或无效");
                    }
                    log.error("请检查: 1) API Key是否正确 2) Secret Key是否正确 3) API Key是否有读取账户信息的权限");
                });
    }
    
    /**
     * 获取币种价格（用于计算总资产）
     * 参考: https://developers.binance.com/docs/binance-spot-api-docs/market-data-endpoints
     */
    public Mono<java.util.Map<String, String>> getSymbolPrices() {
        return webClient.get()
                .uri(BASE_URL + "/api/v3/ticker/price")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<java.util.List<Map<String, Object>>>() {})
                .map(priceList -> {
                    java.util.Map<String, String> priceMap = new java.util.HashMap<>();
                    for (Map<String, Object> priceData : priceList) {
                        String symbol = (String) priceData.get("symbol");
                        Object priceObj = priceData.get("price");
                        String price = priceObj != null ? priceObj.toString() : null;
                        if (symbol != null && price != null) {
                            priceMap.put(symbol, price);
                        }
                    }
                    log.debug("获取到{}个交易对价格", priceMap.size());
                    return priceMap;
                })
                .onErrorReturn(new java.util.HashMap<>());
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

