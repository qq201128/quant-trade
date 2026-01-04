package com.quant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.model.StrategyConfig;
import com.quant.repository.StrategyConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 策略配置服务
 * 负责策略配置的数据库持久化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyConfigService {
    
    private final StrategyConfigRepository strategyConfigRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * 保存策略配置
     * 
     * @param userId 用户ID
     * @param strategyName 策略名称
     * @param strategyType 策略类型
     * @param params 配置参数（包含symbols和其他参数）
     */
    @Transactional
    public void saveStrategyConfig(String userId, String strategyName, String strategyType, Map<String, Object> params) {
        try {
            // 将配置参数转换为JSON字符串
            String configParamsJson = objectMapper.writeValueAsString(params);
            
            // 查找是否已存在配置
            StrategyConfig existingConfig = strategyConfigRepository
                    .findByUserIdAndStrategyName(userId, strategyName)
                    .orElse(null);
            
            if (existingConfig != null) {
                // 更新现有配置
                existingConfig.setStrategyType(strategyType);
                existingConfig.setConfigParams(configParamsJson);
                strategyConfigRepository.save(existingConfig);
                log.info("更新策略配置: userId={}, strategyName={}, strategyType={}", 
                        userId, strategyName, strategyType);
            } else {
                // 创建新配置
                StrategyConfig config = StrategyConfig.builder()
                        .userId(userId)
                        .strategyName(strategyName)
                        .strategyType(strategyType)
                        .configParams(configParamsJson)
                        .build();
                strategyConfigRepository.save(config);
                log.info("创建策略配置: userId={}, strategyName={}, strategyType={}", 
                        userId, strategyName, strategyType);
            }
        } catch (Exception e) {
            log.error("保存策略配置失败: userId={}, strategyName={}, error={}", 
                    userId, strategyName, e.getMessage(), e);
            throw new RuntimeException("保存策略配置失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取策略配置
     * 
     * @param userId 用户ID
     * @param strategyName 策略名称
     * @return 配置参数Map，如果不存在返回null
     */
    public Map<String, Object> getStrategyConfig(String userId, String strategyName) {
        try {
            StrategyConfig config = strategyConfigRepository
                    .findByUserIdAndStrategyName(userId, strategyName)
                    .orElse(null);
            
            if (config == null || config.getConfigParams() == null) {
                return null;
            }
            
            // 将JSON字符串转换为Map
            TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {};
            return objectMapper.readValue(config.getConfigParams(), typeRef);
        } catch (Exception e) {
            log.error("获取策略配置失败: userId={}, strategyName={}, error={}", 
                    userId, strategyName, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 删除策略配置
     * 
     * @param userId 用户ID
     * @param strategyName 策略名称
     */
    @Transactional
    public void deleteStrategyConfig(String userId, String strategyName) {
        try {
            strategyConfigRepository.deleteByUserIdAndStrategyName(userId, strategyName);
            log.info("删除策略配置: userId={}, strategyName={}", userId, strategyName);
        } catch (Exception e) {
            log.error("删除策略配置失败: userId={}, strategyName={}, error={}", 
                    userId, strategyName, e.getMessage(), e);
            throw new RuntimeException("删除策略配置失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查策略配置是否存在
     *
     * @param userId 用户ID
     * @param strategyName 策略名称
     * @return 是否存在
     */
    public boolean existsStrategyConfig(String userId, String strategyName) {
        return strategyConfigRepository.existsByUserIdAndStrategyName(userId, strategyName);
    }

    /**
     * 启用策略
     * 如果配置不存在，会创建一个新的配置（使用默认参数）
     */
    @Transactional
    public void enableStrategy(String userId, String strategyName, String exchangeType) {
        StrategyConfig config = strategyConfigRepository
                .findByUserIdAndStrategyName(userId, strategyName)
                .orElse(null);
        
        if (config != null) {
            // 更新现有配置
            config.setEnabled(true);
            config.setExchangeType(exchangeType);
            strategyConfigRepository.save(config);
            log.info("启用策略: userId={}, strategyName={}", userId, strategyName);
        } else {
            // 如果配置不存在，创建一个新的配置（使用默认参数）
            try {
                Map<String, Object> defaultParams = new HashMap<>();
                defaultParams.put("symbols", java.util.Arrays.asList("BTC/USDT"));
                String configParamsJson = objectMapper.writeValueAsString(defaultParams);
                
                config = StrategyConfig.builder()
                        .userId(userId)
                        .strategyName(strategyName)
                        .enabled(true)
                        .exchangeType(exchangeType)
                        .configParams(configParamsJson)
                        .build();
                strategyConfigRepository.save(config);
                log.info("创建并启用策略: userId={}, strategyName={}, exchangeType={}", 
                        userId, strategyName, exchangeType);
            } catch (Exception e) {
                log.error("创建策略配置失败: userId={}, strategyName={}, error={}", 
                        userId, strategyName, e.getMessage(), e);
                throw new RuntimeException("创建策略配置失败: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 禁用策略
     */
    @Transactional
    public void disableStrategy(String userId, String strategyName) {
        StrategyConfig config = strategyConfigRepository
                .findByUserIdAndStrategyName(userId, strategyName)
                .orElse(null);
        if (config != null) {
            config.setEnabled(false);
            strategyConfigRepository.save(config);
            log.info("禁用策略: userId={}, strategyName={}", userId, strategyName);
        }
    }

    /**
     * 获取所有启用的策略
     */
    public java.util.List<StrategyConfig> getEnabledStrategies() {
        return strategyConfigRepository.findByEnabledTrue();
    }
    
    /**
     * 获取用户的所有策略配置
     */
    public java.util.List<StrategyConfig> getUserStrategies(String userId) {
        return strategyConfigRepository.findByUserId(userId);
    }
}

