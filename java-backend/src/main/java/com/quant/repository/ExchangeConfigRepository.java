package com.quant.repository;

import com.quant.model.ExchangeConfig;
import com.quant.model.ExchangeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 交易所配置Repository
 */
@Repository
public interface ExchangeConfigRepository extends JpaRepository<ExchangeConfig, Long> {
    
    /**
     * 根据用户ID和交易所类型查找配置
     */
    Optional<ExchangeConfig> findByUserIdAndExchangeType(String userId, ExchangeType exchangeType);
    
    /**
     * 检查用户是否已有该交易所的配置
     */
    boolean existsByUserIdAndExchangeType(String userId, ExchangeType exchangeType);
}



