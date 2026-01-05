package com.quant.controller;

import com.quant.dto.ExchangeConfigResponse;
import com.quant.dto.UserInfoResponse;
import com.quant.model.ExchangeConfig;
import com.quant.model.ExchangeType;
import com.quant.model.User;
import com.quant.service.AccountService;
import com.quant.service.AuthService;
import com.quant.service.ExchangeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {
    
    private final AuthService authService;
    private final AccountService accountService;
    private final ExchangeConfigService exchangeConfigService;
    
    /**
     * 设置用户交易所配置
     * 保存指定交易所的API Key配置
     */
    @PostMapping("/{userId}/exchange")
    public ResponseEntity<ExchangeConfigResponse> setExchange(
            @PathVariable String userId,
            @RequestParam ExchangeType exchangeType,
            @RequestParam String apiKey,
            @RequestParam String secretKey,
            @RequestParam(required = false) String passphrase) {
        
        try {
            // 保存交易所配置
            ExchangeConfig config = exchangeConfigService.saveOrUpdateConfig(
                    userId, exchangeType, apiKey, secretKey, passphrase);
            
            // 更新用户的当前交易所选择（只更新exchangeType，不更新API Key）
            // 注意：API Key等敏感信息现在存储在exchange_configs表中
            authService.updateExchangeType(userId, exchangeType);
            
            // 初始化交易所连接
            accountService.initializeUserExchange(userId).subscribe();
            
            ExchangeConfigResponse response = ExchangeConfigResponse.builder()
                    .exchangeType(exchangeType.getCode())
                    .hasConfig(true)
                    .build();
            
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("设置交易所失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 获取指定交易所的配置
     */
    @GetMapping("/{userId}/exchange/{exchangeType}")
    public ResponseEntity<ExchangeConfigResponse> getExchangeConfig(
            @PathVariable String userId,
            @PathVariable ExchangeType exchangeType) {
        
        try {
            ExchangeConfig config = exchangeConfigService.getConfig(userId, exchangeType);
            
            ExchangeConfigResponse response = ExchangeConfigResponse.builder()
                    .exchangeType(exchangeType.getCode())
                    .hasConfig(config != null)
                    .apiKey(config != null && config.getApiKey() != null ? 
                            maskApiKey(config.getApiKey()) : null)  // 只返回部分用于显示
                    .build();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取交易所配置失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 掩码API Key（只显示前后几位）
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
    
    /**
     * 获取所有用户列表（管理员功能）
     */
    @GetMapping("/list")
    public ResponseEntity<java.util.List<UserInfoResponse>> getAllUsers() {
        try {
            java.util.List<User> users = authService.getAllUsers();
            
            java.util.List<UserInfoResponse> responseList = users.stream()
                    .map(user -> UserInfoResponse.builder()
                            .userId(user.getUserId())
                            .username(user.getUsername())
                            .email(user.getEmail())
                            .phone(user.getPhone())
                            .exchangeType(user.getExchangeType() != null ? user.getExchangeType().getCode() : null)
                            .enabled(user.getEnabled())
                            .createdAt(user.getCreatedAt())
                            .updatedAt(user.getUpdatedAt())
                            .build())
                    .collect(java.util.stream.Collectors.toList());
            
            return ResponseEntity.ok(responseList);
        } catch (Exception e) {
            log.error("获取用户列表失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取用户信息
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserInfoResponse> getUser(@PathVariable String userId) {
        try {
            User user = authService.getUserByUserId(userId);
            
            // 转换为响应DTO
            UserInfoResponse response = UserInfoResponse.builder()
                    .userId(user.getUserId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .phone(user.getPhone())
                    .exchangeType(user.getExchangeType() != null ? user.getExchangeType().getCode() : null)
                    .enabled(user.getEnabled())
                    .createdAt(user.getCreatedAt())
                    .updatedAt(user.getUpdatedAt())
                    .build();
            
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("获取用户信息失败: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}

