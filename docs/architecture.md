# Java + Python 量化交易系统架构文档

## 架构设计

### 整体架构图

```
┌─────────────────────────────────────────────────────────┐
│                    Java 后台控制层                        │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐             │
│  │交易引擎   │  │策略管理器 │  │风险控制   │             │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘             │
│       │             │              │                    │
│       └─────────────┼──────────────┘                    │
│                     │                                    │
│              ┌──────▼──────┐                            │
│              │ 订单执行服务  │                            │
│              └──────┬───────┘                            │
└─────────────────────┼────────────────────────────────────┘
                      │
         ┌────────────┼────────────┐
         │            │            │
    ┌────▼────┐  ┌───▼────┐  ┌───▼────┐
    │ RESTful │  │ 消息队列│  │ 进程调用│
    │   API   │  │ (可选)  │  │ (可选)  │
    └────┬────┘  └────┬───┘  └────┬───┘
         │            │            │
┌────────┴────────────┴────────────┴────────┐
│         Python 策略服务层                  │
│  ┌──────────┐  ┌──────────┐             │
│  │均线策略   │  │ML策略     │             │
│  └──────────┘  └──────────┘             │
│  ┌──────────┐  ┌──────────┐             │
│  │其他策略...│  │策略基类   │             │
│  └──────────┘  └──────────┘             │
└──────────────────────────────────────────┘
```

## 技术集成方案对比

### 方案一：RESTful API（推荐 ⭐⭐⭐⭐⭐）

**优点：**
- ✅ 松耦合，易于扩展
- ✅ 支持分布式部署
- ✅ 语言无关，易于维护
- ✅ 支持多策略并行
- ✅ 便于监控和日志

**缺点：**
- ❌ 网络延迟（同机部署通常 < 10ms）
- ❌ 需要管理API服务

**适用场景：**
- 生产环境
- 微服务架构
- 多策略并行
- 云部署

**实现示例：**
```java
// Java端调用
StrategyResponse response = webClient.post()
    .uri("http://localhost:8000/api/strategy/execute")
    .bodyValue(request)
    .retrieve()
    .bodyToMono(StrategyResponse.class)
    .block();
```

```python
# Python端提供API
@app.post("/api/strategy/execute")
async def execute_strategy(request: StrategyRequest):
    result = strategy.execute(...)
    return StrategyResponse(**result)
```

---

### 方案二：消息队列（推荐用于高频 ⭐⭐⭐⭐）

**优点：**
- ✅ 异步处理，高吞吐
- ✅ 解耦，系统稳定
- ✅ 支持消息持久化
- ✅ 易于扩展

**缺点：**
- ❌ 系统复杂度增加
- ❌ 需要消息队列中间件

**适用场景：**
- 高频交易
- 大量策略并行
- 实时性要求高

**实现示例：**
```java
// Java端发送消息
redisTemplate.convertAndSend("strategy:execute", request);
```

```python
# Python端订阅消息
redis_client = redis.Redis()
pubsub = redis_client.pubsub()
pubsub.subscribe('strategy:execute')
```

---

### 方案三：进程调用（推荐用于原型 ⭐⭐⭐）

**优点：**
- ✅ 实现简单
- ✅ Python环境隔离
- ✅ 易于调试

**缺点：**
- ❌ 进程间通信开销
- ❌ 需要序列化数据
- ❌ 错误处理复杂

**适用场景：**
- 快速原型
- 策略执行频率不高
- 单机部署

**实现示例：**
```java
ProcessBuilder pb = new ProcessBuilder(
    "python", "strategy.py", jsonRequest
);
Process process = pb.start();
// 读取输出...
```

---

### 方案四：Jython（不推荐 ⭐）

**优点：**
- ✅ 直接集成，无进程开销

**缺点：**
- ❌ Python版本限制（仅支持Python 2.7）
- ❌ 库兼容性问题
- ❌ 性能一般

**适用场景：**
- 简单脚本
- 不依赖复杂Python库

---

## 数据流设计

### 请求流程

```
1. Java交易引擎收集市场数据
   ↓
2. 构建StrategyRequest（包含市场数据、策略参数等）
   ↓
3. 通过RESTful API调用Python策略服务
   ↓
4. Python策略执行计算，返回StrategyResponse
   ↓
5. Java风险控制验证信号
   ↓
6. 通过验证后执行订单
```

### 数据模型

**StrategyRequest (Java → Python)**
```json
{
  "strategyName": "ma_strategy",
  "symbol": "AAPL",
  "marketData": {
    "prices": [100.0, 101.0, 102.0, ...],
    "volume": 1000000,
    "timestamp": 1234567890
  },
  "strategyParams": {
    "short_period": 5,
    "long_period": 20
  },
  "position": {
    "quantity": 100,
    "avgPrice": 100.0
  },
  "account": {
    "balance": 100000.0,
    "available": 100000.0
  }
}
```

**StrategyResponse (Python → Java)**
```json
{
  "signal": "BUY",
  "position": 0.7,
  "targetPrice": 105.0,
  "stopLoss": 98.0,
  "takeProfit": 105.0,
  "confidence": 0.75,
  "metadata": {
    "ma_short": 102.0,
    "ma_long": 100.0
  }
}
```

## 性能优化建议

### 1. 连接池管理
```java
// 使用连接池复用HTTP连接
WebClient.builder()
    .clientConnector(new ReactorClientHttpConnector(
        HttpClient.create(ConnectionProvider.builder("pool")
            .maxConnections(100)
            .build())
    ))
    .build();
```

### 2. 异步非阻塞
```java
// 使用响应式编程，非阻塞调用
Mono<StrategyResponse> response = strategyManager
    .executeStrategy(request)
    .subscribeOn(Schedulers.parallel());
```

### 3. 缓存策略结果
```java
// 相同市场数据缓存策略结果
@Cacheable("strategy_results")
public Mono<StrategyResponse> executeStrategy(StrategyRequest request) {
    // ...
}
```

### 4. Python端优化
```python
# 使用异步框架
from fastapi import FastAPI
import asyncio

# 批量处理
async def batch_execute(requests: List[StrategyRequest]):
    tasks = [execute_strategy(req) for req in requests]
    return await asyncio.gather(*tasks)
```

## 部署建议

### 开发环境
- Java和Python同机部署
- 使用RESTful API，端口：Java(8080), Python(8000)

### 生产环境
- Java和Python可分离部署
- 使用负载均衡（Nginx）
- 配置健康检查和自动重启
- 监控和日志系统

### Docker部署示例
```yaml
# docker-compose.yml
version: '3.8'
services:
  java-backend:
    image: trading-backend:latest
    ports:
      - "8080:8080"
    depends_on:
      - python-strategies
  
  python-strategies:
    image: python-strategies:latest
    ports:
      - "8000:8000"
```

## 安全建议

1. **策略沙箱隔离**：Python策略运行在受限环境
2. **权限控制**：策略权限分级管理
3. **风控统一**：所有风控逻辑在Java层
4. **数据加密**：敏感数据传输加密
5. **审计日志**：记录所有策略调用和交易执行



