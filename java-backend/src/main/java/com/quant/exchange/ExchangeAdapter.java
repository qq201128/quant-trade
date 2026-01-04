package com.quant.exchange;

import com.quant.model.AccountInfo;
import com.quant.model.ExchangeType;
import com.quant.model.Order;
import com.quant.model.Position;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 交易所适配器接口
 * 统一不同交易所的API调用
 */
public interface ExchangeAdapter {
    
    /**
     * 获取交易所类型
     */
    ExchangeType getExchangeType();
    
    /**
     * 初始化连接（设置API密钥等）
     */
    void initialize(String apiKey, String secretKey, String passphrase);
    
    /**
     * 获取账户信息
     */
    Mono<AccountInfo> getAccountInfo(String userId);
    
    /**
     * 获取持仓列表
     */
    Mono<List<Position>> getPositions(String userId);
    
    /**
     * 下单
     */
    Mono<Order> placeOrder(Order order);
    
    /**
     * 取消订单
     */
    Mono<Boolean> cancelOrder(String orderId);
    
    /**
     * 查询订单
     */
    Mono<Order> getOrder(String orderId);
    
    /**
     * 订阅账户和仓位变化（WebSocket）
     * 返回实时数据流
     */
    Flux<AccountInfo> subscribeAccountUpdates(String userId);
    
    /**
     * 订阅市场数据（WebSocket）
     */
    Flux<Map<String, Object>> subscribeMarketData(String symbol);
    
    /**
     * 测试连接
     */
    Mono<Boolean> testConnection();
}



