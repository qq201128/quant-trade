package com.quant.service;

import com.quant.model.ExchangeConfig;
import com.quant.model.ExchangeType;
import com.quant.repository.ExchangeConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 交易所配置服务
 * 管理每个用户每个交易所的独立配置
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeConfigService {
    
    private final ExchangeConfigRepository exchangeConfigRepository;
    
    /**
     * 保存或更新交易所配置
     */
    @Transactional
    public ExchangeConfig saveOrUpdateConfig(String userId, ExchangeType exchangeType,
                                             String apiKey, String secretKey, String passphrase) {
        ExchangeConfig config = exchangeConfigRepository
                .findByUserIdAndExchangeType(userId, exchangeType)
                .orElse(ExchangeConfig.builder()
                        .userId(userId)
                        .exchangeType(exchangeType)
                        .build());
        
        config.setApiKey(apiKey);
        config.setSecretKey(secretKey);
        config.setPassphrase(passphrase);
        
        config = exchangeConfigRepository.save(config);
        log.info("保存交易所配置: userId={}, exchangeType={}", userId, exchangeType);
        
        return config;
    }
    
    /**
     * 获取交易所配置
     */
    public ExchangeConfig getConfig(String userId, ExchangeType exchangeType) {
        return exchangeConfigRepository
                .findByUserIdAndExchangeType(userId, exchangeType)
                .orElse(null);
    }
    
    /**
     * 删除交易所配置
     */
    @Transactional
    public void deleteConfig(String userId, ExchangeType exchangeType) {
        exchangeConfigRepository
                .findByUserIdAndExchangeType(userId, exchangeType)
                .ifPresent(exchangeConfigRepository::delete);
        log.info("删除交易所配置: userId={}, exchangeType={}", userId, exchangeType);
    }
}



