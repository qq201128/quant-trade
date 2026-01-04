package com.quant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 交易所配置响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeConfigResponse {
    private String exchangeType;
    private String apiKey;  // 注意：实际应该不返回，这里为了前端显示，可以返回部分
    private boolean hasConfig;  // 是否有配置
}



