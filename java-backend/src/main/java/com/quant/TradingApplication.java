package com.quant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 量化交易系统主应用
 * 
 * Java后台控制层，负责：
 * - 系统调度和任务管理
 * - 风险控制
 * - 订单执行
 * - Python策略服务调用
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class TradingApplication {
    public static void main(String[] args) {
        SpringApplication.run(TradingApplication.class, args);
    }
}

