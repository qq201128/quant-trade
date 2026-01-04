package com.quant.config;

import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.net.InetSocketAddress;
import java.net.URI;

/**
 * WebClient配置
 * 用于与Python策略服务通信和交易所API通信
 */
@Slf4j
@Configuration
public class WebClientConfig {
    
    @Value("${proxy.enabled:false}")
    private boolean proxyEnabled;
    
    @Value("${proxy.http:}")
    private String httpProxy;
    
    @Value("${proxy.https:}")
    private String httpsProxy;
    
    /**
     * 通用WebClient（用于交易所API，使用代理）
     */
    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create();
        
        // 如果启用了代理，配置代理
        if (proxyEnabled) {
            String proxyUrl = httpsProxy != null && !httpsProxy.isEmpty() ? httpsProxy : httpProxy;
            if (proxyUrl != null && !proxyUrl.isEmpty()) {
                try {
                    URI proxyUri = URI.create(proxyUrl);
                    String host = proxyUri.getHost();
                    int port = proxyUri.getPort() > 0 ? proxyUri.getPort() : 10809;
                    
                    log.info("配置HTTP代理: {}:{}", host, port);
                    
                    httpClient = httpClient.proxy(proxy -> proxy
                            .type(reactor.netty.transport.ProxyProvider.Proxy.HTTP)
                            .host(host)
                            .port(port)
                    );
                } catch (Exception e) {
                    log.error("配置代理失败: {}", e.getMessage(), e);
                }
            }
        }
        
        return WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }
    
    /**
     * Python策略服务专用WebClient（不使用代理，直接连接localhost）
     */
    @Bean(name = "pythonStrategyWebClient")
    public WebClient pythonStrategyWebClient() {
        // 创建不使用代理的HttpClient，专门用于连接本地Python服务
        HttpClient httpClient = HttpClient.create();
        
        log.info("创建Python策略服务专用WebClient（不使用代理）");
        
        return WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }
}

