# 数据库设置指南

## MySQL数据库配置

### 1. 创建数据库

```sql
CREATE DATABASE quant_trading CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 配置application.yml

在 `java-backend/src/main/resources/application.yml` 中配置数据库连接：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/quant_trading?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### 3. 自动创建表

JPA会自动根据实体类创建表结构（`ddl-auto: update`）。

或者手动执行SQL脚本：

```sql
-- 创建用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL UNIQUE COMMENT '用户ID（业务标识）',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码（加密）',
    email VARCHAR(100) UNIQUE COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '手机号',
    exchange_type VARCHAR(20) COMMENT '交易所类型：OKX/BINANCE',
    api_key VARCHAR(255) COMMENT '交易所API Key（加密）',
    secret_key VARCHAR(255) COMMENT '交易所Secret Key（加密）',
    passphrase VARCHAR(255) COMMENT '交易所Passphrase（加密，仅OKX需要）',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_username (username),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

## 用户注册和登录流程

### 1. 用户注册

**API端点**: `POST /api/auth/register`

**请求体**:
```json
{
  "username": "testuser",
  "password": "password123",
  "email": "test@example.com",
  "phone": "13800138000"
}
```

**响应**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "userId": "user_abc123def456",
  "username": "testuser",
  "expiresIn": 86400
}
```

### 2. 用户登录

**API端点**: `POST /api/auth/login`

**请求体**:
```json
{
  "username": "testuser",
  "password": "password123"
}
```

**响应**: 同注册响应

### 3. 使用Token

登录成功后，将Token保存到localStorage，后续请求在Header中携带：

```
Authorization: Bearer <token>
```

### 4. WebSocket连接

连接WebSocket时，在URL中传递token：

```
ws://localhost:8080/ws?token=<jwt_token>
```

或在Header中传递：

```
Authorization: Bearer <jwt_token>
```

## 安全注意事项

1. **密码加密**: 使用BCrypt加密存储
2. **JWT密钥**: 生产环境请修改`jwt.secret`配置
3. **HTTPS**: 生产环境必须使用HTTPS
4. **Token过期**: 默认24小时，可根据需要调整
5. **API密钥加密**: 建议对交易所API密钥进行加密存储



