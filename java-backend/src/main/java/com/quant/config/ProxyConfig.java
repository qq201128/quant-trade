package com.quant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 代理配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "proxy")
public class ProxyConfig {
    
    /**
     * 是否启用代理
     */
    private boolean enabled = false;
    
    /**
     * HTTP代理地址
     */
    private String http = "";
    
    /**
     * HTTPS代理地址
     */
    private String https = "";
    
    /**
     * 获取代理地址（优先使用HTTPS代理）
     */
    public String getProxyUrl() {
        if (!enabled) {
            return null;
        }
        return (https != null && !https.isEmpty()) ? https : http;
    }
}



