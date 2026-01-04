package com.quant.service;

import com.quant.dto.AuthResponse;
import com.quant.dto.LoginRequest;
import com.quant.dto.RegisterRequest;
import com.quant.model.ExchangeType;
import com.quant.model.User;
import com.quant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 认证服务
 * 处理用户注册、登录等认证相关业务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    
    /**
     * 用户注册
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        
        // 检查邮箱是否已存在
        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new RuntimeException("邮箱已被注册");
            }
        }
        
        // 生成用户ID
        String userId = "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        
        // 创建用户
        User user = User.builder()
                .userId(userId)
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .phone(request.getPhone())
                .enabled(true)
                .build();
        
        user = userRepository.save(user);
        log.info("用户注册成功: userId={}, username={}", user.getUserId(), user.getUsername());
        
        // 生成JWT Token
        String token = jwtTokenService.generateToken(user.getUserId(), user.getUsername());
        
        return AuthResponse.builder()
                .token(token)
                .userId(user.getUserId())
                .username(user.getUsername())
                .expiresIn(jwtTokenService.getExpirationTime())
                .build();
    }
    
    /**
     * 用户登录
     */
    public AuthResponse login(LoginRequest request) {
        // 查找用户
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("用户名或密码错误"));
        
        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        
        // 检查用户是否启用
        if (!user.getEnabled()) {
            throw new RuntimeException("账户已被禁用");
        }
        
        log.info("用户登录成功: userId={}, username={}", user.getUserId(), user.getUsername());
        
        // 生成JWT Token
        String token = jwtTokenService.generateToken(user.getUserId(), user.getUsername());
        
        return AuthResponse.builder()
                .token(token)
                .userId(user.getUserId())
                .username(user.getUsername())
                .expiresIn(jwtTokenService.getExpirationTime())
                .build();
    }
    
    /**
     * 根据userId获取用户
     */
    public User getUserByUserId(String userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }
    
    /**
     * 更新用户交易所配置（已废弃，使用ExchangeConfigService）
     */
    @Deprecated
    @Transactional
    public User updateExchange(String userId, ExchangeType exchangeType, 
                              String apiKey, String secretKey, String passphrase) {
        User user = getUserByUserId(userId);
        user.setExchangeType(exchangeType);
        user.setApiKey(apiKey);
        user.setSecretKey(secretKey);
        user.setPassphrase(passphrase);
        return userRepository.save(user);
    }
    
    /**
     * 只更新用户的交易所类型（不更新API Key）
     */
    @Transactional
    public User updateExchangeType(String userId, ExchangeType exchangeType) {
        User user = getUserByUserId(userId);
        user.setExchangeType(exchangeType);
        return userRepository.save(user);
    }
}

