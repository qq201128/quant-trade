package com.quant.controller;

import com.quant.model.StrategyConfig;
import com.quant.model.StrategyType;
import com.quant.service.StrategyConfigService;
import com.quant.service.StrategyExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 策略管理控制器
 * 处理策略的启动、停止、配置等操作
 */
@Slf4j
@RestController
@RequestMapping("/api/strategy")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StrategyController {
    
    private final StrategyExecutionService strategyExecutionService;
    private final StrategyConfigService strategyConfigService;
    
    /**
     * 启动策略
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startStrategy(
            @RequestBody Map<String, Object> request) {
        log.info("收到启动策略请求: {}", request);
        
        String userId = (String) request.get("userId");
        String strategyName = (String) request.get("strategyName");
        String strategyType = (String) request.get("strategyType");
        String exchangeType = (String) request.get("exchangeType");
        
        Map<String, Object> response = new HashMap<>();
        
        // 检查策略是否已在运行
        if (strategyExecutionService.isStrategyRunning(userId, strategyName)) {
            response.put("success", false);
            response.put("message", "策略已在运行中");
            response.put("strategyName", strategyName);
            return ResponseEntity.ok(response);
        }
        
        // 启动策略
        boolean started = strategyExecutionService.startStrategy(userId, strategyName, strategyType, exchangeType);
        
        if (started) {
            log.info("策略启动成功: userId={}, strategyName={}, strategyType={}, exchangeType={}", 
                    userId, strategyName, strategyType, exchangeType);
            response.put("success", true);
            response.put("message", "策略启动成功");
            response.put("strategyName", strategyName);
            response.put("strategyType", strategyType);
        } else {
            log.warn("策略启动失败: userId={}, strategyName={}, 可能原因：Python服务不可用或策略已在运行", 
                    userId, strategyName);
            response.put("success", false);
            response.put("message", "策略启动失败，请检查Python策略服务是否运行（http://localhost:8000）");
            response.put("strategyName", strategyName);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 停止策略
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopStrategy(
            @RequestBody Map<String, Object> request) {
        log.info("收到停止策略请求: {}", request);
        
        String userId = (String) request.get("userId");
        String strategyName = (String) request.get("strategyName");
        String strategyType = (String) request.get("strategyType");
        
        Map<String, Object> response = new HashMap<>();
        
        // 停止策略
        boolean stopped = strategyExecutionService.stopStrategy(userId, strategyName);
        
        if (stopped) {
            log.info("策略停止成功: userId={}, strategyName={}, strategyType={}", 
                    userId, strategyName, strategyType);
            response.put("success", true);
            response.put("message", "策略已停止");
            response.put("strategyName", strategyName);
            response.put("strategyType", strategyType);
        } else {
            log.warn("策略停止失败: userId={}, strategyName={}, 可能原因：策略未在运行", 
                    userId, strategyName);
            response.put("success", false);
            response.put("message", "策略未在运行");
            response.put("strategyName", strategyName);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 保存策略配置
     */
    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> saveStrategyConfig(
            @RequestBody Map<String, Object> request) {
        log.info("收到保存策略配置请求: {}", request);
        
        String userId = (String) request.get("userId");
        String strategyName = (String) request.get("strategyName");
        String strategyType = (String) request.get("strategyType");
        Map<String, Object> params = (Map<String, Object>) request.get("params");
        
        // 保存策略配置到数据库
        strategyConfigService.saveStrategyConfig(userId, strategyName, strategyType, params);
        
        log.info("保存策略配置: userId={}, strategyName={}, strategyType={}, params={}", 
                userId, strategyName, strategyType, params);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "策略配置保存成功");
        response.put("strategyName", strategyName);
        response.put("strategyType", strategyType);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取策略列表
     * 从 StrategyType 枚举动态生成策略列表
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getStrategyList() {
        log.info("收到获取策略列表请求");
        
        // 从 StrategyType 枚举动态生成策略列表
        List<Map<String, Object>> strategies = Arrays.stream(StrategyType.values())
                .map(strategyType -> {
                    Map<String, Object> strategy = new HashMap<>();
                    strategy.put("name", strategyType.getName());
                    strategy.put("type", strategyType.getCode());
                    strategy.put("description", strategyType.getDescription());
                    return strategy;
                })
                .collect(Collectors.toList());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("strategies", strategies);
        
        log.info("返回策略列表，共 {} 个策略", strategies.size());
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取策略配置
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getStrategyConfig(
            @RequestParam String userId,
            @RequestParam String strategyName) {
        log.info("收到获取策略配置请求: userId={}, strategyName={}", userId, strategyName);
        
        Map<String, Object> config = strategyConfigService.getStrategyConfig(userId, strategyName);
        
        Map<String, Object> response = new HashMap<>();
        if (config != null) {
            response.put("success", true);
            response.put("config", config);
            response.put("strategyName", strategyName);
        } else {
            response.put("success", true);
            response.put("config", null); // 没有配置
            response.put("strategyName", strategyName);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取策略状态
     */
    @GetMapping("/status/{strategyName}")
    public ResponseEntity<Map<String, Object>> getStrategyStatus(
            @PathVariable String strategyName,
            @RequestParam(required = false) String userId) {
        log.info("收到获取策略状态请求: strategyName={}, userId={}", strategyName, userId);
        
        boolean isRunning = false;
        if (userId != null && !userId.isEmpty()) {
            isRunning = strategyExecutionService.isStrategyRunning(userId, strategyName);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("strategyName", strategyName);
        response.put("status", isRunning ? "running" : "stopped"); // running, stopped, error
        response.put("message", isRunning ? "策略正在运行" : "策略当前未运行");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取用户所有策略的状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAllStrategiesStatus(
            @RequestParam String userId) {
        log.info("收到获取所有策略状态请求: userId={}", userId);
        
        // 获取用户的所有策略配置
        List<StrategyConfig> configs = strategyConfigService.getUserStrategies(userId);
        
        Map<String, Object> strategiesStatus = new HashMap<>();
        for (com.quant.model.StrategyConfig config : configs) {
            boolean isRunning = strategyExecutionService.isStrategyRunning(userId, config.getStrategyName());
            strategiesStatus.put(config.getStrategyName(), Map.of(
                "enabled", config.getEnabled() != null && config.getEnabled(),
                "running", isRunning,
                "strategyType", config.getStrategyType() != null ? config.getStrategyType() : ""
            ));
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("strategies", strategiesStatus);
        
        return ResponseEntity.ok(response);
    }
}

