# 多用户容量评估报告（更新版）

> **评估日期**: 2026-01-04
> **评估结论**: ❌ **当前不支持 20 个用户**（存在严重的架构问题）
> **修复后**: ✅ **可支持 20-30 个用户**

---

## 执行摘要

### 当前状态
- ❌ **实际只能支持 1 个用户**
- 存在 **2 个 P0 级别的阻塞性问题**
- 需要立即修复才能支持多用户

### 关键问题
1. **交易所适配器单例共享**（严重）：所有用户共享同一个适配器实例，API 密钥互相覆盖
2. **Python 策略服务单线程**（严重）：无法并发处理多个用户的策略请求

### 修复后容量
- 理论容量：**20-30 个并发用户**
- 推荐容量：**20 个用户**（留有安全余量）

---

## 系统架构分析

### ✅ 已支持的多用户特性

1. **用户隔离机制**
   - 每个用户有独立的`userId`标识
   - 策略执行使用`userId`进行隔离
   - 数据库层面支持多用户（User表、StrategyConfig表）

2. **并发处理能力**
   - 使用`ConcurrentHashMap`管理用户策略状态
   - 使用`@Async`异步执行策略循环
   - 每个用户的策略在独立线程中执行

3. **数据隔离**
   - 账户信息按`userId`查询
   - 持仓信息按`userId`获取
   - 策略配置按`userId`存储

---

## ❌ 严重问题分析

### 🔴 P0-1: 交易所适配器单例共享（阻塞性问题）

**问题位置**: `ExchangeAdapterFactory.java:22-31`

**问题描述**:
```java
@Component
public class BinanceAdapter implements ExchangeAdapter {
    private String apiKey;      // 实例变量
    private String secretKey;   // 实例变量
    // 所有用户共享同一个 Spring Bean 实例
}
```

**问题分析**:
1. `BinanceAdapter` 是 Spring 单例 Bean（默认作用域）
2. `ExchangeAdapterFactory.getAdapter()` 返回的是同一个实例
3. 多个用户调用 `initialize()` 会互相覆盖 API 密钥

**影响**:
- 用户 A 初始化后，用户 B 初始化会覆盖用户 A 的密钥
- 用户 A 的交易请求会使用用户 B 的账户执行
- **严重的安全和功能问题**
- **当前实际只能支持 1 个用户**

**严重程度**: ❌ **P0 - 阻塞性问题**

---

### 🔴 P0-2: Python 策略服务单线程（性能瓶颈）

**问题位置**: `api_server.py` + 启动脚本

**问题描述**:
```python
# 默认启动方式（单进程单线程）
uvicorn.run(app, host="0.0.0.0", port=8000)
```

**问题分析**:
1. FastAPI 默认单进程运行
2. 虽然支持异步，但 CPU 密集型策略计算会阻塞
3. 20 个用户同时请求会排队等待

**影响**:
- 策略执行响应时间长
- 成为系统瓶颈
- 无法并发处理多个用户请求

**严重程度**: ❌ **P0 - 阻塞性问题**

---

## ⚠️ 其他配置评估

### 1. 数据库连接池 ✅ 已优化

**配置位置**: `application.yml:19-25`

**当前配置**:
```yaml
hikari:
  maximum-pool-size: 30      # 最大连接数（20用户 + 10缓冲）
  minimum-idle: 10           # 最小空闲连接
  connection-timeout: 30000  # 连接超时30秒
```

**评估**: ✅ 配置合理，支持 20-30 个并发用户

---

### 2. Redis 连接池 ⚠️ 建议优化

**配置位置**: `application.yml:51-56`

**当前配置**:
```yaml
lettuce:
  pool:
    max-active: 20   # 最大连接数
    max-idle: 10     # 最大空闲连接
```

**评估**: ⚠️ 可支持 20 个用户，建议增加到 30

---

### 3. 异步线程池 ✅ 已优化

**配置位置**: `AsyncConfig.java:28-38`

**当前配置**:
```java
executor.setCorePoolSize(30);      // 核心线程数
executor.setMaxPoolSize(50);       // 最大线程数
executor.setQueueCapacity(100);    // 队列容量
```

**评估**: ✅ 配置合理，支持 20-30 个并发用户

---

### 4. WebSocket 连接管理 ⚠️ 资源消耗较大

**问题位置**: `BinanceFuturesWebSocketClient.java:47`

**问题描述**:
- 每个用户创建独立的 WebSocket 连接
- 每个连接创建 2 个调度线程
- 20 个用户 = 40 个调度线程

**影响**: 资源消耗大，但可接受

**建议**: 考虑使用连接池或共享连接

---

### 5. 定时任务调度 ⚠️ 注意限流

**配置位置**: `AccountService.java:434`

**当前配置**:
```java
@Scheduled(fixedRate = 3000) // 每3秒刷新一次
```

**问题**: 20 个用户 × 每 3 秒 = 可能触发交易所 API 限流

