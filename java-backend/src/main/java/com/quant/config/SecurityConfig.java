package com.quant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security配置
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // 禁用CSRF，因为使用JWT
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 无状态会话
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll() // 认证接口允许匿名访问
                .requestMatchers("/api/user/**").permitAll() // 用户接口暂时允许访问（实际应该需要认证）
                .requestMatchers("/api/account/**").permitAll() // 账户接口暂时允许访问（实际应该需要认证）
                .requestMatchers("/api/strategy/**").permitAll() // 策略接口暂时允许访问（实际应该需要认证）
                .requestMatchers("/ws/**").permitAll() // WebSocket允许匿名访问（在拦截器中验证）
                .anyRequest().authenticated() // 其他请求需要认证
            );
        
        return http.build();
    }
}

