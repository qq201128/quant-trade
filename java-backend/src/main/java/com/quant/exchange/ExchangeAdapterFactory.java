package com.quant.exchange;

import com.quant.model.ExchangeType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 交易所适配器工厂
 * 根据交易所类型返回对应的适配器
 *
 * 注意：支持原型作用域，每次调用 getAdapter() 都会创建新的适配器实例
 */
@Component
@RequiredArgsConstructor
public class ExchangeAdapterFactory {

    private final ApplicationContext applicationContext;

    /**
     * 根据交易所类型获取适配器（每次调用创建新实例）
     *
     * @param exchangeType 交易所类型
     * @return 新的适配器实例（原型作用域）
     */
    public ExchangeAdapter getAdapter(ExchangeType exchangeType) {
        String beanName = exchangeType.getCode().toLowerCase() + "Adapter";

        // 使用 ApplicationContext.getBean() 获取原型作用域的 Bean
        // 每次调用都会创建新实例，确保多用户隔离
        try {
            return applicationContext.getBean(beanName, ExchangeAdapter.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("不支持的交易所类型: " + exchangeType, e);
        }
    }
}



