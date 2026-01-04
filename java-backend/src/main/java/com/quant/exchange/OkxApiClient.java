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
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * OKX REST API客户端
 * 用于获取账户信息、下单等
 * 
 * 参考文档: https://www.okx.com/docs-v5/en/#rest-api
 */
@Slf4j
public class OkxApiClient {
    
    private static final String BASE_URL = "https://www.okx.com";
    private final String apiKey;
    private final String secretKey;
    private final String passphrase;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public OkxApiClient(String apiKey, String secretKey, String passphrase, WebClient webClient) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.passphrase = passphrase;
        this.webClient = webClient;
    }
    
    /**
     * 获取账户余额
     * 参考: https://www.okx.com/docs-v5/en/#rest-api-account-get-balance
     */
    public Mono<Map<String, Object>> getAccountBalance(String ccy) {
        String timestamp = getTimestamp();
        String method = "GET";
        String requestPath = "/api/v5/account/balance";
        String queryString = ccy != null ? "?ccy=" + ccy : "";
        String sign = generateSign(timestamp, method, requestPath, queryString, null);
        
        return webClient.get()
                .uri(BASE_URL + requestPath + queryString)
                .header("OK-ACCESS-KEY", apiKey)
                .header("OK-ACCESS-SIGN", sign)
                .header("OK-ACCESS-TIMESTAMP", timestamp)
                .header("OK-ACCESS-PASSPHRASE", passphrase)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnError(error -> log.error("获取账户余额失败: {}", error.getMessage()));
    }
    
    /**
     * 获取持仓信息
     * 参考: https://www.okx.com/docs-v5/en/#rest-api-account-get-positions
     */
    public Mono<Map<String, Object>> getPositions(String instType, String instId) {
        String timestamp = getTimestamp();
        String method = "GET";
        String requestPath = "/api/v5/account/positions";
        StringBuilder queryBuilder = new StringBuilder();
        if (instType != null) {
            queryBuilder.append("instType=").append(instType);
        }
        if (instId != null) {
            if (queryBuilder.length() > 0) queryBuilder.append("&");
            queryBuilder.append("instId=").append(instId);
        }
        String queryString = queryBuilder.length() > 0 ? "?" + queryBuilder.toString() : "";
        String sign = generateSign(timestamp, method, requestPath, queryString, null);
        
        return webClient.get()
                .uri(BASE_URL + requestPath + queryString)
                .header("OK-ACCESS-KEY", apiKey)
                .header("OK-ACCESS-SIGN", sign)
                .header("OK-ACCESS-TIMESTAMP", timestamp)
                .header("OK-ACCESS-PASSPHRASE", passphrase)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnError(error -> log.error("获取持仓信息失败: {}", error.getMessage()));
    }
    
    /**
     * 下单
     * 参考: https://www.okx.com/docs-v5/en/#rest-api-trade-place-order
     */
    public Mono<Map<String, Object>> placeOrder(Map<String, Object> orderParams) {
        String timestamp = getTimestamp();
        String method = "POST";
        String requestPath = "/api/v5/trade/order";
        String body = null;
        try {
            body = objectMapper.writeValueAsString(orderParams);
        } catch (Exception e) {
            return Mono.error(new RuntimeException("序列化订单参数失败", e));
        }
        String sign = generateSign(timestamp, method, requestPath, "", body);
        
        return webClient.post()
                .uri(BASE_URL + requestPath)
                .header("OK-ACCESS-KEY", apiKey)
                .header("OK-ACCESS-SIGN", sign)
                .header("OK-ACCESS-TIMESTAMP", timestamp)
                .header("OK-ACCESS-PASSPHRASE", passphrase)
                .header("Content-Type", "application/json")
                .bodyValue(orderParams)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnError(error -> log.error("下单失败: {}", error.getMessage()));
    }
    
    /**
     * 生成签名
     * OKX使用HMAC SHA256签名
     */
    private String generateSign(String timestamp, String method, String requestPath, 
                               String queryString, String body) {
        try {
            String message = timestamp + method + requestPath;
            if (queryString != null && !queryString.isEmpty()) {
                message += queryString;
            }
            if (body != null && !body.isEmpty()) {
                message += body;
            }
            
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
     * 获取时间戳（ISO 8601格式，如：2020-12-08T09:08:57.715Z）
     */
    private String getTimestamp() {
        return java.time.Instant.now().toString();
    }
}

