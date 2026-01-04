# WebSocket 认证失败问题分析与修复

## 问题描述

### 错误日志
```
2026-01-04 15:45:06.649 [http-nio-8080-exec-9] WARN  c.q.w.WebSocketAuthInterceptor - URL参数Token解析失败: JWT signature does not match locally computed signature. JWT validity cannot be asserted and should not be trusted.

2026-01-04 15:45:06.649 [http-nio-8080-exec-9] WARN  c.q.w.WebSocketAuthInterceptor - WebSocket连接认证失败: 缺少认证信息
```

### 用户问题
1. **登录的认证信息存放在哪里？**
2. **为什么认证失败时前端没有让用户重新登录？**

---

## 问题分析

### 1. 认证信息存储位置

**前端存储位置**：
- **存储方式**：`localStorage`
- **存储键名**：`tradingConfig`
- **存储内容**：
  ```javascript
  {
    userId: "user_xxx",
    username: "username",
    token: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    exchangeType: "OKX"
  }
  ```
- **存储位置**：`electron-app/js/app.js` 的 `saveConfig()` 方法（第163-172行）
- **加载位置**：`electron-app/js/app.js` 的 `loadConfig()` 方法（第139-161行）

### 2. JWT 签名验证失败的原因

**可能原因**：
1. **JWT Secret 密钥不匹配**
   - 开发环境：`dev-quant-trading-system-secret-key-for-jwt-token-generation-2024-very-long-secret-key`
   - 生产环境：`prod-quant-trading-system-secret-key-for-jwt-token-generation-2024-very-long-secret-key-change-this-in-production`
   - 默认值：`quant-trading-system-secret-key-for-jwt-token-generation-2024`
   - **问题**：如果后端重启后使用了不同的 secret，或者 Token 是用旧的 secret 生成的，就会导致签名验证失败

2. **Token 过期或损坏**
   - Token 可能已过期（默认24小时）
   - Token 在传输过程中被修改或损坏

3. **环境切换**
   - 从开发环境切换到生产环境（或反之），JWT secret 不同导致验证失败

### 3. 为什么前端没有触发重新登录

**原因分析**：
1. **WebSocket 握手失败发生在 `beforeHandshake` 阶段**
   - 后端 `WebSocketAuthInterceptor` 在握手前验证 Token
   - 如果验证失败，返回 `false`，连接被拒绝
   - 此时连接还未建立，SockJS 可能不会触发 STOMP 的 `connect` 错误回调

2. **原有错误处理不完善**
   - 原有代码只在 STOMP `connect` 的错误回调中处理认证失败
   - 但握手失败可能不会触发这个回调
   - 导致前端无法检测到认证失败

---

## 解决方案

### 1. 前端增强错误检测（已修复）

**修复内容**：
- 添加 SockJS 底层连接事件监听（`onclose`、`onerror`）
- 添加握手超时检测（5秒）
- 在握手失败时自动验证 Token 并触发重新登录
- 区分不同类型的连接失败（认证失败 vs 其他错误）

**关键代码**：
```javascript
// 监听 SockJS 连接关闭事件（包括握手失败）
socket.onclose = (event) => {
    // 检查是否是认证失败（HTTP 403 或 401）
    if (event.code === 403 || event.code === 401 || event.code === 1008) {
        console.warn('WebSocket认证失败，Token可能已过期或无效');
        // 验证Token并决定是否重新登录
        this.validateToken().then(isValid => {
            if (!isValid) {
                this.logout(); // 跳转到登录页面
            } else {
                // Token有效但认证失败，可能是密钥不匹配，清除Token并重新登录
                this.logout();
            }
        });
    }
};
```

### 2. JWT Secret 配置建议

**问题**：不同环境使用不同的 JWT secret 会导致 Token 无法跨环境使用

**建议**：
1. **开发环境**：使用固定的 secret，避免频繁变更
2. **生产环境**：通过环境变量设置 secret，不要硬编码
3. **Token 刷新**：当检测到 secret 不匹配时，自动清除旧 Token 并提示重新登录

**配置位置**：
- 开发环境：`java-backend/src/main/resources/application-dev.yml`
- 生产环境：`java-backend/src/main/resources/application-prod.yml`
- 默认值：`java-backend/src/main/java/com/quant/service/JwtTokenService.java`

---

## 修复后的行为

### 1. WebSocket 连接流程

```
1. 前端调用 connectWebSocket()
   ↓
2. 验证 Token 是否有效（validateToken()）
   ↓
3. 如果 Token 无效 → 直接跳转到登录页面
   ↓
4. 如果 Token 有效 → 尝试连接 WebSocket
   ↓
5. 监听 SockJS 底层事件：
   - onclose: 检测握手失败（403/401）
   - onerror: 检测连接错误
   - 超时检测：5秒内未建立连接
   ↓
6. 如果检测到认证失败：
   - 再次验证 Token
   - 如果 Token 无效 → 跳转到登录页面
   - 如果 Token 有效但认证失败 → 清除 Token 并跳转到登录页面
```

### 2. 错误处理逻辑

| 错误类型 | 检测方式 | 处理方式 |
|---------|---------|---------|
| Token 无效（过期/损坏） | `validateToken()` 返回 false | 直接跳转到登录页面 |
| 握手失败（403/401） | `socket.onclose` 事件，错误码 403/401/1008 | 验证 Token，无效则跳转登录 |
| 握手超时 | 5秒内未建立连接 | 验证 Token，无效则跳转登录 |
| 其他连接错误 | `socket.onerror` 事件 | 3秒后重试连接 |

---

## 测试建议

### 1. 测试场景

1. **Token 过期场景**
   - 等待 Token 过期（或手动修改过期时间）
   - 尝试连接 WebSocket
   - **预期**：自动跳转到登录页面

2. **JWT Secret 不匹配场景**
   - 修改后端 JWT secret
   - 使用旧 Token 尝试连接
   - **预期**：检测到认证失败，清除 Token 并跳转到登录页面

3. **网络错误场景**
   - 断开网络连接
   - 尝试连接 WebSocket
   - **预期**：显示连接失败，3秒后重试

### 2. 验证方法

1. **查看浏览器控制台**
   - 应该能看到详细的错误日志
   - 包括 Token 验证结果、连接失败原因等

2. **查看后端日志**
   - 应该能看到 WebSocket 认证失败的警告日志
   - 包括 Token 解析失败的原因

3. **用户体验**
   - 认证失败时应该自动跳转到登录页面
   - 不应该出现"卡住"或"无响应"的情况

---

## 相关文件

### 前端文件
- `electron-app/js/app.js` - 主应用逻辑，包含 WebSocket 连接和错误处理

### 后端文件
- `java-backend/src/main/java/com/quant/websocket/WebSocketAuthInterceptor.java` - WebSocket 认证拦截器
- `java-backend/src/main/java/com/quant/service/JwtTokenService.java` - JWT Token 服务
- `java-backend/src/main/resources/application-dev.yml` - 开发环境配置
- `java-backend/src/main/resources/application-prod.yml` - 生产环境配置

---

## 总结

1. **认证信息存储**：前端存储在 `localStorage` 的 `tradingConfig` 键中
2. **认证失败原因**：主要是 JWT 签名验证失败（secret 不匹配或 Token 损坏）
3. **修复方案**：增强前端错误检测，在握手失败时自动验证 Token 并触发重新登录
4. **用户体验**：认证失败时自动跳转到登录页面，不再出现"卡住"的情况

