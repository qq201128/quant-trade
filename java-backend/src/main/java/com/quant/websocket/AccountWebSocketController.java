package com.quant.websocket;

import com.quant.model.AccountInfo;
import com.quant.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * WebSocket控制器
 * 处理账户和仓位数据的实时推送
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AccountWebSocketController {
    
    private final AccountService accountService;
    private final SimpMessagingTemplate messagingTemplate;
    
    /**
     * 获取用户ID（从Principal或Header中）
     */
    private String getUserId(Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        if (principal != null) {
            return principal.getName();
        }
        
        // 如果Principal为null，尝试从Header中获取
        if (headerAccessor != null) {
            String userId = (String) headerAccessor.getSessionAttributes().get("userId");
            if (userId != null) {
                return userId;
            }
        }
        
        // 默认返回匿名用户（实际应该抛出异常）
        log.warn("无法获取用户ID，Principal和Header均为null");
        return "anonymous";
    }
    
    /**
     * 客户端订阅账户信息
     * 客户端连接后自动推送账户数据
     */
    @SubscribeMapping("/user/account")
    public AccountInfo subscribeAccount(Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        String userId = getUserId(principal, headerAccessor);
        // log.info("用户 {} 订阅账户信息", userId);
        
        // 立即返回当前账户信息
        return accountService.getAccountInfo(userId);
    }
    
    /**
     * 客户端请求账户信息
     * 使用SimpMessagingTemplate动态发送到用户特定的目标
     */
    @MessageMapping("/account/request")
    public void requestAccount(Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        String userId = getUserId(principal, headerAccessor);
        // log.info("用户 {} 请求账户信息", userId);
        
        // 获取账户信息
        AccountInfo accountInfo = accountService.getAccountInfo(userId);
        
        // 构建完整的目标路径
        String destination = "/user/" + userId + "/account";
        // log.info("准备发送账户信息: destination={}, userId={}, totalBalance={}, 持仓数量={}", 
        //         destination, userId, accountInfo.getTotalBalance(),
        //         accountInfo.getPositions() != null ? accountInfo.getPositions().size() : 0);
        
        // 使用SimpMessagingTemplate动态发送到用户特定的目标
        messagingTemplate.convertAndSendToUser(
            userId,
            "/account",
            accountInfo
        );
        
        // 同时尝试直接发送到完整路径（作为备用方案）
        messagingTemplate.convertAndSend(destination, accountInfo);
        
//        log.info("账户信息已发送: userId={}, destination={}", userId, destination);
    }
}

