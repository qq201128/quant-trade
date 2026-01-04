package com.quant.websocket;

import java.security.Principal;

/**
 * 用户Principal实现
 * 用于WebSocket认证
 */
public class UserPrincipal implements Principal {
    
    private final String userId;
    
    public UserPrincipal(String userId) {
        this.userId = userId;
    }
    
    @Override
    public String getName() {
        return userId;
    }
    
    public String getUserId() {
        return userId;
    }
}



