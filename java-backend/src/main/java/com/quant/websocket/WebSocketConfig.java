package com.quant.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * WebSocket配置
 * 支持STOMP协议，用于实时推送账户和仓位数据
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    private final WebSocketAuthInterceptor authInterceptor;
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单的内存消息代理，用于向客户端发送消息
        // 注意：必须包含"/user"前缀，才能支持用户目标消息
        config.enableSimpleBroker("/topic", "/queue", "/user");
        // 客户端发送消息的前缀
        config.setApplicationDestinationPrefixes("/app");
        // 用户目标前缀（点对点消息）
        // 当使用convertAndSendToUser时，Spring会自动将目标转换为/user/{username}/destination
        config.setUserDestinationPrefix("/user");
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册STOMP端点，客户端通过此端点连接
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // 允许跨域，生产环境应限制具体域名
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    @Override
                    protected java.security.Principal determineUser(
                            org.springframework.http.server.ServerHttpRequest request,
                            org.springframework.web.socket.WebSocketHandler wsHandler,
                            java.util.Map<String, Object> attributes) {
                        // 从attributes中获取userId并创建Principal
                        String userId = (String) attributes.get("userId");
                        if (userId != null) {
                            return new UserPrincipal(userId);
                        }
                        return null;
                    }
                })
                .addInterceptors(authInterceptor)
                .withSockJS(); // 启用SockJS支持，提供降级方案
    }
}

