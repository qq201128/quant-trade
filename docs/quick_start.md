# 快速开始指南

## 系统架构概览

```
用户前端 (Web/Mobile)
    ↓ WebSocket连接
Java后台服务 (Spring Boot)
    ├── WebSocket服务器 (实时推送账户/仓位)
    ├── 交易所适配器 (OKX/Binance)
    └── 策略管理器
        ↓ RESTful API
Python策略服务 (FastAPI)
    └── 多种策略实现
```

## 1. 启动服务

### 启动Python策略服务

```bash
cd python-strategies
pip install -r requirements.txt
python api_server.py
```

服务将在 `http://localhost:8000` 启动

### 启动Java后台服务

```bash
cd java-backend
mvn clean install
mvn spring-boot:run
```

服务将在 `http://localhost:8080` 启动

## 2. 配置用户交易所

### 设置OKX交易所

```bash
curl -X POST "http://localhost:8080/api/user/user123/exchange" \
  -d "exchangeType=OKX" \
  -d "apiKey=your_okx_api_key" \
  -d "secretKey=your_okx_secret_key" \
  -d "passphrase=your_okx_passphrase"
```

### 设置Binance交易所

```bash
curl -X POST "http://localhost:8080/api/user/user123/exchange" \
  -d "exchangeType=BINANCE" \
  -d "apiKey=your_binance_api_key" \
  -d "secretKey=your_binance_secret_key"
```

设置后，系统会自动：
- 初始化交易所连接
- 订阅账户和仓位更新
- 开始通过WebSocket推送实时数据

## 3. 连接WebSocket

### HTML/JavaScript示例

```html
<!DOCTYPE html>
<html>
<head>
    <title>量化交易系统 - WebSocket客户端</title>
    <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/stompjs@2/lib/stomp.min.js"></script>
</head>
<body>
    <h1>账户信息</h1>
    <div id="accountInfo"></div>
    
    <script>
        const userId = 'user123';
        const socket = new SockJS('http://localhost:8080/ws');
        const stompClient = Stomp.over(socket);
        
        // 连接WebSocket
        stompClient.connect({userId: userId}, function(frame) {
            console.log('连接成功: ' + frame);
            
            // 订阅账户信息更新
            stompClient.subscribe('/user/' + userId + '/account', function(message) {
                const accountInfo = JSON.parse(message.body);
                console.log('收到账户更新:', accountInfo);
                
                // 更新UI
                document.getElementById('accountInfo').innerHTML = `
                    <h2>账户余额</h2>
                    <p>总资产: ${accountInfo.totalBalance} USDT</p>
                    <p>可用余额: ${accountInfo.availableBalance} USDT</p>
                    <p>冻结余额: ${accountInfo.frozenBalance} USDT</p>
                    <p>账户权益: ${accountInfo.equity} USDT</p>
                    <p>未实现盈亏: ${accountInfo.unrealizedPnl} USDT</p>
                    
                    <h2>持仓列表</h2>
                    <ul>
                        ${accountInfo.positions.map(pos => `
                            <li>
                                ${pos.symbol} - ${pos.side} - 
                                数量: ${pos.quantity} - 
                                当前价: ${pos.currentPrice} - 
                                盈亏: ${pos.unrealizedPnl} (${pos.pnlPercentage}%)
                            </li>
                        `).join('')}
                    </ul>
                `;
            });
            
            // 请求当前账户信息
            stompClient.send('/app/account/request', {}, {});
        });
    </script>
</body>
</html>
```

## 4. 使用策略

### 普通策略（调用Python）

```bash
curl -X POST "http://localhost:8000/api/strategy/execute" \
  -H "Content-Type: application/json" \
  -d '{
    "strategyName": "ma_strategy",
    "symbol": "BTC-USDT",
    "marketData": {
      "prices": [50000, 50100, 50200, 50300, 50400]
    },
    "strategyParams": {
      "short_period": 5,
      "long_period": 20
    },
    "position": {},
    "account": {}
  }'
```

### 网格策略（Java端）

网格策略在Java端实现，通过策略工厂调用：

```java
StrategyFactory strategyFactory = ...;
BaseStrategy gridStrategy = strategyFactory.getStrategy(StrategyType.GRID);

StrategyRequest request = StrategyRequest.builder()
    .strategyName("grid_strategy")
    .symbol("BTC-USDT")
    .marketData(Map.of("price", 50000.0))
    .strategyParams(Map.of(
        "gridCount", 10,
        "gridLower", 48000.0,
        "gridUpper", 52000.0
    ))
    .build();

StrategyResponse response = gridStrategy.execute(request);
```

### 双向策略（Java端）

```java
BaseStrategy dualStrategy = strategyFactory.getStrategy(StrategyType.DUAL_DIRECTION);

StrategyRequest request = StrategyRequest.builder()
    .strategyName("dual_direction_strategy")
    .symbol("BTC-USDT")
    .marketData(Map.of("price", 50000.0))
    .position(Map.of(
        "longQuantity", 1.0,
        "shortQuantity", 0.8
    ))
    .build();

StrategyResponse response = dualStrategy.execute(request);
```

## 5. 策略类型说明

### 普通策略 (NORMAL)
- **实现位置**: Python端
- **特点**: 基于技术指标、机器学习等
- **适用**: 传统量化策略
- **示例**: 均线策略、ML策略

### 网格策略 (GRID)
- **实现位置**: Java端（也可在Python端）
- **特点**: 价格区间内自动低买高卖
- **适用**: 震荡市场
- **参数**: 
  - `gridCount`: 网格数量
  - `gridLower`: 网格下限
  - `gridUpper`: 网格上限

### 双向策略 (DUAL_DIRECTION)
- **实现位置**: Java端（也可在Python端）
- **特点**: 同时持有多空仓位
- **适用**: 波动市场，通过价差获利
- **参数**: 多空比例调整阈值

## 6. 数据流说明

### 账户数据流

```
交易所 (OKX/Binance)
    ↓ WebSocket订阅
ExchangeAdapter (交易所适配器)
    ↓ 数据转换
AccountService (账户服务)
    ↓ 缓存 + WebSocket推送
前端客户端 (实时显示)
```

### 策略执行流

```
TradingEngine (交易引擎)
    ↓ 构建请求
StrategyFactory (策略工厂)
    ↓ 选择策略
BaseStrategy (策略实现)
    ├── NormalStrategy → Python API
    ├── GridStrategy → Java实现
    └── DualDirectionStrategy → Java实现
    ↓ 返回信号
RiskController (风控检查)
    ↓ 通过后
OrderService (订单执行)
    ↓
ExchangeAdapter (交易所下单)
```

## 7. 监控和调试

### 查看日志

Java服务日志：
```bash
tail -f logs/application.log
```

Python服务日志：
```bash
# 日志会输出到控制台
```

### 健康检查

```bash
# Java服务
curl http://localhost:8080/actuator/health

# Python服务
curl http://localhost:8000/health
```

## 8. 生产环境部署建议

1. **安全性**
   - API密钥加密存储
   - WebSocket连接使用WSS
   - 限制WebSocket跨域访问

2. **性能**
   - 使用Redis缓存账户数据
   - WebSocket连接池管理
   - 策略结果缓存

3. **可靠性**
   - 交易所连接断线重连
   - 策略执行异常处理
   - 订单执行确认机制

4. **监控**
   - 账户余额监控
   - 策略执行监控
   - 系统性能监控



