package com.quant.model;

/**
 * 交易所类型枚举
 */
public enum ExchangeType {
    OKX("OKX", "欧易交易所"),
    BINANCE("BINANCE", "币安交易所");
    
    private final String code;
    private final String name;
    
    ExchangeType(String code, String name) {
        this.code = code;
        this.name = name;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getName() {
        return name;
    }
}



