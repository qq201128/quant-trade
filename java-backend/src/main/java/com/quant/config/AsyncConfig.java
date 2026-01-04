package com.quant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 * 优化线程池配置以支持多用户并发执行策略
 * 
 * 配置说明：
 * - 核心线程数：30（支持20个用户 + 10个缓冲）
 * - 最大线程数：50（峰值情况）
 * - 队列容量：100（缓冲突发请求）
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    
    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数：支持20个用户，每个用户1-2个策略
        // 30 = 20用户 × 1.5（考虑每个用户可能有多个策略）
        executor.setCorePoolSize(30);
        
        // 最大线程数：峰值情况下的最大并发
        executor.setMaxPoolSize(50);
        
        // 队列容量：缓冲突发请求
        executor.setQueueCapacity(100);
        
        // 线程名前缀：便于日志追踪
        executor.setThreadNamePrefix("strategy-exec-");
        
        // 拒绝策略：调用者运行（确保任务不丢失）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 线程空闲时间：60秒后回收
        executor.setKeepAliveSeconds(60);
        
        // 等待所有任务完成后再关闭
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        return executor;
    }
}

