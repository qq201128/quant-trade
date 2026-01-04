package com.quant.controller;

import com.quant.model.AccountInfo;
import com.quant.model.Position;
import com.quant.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 账户信息REST API控制器
 * 提供账户信息、持仓等查询接口
 */
@Slf4j
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AccountController {
    
    private final AccountService accountService;
    
    /**
     * 获取当前用户的账户信息
     */
    @GetMapping("/info")
    public ResponseEntity<AccountInfo> getAccountInfo() {
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            AccountInfo accountInfo = accountService.getAccountInfo(userId);
            return ResponseEntity.ok(accountInfo);
        } catch (Exception e) {
            log.error("获取账户信息失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取指定用户的账户信息（管理员功能）
     */
    @GetMapping("/info/{userId}")
    public ResponseEntity<AccountInfo> getAccountInfoByUserId(@PathVariable String userId) {
        try {
            AccountInfo accountInfo = accountService.getAccountInfo(userId);
            return ResponseEntity.ok(accountInfo);
        } catch (Exception e) {
            log.error("获取账户信息失败: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取当前用户的持仓列表
     */
    @GetMapping("/positions")
    public ResponseEntity<List<Position>> getPositions() {
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            List<Position> positions = accountService.getPositions(userId);
            return ResponseEntity.ok(positions);
        } catch (Exception e) {
            log.error("获取持仓列表失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取指定用户的持仓列表（管理员功能）
     */
    @GetMapping("/positions/{userId}")
    public ResponseEntity<List<Position>> getPositionsByUserId(@PathVariable String userId) {
        try {
            List<Position> positions = accountService.getPositions(userId);
            return ResponseEntity.ok(positions);
        } catch (Exception e) {
            log.error("获取持仓列表失败: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 刷新账户信息（强制从交易所API获取最新数据）
     */
    @PostMapping("/refresh")
    public ResponseEntity<AccountInfo> refreshAccountInfo() {
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            // 清除缓存，强制刷新
            accountService.clearCache(userId);
            AccountInfo accountInfo = accountService.getAccountInfo(userId);
            return ResponseEntity.ok(accountInfo);
        } catch (Exception e) {
            log.error("刷新账户信息失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 平仓（关闭持仓，支持部分平仓，支持按保证金平仓）
     */
    @PostMapping("/positions/{userId}/close")
    public ResponseEntity<Map<String, Object>> closePosition(
            @PathVariable String userId,
            @RequestParam String symbol,
            @RequestParam String side,
            @RequestParam(required = false) java.math.BigDecimal quantity,
            @RequestParam(required = false) java.math.BigDecimal margin) {
        try {
            log.info("用户 {} 请求平仓: symbol={}, side={}, quantity={}, margin={}", 
                    userId, symbol, side, quantity, margin);
            
            boolean success = accountService.closePosition(userId, symbol, side, quantity, margin);
            
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("success", success);
            response.put("message", success ? "平仓成功" : "平仓失败");
            
            if (success) {
                // 清除缓存，强制刷新
                accountService.clearCache(userId);
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        } catch (Exception e) {
            log.error("平仓失败: userId={}, symbol={}, side={}, quantity={}, margin={}, error={}", 
                    userId, symbol, side, quantity, margin, e.getMessage());
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("success", false);
            response.put("message", "平仓失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 开仓（加仓，支持按保证金加仓）
     */
    @PostMapping("/positions/{userId}/open")
    public ResponseEntity<Map<String, Object>> openPosition(
            @PathVariable String userId,
            @RequestParam String symbol,
            @RequestParam String side,
            @RequestParam(required = false) java.math.BigDecimal quantity,
            @RequestParam(required = false) java.math.BigDecimal margin) {
        try {
            log.info("用户 {} 请求开仓: symbol={}, side={}, quantity={}, margin={}", 
                    userId, symbol, side, quantity, margin);
            
            boolean success = accountService.openPosition(userId, symbol, side, quantity, margin);
            
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("success", success);
            response.put("message", success ? "开仓成功" : "开仓失败");
            
            if (success) {
                // 清除缓存，强制刷新
                accountService.clearCache(userId);
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        } catch (Exception e) {
            log.error("开仓失败: userId={}, symbol={}, side={}, quantity={}, margin={}, error={}", 
                    userId, symbol, side, quantity, margin, e.getMessage());
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("success", false);
            response.put("message", "开仓失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 获取当前用户ID（从请求头或JWT Token中解析）
     */
    private String getCurrentUserId() {
        // 优先从请求头获取（前端传递）
        try {
            org.springframework.web.context.request.RequestAttributes requestAttributes = 
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (requestAttributes instanceof org.springframework.web.context.request.ServletRequestAttributes) {
                jakarta.servlet.http.HttpServletRequest request = 
                    ((org.springframework.web.context.request.ServletRequestAttributes) requestAttributes).getRequest();
                String userId = request.getHeader("X-User-Id");
                if (userId != null && !userId.isEmpty()) {
                    return userId;
                }
            }
        } catch (Exception e) {
            log.debug("从请求头获取userId失败: {}", e.getMessage());
        }
        
        // 从JWT Token中解析（如果配置了JWT过滤器）
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && 
            !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }
        return null;
    }
}

