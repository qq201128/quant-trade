package com.quant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


/**
 * 盈利次数和补仓次数管理服务
 * 基于Redis实现持久化存储
 * 
 * 策略逻辑（参考自用auto目录）：
 * 1. 盈利次数：当某方向盈利达到50%时，该方向的盈利次数+1
 * 2. 补仓次数：当执行补仓操作时，该方向的补仓次数+1
 * 3. 清零规则：当某方向达到50%盈利时，清零另一方向的计数
 * 4. 补仓规则：每4次盈利=1次补仓机会
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfitCountService {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    // Redis Key前缀
    private static final String PROFIT_COUNT_KEY_PREFIX = "profit:count:";
    private static final String ADD_COUNT_KEY_PREFIX = "profit:add:";
    
    // 数据过期时间：永不失效（盈利次数和补仓次数需要永久保存）
    // 注意：不设置过期时间，数据将永久保存
    
    /**
     * 生成Redis Key
     * 格式：profit:count:{userId}:{symbol}:{side}
     */
    private String getProfitCountKey(String userId, String symbol, String side) {
        return PROFIT_COUNT_KEY_PREFIX + userId + ":" + symbol + ":" + side;
    }
    
    private String getAddCountKey(String userId, String symbol, String side) {
        return ADD_COUNT_KEY_PREFIX + userId + ":" + symbol + ":" + side;
    }
    
    /**
     * 获取盈利次数
     * 如果key不存在，返回0（不会自动创建key）
     */
    public Mono<Integer> getProfitCount(String userId, String symbol, String side) {
        String key = getProfitCountKey(userId, symbol, side);
        String value = redisTemplate.opsForValue().get(key);
        int count = value != null ? Integer.parseInt(value) : 0;
        return Mono.just(count);
    }
    
    /**
     * 获取补仓次数
     * 如果key不存在，返回0（不会自动创建key）
     */
    public Mono<Integer> getAddCount(String userId, String symbol, String side) {
        String key = getAddCountKey(userId, symbol, side);
        String value = redisTemplate.opsForValue().get(key);
        int count = value != null ? Integer.parseInt(value) : 0;
        return Mono.just(count);
    }
    
    /**
     * 初始化盈利次数（如果不存在则设置为0）
     * 确保key存在，即使值为0
     */
    public Mono<Void> initProfitCountIfAbsent(String userId, String symbol, String side) {
        String key = getProfitCountKey(userId, symbol, side);
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            redisTemplate.opsForValue().set(key, "0");
            log.debug("初始化盈利次数: userId={}, symbol={}, side={}, value=0", userId, symbol, side);
        }
        return Mono.empty();
    }
    
    /**
     * 初始化补仓次数（如果不存在则设置为0）
     * 确保key存在，即使值为0
     */
    public Mono<Void> initAddCountIfAbsent(String userId, String symbol, String side) {
        String key = getAddCountKey(userId, symbol, side);
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            redisTemplate.opsForValue().set(key, "0");
            log.debug("初始化补仓次数: userId={}, symbol={}, side={}, value=0", userId, symbol, side);
        }
        return Mono.empty();
    }
    
    /**
     * 增加盈利次数（当盈利达到50%时调用）
     * 如果key不存在，increment会自动创建并设置为1
     */
    public Mono<Integer> incrementProfitCount(String userId, String symbol, String side) {
        String key = getProfitCountKey(userId, symbol, side);
        // Redis的INCR命令：如果key不存在，会自动创建并设置为1
        Long newCount = redisTemplate.opsForValue().increment(key);
        // 不设置过期时间，数据永久保存
        int count = newCount != null ? newCount.intValue() : 0;
        log.info("盈利次数+1: userId={}, symbol={}, side={}, 新计数={} (Redis DB: 0)", userId, symbol, side, count);
        return Mono.just(count);
    }
    
    /**
     * 增加补仓次数（当执行补仓操作时调用）
     * 如果key不存在，increment会自动创建并设置为1
     */
    public Mono<Integer> incrementAddCount(String userId, String symbol, String side) {
        String key = getAddCountKey(userId, symbol, side);
        // Redis的INCR命令：如果key不存在，会自动创建并设置为1
        Long newCount = redisTemplate.opsForValue().increment(key);
        // 不设置过期时间，数据永久保存
        int count = newCount != null ? newCount.intValue() : 0;
        log.info("补仓次数+1: userId={}, symbol={}, side={}, 新计数={} (Redis DB: 0)", userId, symbol, side, count);
        return Mono.just(count);
    }
    
    /**
     * 清零盈利次数和补仓次数（当另一方向盈利时调用）
     */
    public Mono<Void> resetCounts(String userId, String symbol, String side) {
        String profitKey = getProfitCountKey(userId, symbol, side);
        String addKey = getAddCountKey(userId, symbol, side);

        redisTemplate.delete(profitKey);
        redisTemplate.delete(addKey);

        log.info("清零计数: userId={}, symbol={}, side={}", userId, symbol, side);
        return Mono.empty();
    }

    /**
     * 只清零补仓次数（平仓时调用，不清零盈利次数）
     */
    public Mono<Void> resetAddCount(String userId, String symbol, String side) {
        String addKey = getAddCountKey(userId, symbol, side);
        redisTemplate.delete(addKey);
        log.info("清零补仓次数: userId={}, symbol={}, side={}", userId, symbol, side);
        return Mono.empty();
    }
    
    /**
     * 检查并处理盈利达到50%的情况
     * 1. 如果之前未达到50%，现在达到50%，则盈利次数+1
     * 2. 清零另一方向的计数
     */
    public Mono<Void> handleProfitReached50(String userId, String symbol, String side, 
                                             boolean previousReached50, boolean currentReached50) {
        if (!previousReached50 && currentReached50) {
            // 刚达到50%盈利，盈利次数+1
            return incrementProfitCount(userId, symbol, side)
                    .then(resetOppositeSideCounts(userId, symbol, side));
        }
        return Mono.empty();
    }
    
    /**
     * 清零另一方向的计数
     */
    private Mono<Void> resetOppositeSideCounts(String userId, String symbol, String currentSide) {
        String oppositeSide = "LONG".equals(currentSide) ? "SHORT" : "LONG";
        return resetCounts(userId, symbol, oppositeSide);
    }
    
    /**
     * 批量获取盈利次数和补仓次数
     * 如果key不存在，会自动初始化为0
     */
    public Mono<java.util.Map<String, Integer>> getCounts(String userId, String symbol, String side) {
        // 确保key存在（如果不存在则初始化为0）
        return initProfitCountIfAbsent(userId, symbol, side)
                .then(initAddCountIfAbsent(userId, symbol, side))
                .then(Mono.zip(
                        getProfitCount(userId, symbol, side),
                        getAddCount(userId, symbol, side)
                ).map(tuple -> {
                    java.util.Map<String, Integer> counts = new java.util.HashMap<>();
                    counts.put("profitCount", tuple.getT1());
                    counts.put("addCount", tuple.getT2());
                    return counts;
                }));
    }
}

