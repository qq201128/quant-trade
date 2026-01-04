# Java + Python 量化交易系统架构

## 架构概述

本系统采用 **Java 后台控制 + Python 量化策略** 的混合架构，充分发挥两种语言的优势：

- **Java**: 企业级后台服务、高并发处理、系统稳定性、JVM生态
- **Python**: 量化策略开发、数据分析、机器学习、丰富的金融库

## 技术集成方案

### 方案一：进程调用（推荐用于快速原型）

- **优点**: 实现简单、Python环境隔离、易于调试
- **缺点**: 进程间通信开销、需要序列化
- **适用**: 策略执行频率不高、实时性要求中等

### 方案二：RESTful API（推荐用于生产环境）

- **优点**: 松耦合、可扩展、支持分布式部署
- **缺点**: 网络延迟、需要API服务管理
- **适用**: 微服务架构、多策略并行、云部署

### 方案三：消息队列（推荐用于高频交易）

- **优点**: 异步处理、高吞吐、解耦
- **缺点**: 系统复杂度增加
- **适用**: 高频策略、多策略并行、实时性要求高

### 方案四：Jython（不推荐）

- **优点**: 直接集成、无进程开销
- **缺点**: Python版本限制、库兼容性问题
- **适用**: 简单脚本、不依赖复杂Python库

## 项目结构

```
quant-trading-system/
├── java-backend/          # Java后台服务
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/quant/
│   │   │   │       ├── TradingEngine.java      # 交易引擎
│   │   │   │       ├── StrategyManager.java    # 策略管理器
│   │   │   │       ├── RiskController.java     # 风控模块
│   │   │   │       └── DataService.java        # 数据服务
│   │   │   └── resources/
│   │   └── test/
│   └── pom.xml
│
├── python-strategies/     # Python策略模块
│   ├── strategies/
│   │   ├── __init__.py
│   │   ├── base_strategy.py    # 策略基类
│   │   ├── ma_strategy.py      # 均线策略示例
│   │   └── ml_strategy.py      # 机器学习策略
│   ├── utils/
│   │   ├── data_loader.py
│   │   └── indicators.py
│   ├── requirements.txt
│   └── api_server.py           # Flask/FastAPI服务
│
├── electron-app/          # Electron前端应用
│   ├── main.js            # Electron主进程
│   ├── preload.js         # 预加载脚本
│   ├── index.html         # 主页面
│   ├── styles/
│   │   └── main.css       # 样式文件
│   ├── js/
│   │   ├── app.js         # 主应用逻辑
│   │   ├── websocket.js   # WebSocket管理
│   │   └── ui.js          # UI工具函数
│   └── package.json
│
├── config/                # 配置文件
│   ├── application.yml
│   └── strategy_config.json
│
└── docs/                  # 文档
    ├── architecture.md
    ├── api.md
    ├── websocket_usage.md
    ├── binance_api_implementation.md
    └── okx_api_implementation.md
```

## 快速开始

### 1. 数据库设置

创建MySQL数据库：

```sql
CREATE DATABASE quant_trading CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

配置数据库连接（修改 `java-backend/src/main/resources/application.yml`）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/quant_trading?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
```

详细说明请参考 [数据库设置指南](docs/database_setup.md)

### 2. Java后端启动

```bash
cd java-backend
mvn clean install
mvn spring-boot:run
```

服务将在 `http://localhost:8080` 启动

### 3. Python策略服务启动

```bash
cd python-strategies
pip install -r requirements.txt
python api_server.py
```

服务将在 `http://localhost:8000` 启动

### 4. Electron前端启动

```bash
cd electron-app
npm install
npm start
```

或者开发模式（自动打开开发者工具）：

```bash
npm run dev
```

### 5. 首次使用

1. 打开Electron应用
2. 在登录页面注册新用户或登录
3. 登录成功后进入主界面
4. 在"系统设置"中配置交易所API密钥

### 3. 配置用户交易所

```bash
# 设置OKX交易所
curl -X POST "http://localhost:8080/api/user/user123/exchange" \
  -d "exchangeType=OKX" \
  -d "apiKey=your_api_key" \
  -d "secretKey=your_secret_key" \
  -d "passphrase=your_passphrase"

# 设置Binance交易所
curl -X POST "http://localhost:8080/api/user/user123/exchange" \
  -d "exchangeType=BINANCE" \
  -d "apiKey=your_api_key" \
  -d "secretKey=your_secret_key"
```

### 4. 连接WebSocket获取实时数据

```javascript
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({userId: 'user123'}, function(frame) {
    // 订阅账户信息
    stompClient.subscribe('/user/user123/account', function(message) {
        const accountInfo = JSON.parse(message.body);
        console.log('账户更新:', accountInfo);
    });
});
```

详细使用说明请参考：
- [快速开始指南](docs/quick_start.md)
- [Electron前端使用说明](electron-app/README.md)
- [WebSocket使用指南](docs/websocket_usage.md)

## 核心功能

### 1. WebSocket实时推送
- ✅ 每个用户通过WebSocket实时获取账户和仓位数据
- ✅ 支持STOMP协议，自动推送更新
- ✅ 服务器主动推送，无需轮询

### 2. 多交易所支持
- ✅ 支持OKX（欧易）和Binance（币安）
- ✅ 用户可选择交易所
- ✅ 交易所适配器模式，易于扩展

### 3. 多种策略类型
- ✅ **普通策略**：技术指标、机器学习等传统策略
- ✅ **网格策略**：价格区间内自动低买高卖
- ✅ **双向策略**：同时持有多空仓位
- ✅ 策略工厂模式，易于扩展新策略

## 核心优势

1. **职责分离**: Java负责系统稳定性，Python负责策略灵活性
2. **技术选型最优**: 各语言发挥所长
3. **易于扩展**: 策略独立开发、测试、部署
4. **风险隔离**: Python策略异常不影响Java核心系统
5. **团队协作**: 后端团队用Java，量化团队用Python
6. **实时推送**: WebSocket实时推送账户和仓位数据
7. **多交易所**: 统一接口支持多个交易所

## 性能考虑

- **延迟**: RESTful API通常 < 10ms（同机部署）
- **吞吐**: 消息队列可支持 10,000+ TPS
- **资源**: Python进程独立，便于资源监控和限制

## 安全建议

- Python策略运行在受限环境
- 策略代码沙箱隔离
- 风控在Java层统一控制
- 策略权限分级管理