**建议**: 增加刷新间隔到 5-10 秒

---

## 🔧 修复方案（按优先级）

### 🔴 P0 - 必须立即修复

#### 修复 1: 交易所适配器单例问题

**方案 A: 使用原型作用域（推荐）**

```java
// BinanceAdapter.java
@Component
@Scope("prototype")  // 每次注入创建新实例
public class BinanceAdapter implements ExchangeAdapter {
    // ...
}

// OkxAdapter.java
@Component
@Scope("prototype")
public class OkxAdapter implements ExchangeAdapter {
    // ...
}
```

**方案 B: 手动创建实例**

```java
// AccountService.java
public Mono<Void> initializeUserExchange(String userId) {
    // 不使用 Spring Bean，直接创建新实例
    ExchangeAdapter adapter;
    if (user.getExchangeType() == ExchangeType.BINANCE) {
        adapter = new BinanceAdapter(webClient, proxyConfig);
    } else {
        adapter = new OkxAdapter(webClient, proxyConfig);
    }
    adapter.initialize(apiKey, secretKey, passphrase);
    userAdapters.put(userId, adapter);
    return Mono.empty();
}
```

---

#### 修复 2: Python 策略服务并发

**方案 A: 使用多进程（推荐）**

创建启动脚本：

```bash
# start.sh (Linux/Mac)
#!/bin/bash
uvicorn api_server:app --host 0.0.0.0 --port 8000 --workers 4

# start.bat (Windows)
uvicorn api_server:app --host 0.0.0.0 --port 8000 --workers 4
```

**方案 B: 使用 Gunicorn（生产环境）**

```bash
pip install gunicorn
gunicorn api_server:app -w 4 -k uvicorn.workers.UvicornWorker --bind 0.0.0.0:8000
```

---

### 🟡 P1 - 建议优化

#### 优化 1: 增加 Redis 连接池

```yaml
# application.yml
lettuce:
  pool:
    max-active: 30   # 从 20 增加到 30
    max-idle: 15     # 从 10 增加到 15
```

#### 优化 2: 调整定时任务频率

```java
@Scheduled(fixedRate = 5000) // 从 3 秒改为 5 秒
public void refreshAllAccountsInfo() {
    // ...
}
```

---

## 📊 容量评估总结

| 组件 | 修复前 | 修复后 | 状态 |
|------|--------|--------|------|
| 交易所适配器 | 1 用户 | 20-30 用户 | ❌ → ✅ |
| Python 策略服务 | 1 并发 | 20-40 并发 | ❌ → ✅ |
| 数据库连接池 | 30 连接 | 30 连接 | ✅ |
| Redis 连接池 | 20 连接 | 30 连接（建议） | ⚠️ → ✅ |
| 异步线程池 | 50 线程 | 50 线程 | ✅ |
| **整体系统** | **1 用户** | **20-30 用户** | ❌ → ✅ |

---

## 🧪 测试建议

### 1. 功能测试

**测试场景**: 验证多用户隔离
```
1. 创建用户 A 和用户 B
2. 配置不同的交易所账户
3. 同时启动策略
4. 验证：用户 A 的订单不会出现在用户 B 的账户中
```

### 2. 压力测试

**测试场景**: 20 个用户并发
```
1. 创建 20 个测试用户
2. 每个用户启动 1 个策略
3. 运行 1 小时
4. 监控：CPU、内存、数据库连接、线程池
```

### 3. 监控指标

- ✅ CPU 使用率 < 80%
- ✅ 内存使用率 < 80%
- ✅ 数据库连接数 < 25
- ✅ Redis 连接数 < 25
- ✅ 线程池队列长度 < 50
- ✅ API 响应时间 < 2 秒

---

## 📋 实施计划

### 阶段 1: 紧急修复（预计 2-4 小时）

1. ✅ 修改 `BinanceAdapter` 和 `OkxAdapter` 为原型作用域
2. ✅ 创建 Python 启动脚本（多进程）
3. ✅ 测试 2-3 个用户验证修复效果

### 阶段 2: 配置优化（预计 1-2 天）

1. ✅ 增加 Redis 连接池大小
2. ✅ 调整定时任务频率
3. ✅ 进行 20 用户压力测试

### 阶段 3: 监控和调优（预计 1 周）

1. ✅ 添加性能监控
2. ✅ 根据实际运行情况调优
3. ✅ 编写运维文档

---

## 🎯 最终结论

### 当前状态
❌ **不支持 20 个用户**
- 存在 2 个 P0 级别的阻塞性问题
- 实际只能支持 1 个用户

### 修复后状态
✅ **支持 20-30 个用户**
- 修复 P0 问题后可稳定运行
- 建议进行 P1 优化以提升性能

### 建议
1. **立即修复** P0 级别问题（2-4 小时）
2. **尽快优化** P1 级别问题（1-2 天）
3. **持续监控** 系统运行状况

修复完成后，系统可以稳定支持 **20-30 个并发用户**。

