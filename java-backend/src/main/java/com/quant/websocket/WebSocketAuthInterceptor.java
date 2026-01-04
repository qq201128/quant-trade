package com.quant.websocket;

import com.quant.service.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket认证拦截器
 * 在WebSocket握手时进行用户认证（支持JWT Token）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements HandshakeInterceptor {
    
    private final JwtTokenService jwtTokenService;
    
    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) throws Exception {

        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            String userId = null;

            // 获取请求URI，用于判断是否是SockJS的info请求
            String uri = servletRequest.getURI().getPath();

            // SockJS的info请求和iframe请求不需要认证
            if (uri.contains("/info") || uri.contains("/iframe")) {
                log.debug("SockJS辅助请求，跳过认证: {}", uri);
                return true;
            }

            // 1. 优先从JWT Token中获取（从Header或参数）
            String token = servletRequest.getServletRequest().getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                try {
                    userId = jwtTokenService.getUserIdFromToken(token);
                    if (jwtTokenService.isTokenExpired(token)) {
                        log.warn("JWT Token已过期");
                        userId = null;
                    }
                } catch (Exception e) {
                    log.warn("JWT Token解析失败: {}", e.getMessage());
                }
            }

            // 2. 如果Token中没有，尝试从URL参数获取
            if (userId == null || userId.isEmpty()) {
                String tokenParam = servletRequest.getServletRequest().getParameter("token");
                if (tokenParam != null && !tokenParam.isEmpty()) {
                    try {
                        userId = jwtTokenService.getUserIdFromToken(tokenParam);
                    } catch (Exception e) {
                        log.warn("URL参数Token解析失败: {}", e.getMessage());
                        userId = null;
                    }
                }
            }

            // 3. 如果还是没有，尝试从userId参数获取（兼容旧方式）
            if (userId == null || userId.isEmpty()) {
                userId = servletRequest.getServletRequest().getParameter("userId");
            }

            // 4. 最后尝试从Header获取
            if (userId == null || userId.isEmpty()) {
                userId = servletRequest.getServletRequest().getHeader("X-User-Id");
            }

            if (userId != null && !userId.isEmpty()) {
                // 将userId存入attributes，供DefaultHandshakeHandler使用
                attributes.put("userId", userId);
                log.info("WebSocket连接认证成功: userId={}", userId);
                return true;
            }
        }

        log.warn("WebSocket连接认证失败: 缺少认证信息");
        return false;
    }
    
    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // 握手后的处理
    }
}

