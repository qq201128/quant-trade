package com.quant.service;

import com.quant.model.StrategyRequest;
import com.quant.model.StrategyResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 策略管理器
 * 负责与Python策略服务通信
 * 
 * 集成方式：RESTful API（推荐）
 * 也可切换为：进程调用、消息队列等
 */
@Slf4j
@Service
public class StrategyManager {
    
    private final WebClient webClient;  // 通用WebClient（用于交易所API）
    private final WebClient pythonStrategyWebClient;  // Python服务专用WebClient（不使用代理）
    
    public StrategyManager(
            WebClient webClient,
            @org.springframework.beans.factory.annotation.Qualifier("pythonStrategyWebClient") WebClient pythonStrategyWebClient) {
        this.webClient = webClient;
        this.pythonStrategyWebClient = pythonStrategyWebClient;
    }
    
    @Value("${python.strategy.api.url:http://localhost:8000}")
    private String pythonApiUrl;
    
    @Value("${python.strategy.api.timeout:5000}")
    private int timeoutMs;
    
    /**
     * 调用Python策略获取交易信号
     * 
     * @param request 策略请求（包含市场数据、策略参数等）
     * @return 策略响应（包含交易信号、仓位建议等）
     */
    public Mono<StrategyResponse> executeStrategy(StrategyRequest request) {
//        log.info("调用Python策略: {}", request.getStrategyName());
        
        // 使用Python服务专用WebClient（不使用代理）
        return pythonStrategyWebClient.post()
                .uri(pythonApiUrl + "/api/strategy/execute")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(StrategyResponse.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .doOnError(error -> log.error("Python策略调用失败: {}", error.getMessage()))
                .onErrorResume(error -> {
                    // 错误处理：返回默认响应或降级策略
                    log.warn("策略调用失败，使用默认响应");
                    return Mono.just(StrategyResponse.defaultResponse());
                });
    }
    
    /**
     * 健康检查：验证Python策略服务是否可用
     */
    public Mono<Boolean> healthCheck() {
        // 使用Python服务专用WebClient（不使用代理）
        return pythonStrategyWebClient.get()
                .uri(pythonApiUrl + "/health")
                .retrieve()
                .bodyToMono(java.util.Map.class)
                .map(response -> {
                    // Python服务返回 {"status": "ok"}
                    Object status = response != null ? response.get("status") : null;
                    return "ok".equalsIgnoreCase(String.valueOf(status));
                })
                .timeout(Duration.ofMillis(2000))
                .onErrorResume(error -> {
                    log.warn("Python策略服务健康检查失败: {}", error.getMessage());
                    return Mono.just(false);
                });
    }
}

