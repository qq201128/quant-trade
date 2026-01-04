package com.quant.repository;

import com.quant.model.StrategyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 策略配置Repository
 */
@Repository
public interface StrategyConfigRepository extends JpaRepository<StrategyConfig, Long> {
    
    /**
     * 根据用户ID和策略名称查找配置
     */
    Optional<StrategyConfig> findByUserIdAndStrategyName(String userId, String strategyName);
    
    /**
     * 根据用户ID查找所有策略配置
     */
    List<StrategyConfig> findByUserId(String userId);
    
    /**
     * 检查用户是否已有该策略的配置
     */
    boolean existsByUserIdAndStrategyName(String userId, String strategyName);
    
    /**
     * 删除用户的策略配置
     */
    void deleteByUserIdAndStrategyName(String userId, String strategyName);

    /**
     * 查找所有启用的策略
     */
    List<StrategyConfig> findByEnabledTrue();
}

