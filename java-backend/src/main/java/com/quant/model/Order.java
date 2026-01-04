package com.quant.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 订单模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private String orderId;
    private String symbol;
    private String side; // BUY, SELL
    private String type; // MARKET, LIMIT
    private BigDecimal quantity;
    private BigDecimal price;
    private String status; // NEW, FILLED, CANCELLED
    private Long timestamp;
}



