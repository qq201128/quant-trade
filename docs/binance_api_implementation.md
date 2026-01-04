# Binance API 实现说明

## 概述

根据 [Binance官方API文档](https://developers.binance.com/docs/binance-spot-api-docs/web-socket-streams) 实现的Binance交易所适配器。

## 核心组件

### 1. BinanceApiClient

REST API客户端，负责：
- 创建用户数据流listenKey
- 延长listenKey有效期（每30分钟）
- 获取账户信息（需要签名认证）
- 删除listenKey

**关键API端点：**
- `POST /api/v3/userDataStream` - 创建用户数据流
- `PUT /api/v3/userDataStream` - 延长listenKey
- `GET /api/v3/account` - 获取账户信息（需要签名）

### 2. BinanceWebSocketClient

WebSocket客户端，符合Binance规范：

#### 市场数据流

- **端点**: `wss://stream.binance.com:9443/ws/<streamName>`
- **流格式**: `<symbol>@<streamType>`
  - `btcusdt@ticker` - 24小时价格统计
  - `btcusdt@depth` - 订单簿深度
  - `btcusdt@trade` - 实时交易
  - `btcusdt@kline_1m` - K线数据

#### 用户数据流

- **端点**: `wss://stream.binance.com:9443/ws/<listenKey>`
- **流程**:
  1. 通过REST API获取listenKey
  2. 连接到用户数据流端点
  3. 接收账户更新和订单执行报告
  4. 每30分钟延长listenKey有效期

#### Ping/Pong处理

根据Binance规范：
- 服务器每20秒发送ping帧
- 客户端必须在1分钟内回复pong帧
- 未回复会导致连接断开

**实现**：`onWebsocketPing`方法自动回复pong

### 3. BinanceAdapter

交易所适配器实现，统一接口：
- `getAccountInfo()` - 通过REST API获取账户信息
- `subscribeAccountUpdates()` - 订阅用户数据流
- `subscribeMarketData()` - 订阅市场数据流

## 数据格式

### 账户更新消息 (outboundAccountPosition)

```json
{
  "e": "outboundAccountPosition",
  "E": 1564034571100,
  "u": 1564034571073,
  "B": [
    {
      "a": "USDT",
      "f": "1000.00000000",
      "l": "0.00000000"
    }
  ]
}
```

**字段说明：**
- `e`: 事件类型
- `E`: 事件时间
- `u`: 账户更新ID
- `B`: 余额数组
  - `a`: 资产名称
  - `f`: 可用余额
  - `l`: 冻结余额

### 订单执行报告 (executionReport)

```json
{
  "e": "executionReport",
  "E": 1499405658658,
  "s": "ETHBTC",
  "c": "mUvoqJxFIILMdfAW5iGSOW",
  "S": "BUY",
  "o": "LIMIT",
  "f": "GTC",
  "q": "1.00000000",
  "p": "0.10264410",
  "P": "0.00000000",
  "F": "0.00000000",
  "g": -1,
  "C": "",
  "x": "NEW",
  "X": "NEW",
  "r": "NONE",
  "i": 4293153,
  "l": "0.00000000",
  "z": "0.00000000",
  "L": "0.00000000",
  "n": "0",
  "N": null,
  "T": 1499405658657,
  "t": -1,
  "I": 8641984,
  "w": true,
  "m": false,
  "M": false,
  "O": 1499405658657,
  "Z": "0.00000000",
  "Y": "0.00000000",
  "Q": "0.00000000"
}
```

## 实现细节

### 签名认证

所有需要认证的REST API请求都需要签名：

```java
private String generateSignature(String data) {
    Mac mac = Mac.getInstance("HmacSHA256");
    SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");
    mac.init(secretKeySpec);
    byte[] hash = mac.doFinal(data.getBytes());
    return Base64.getEncoder().encodeToString(hash);
}
```

### ListenKey管理

- **创建**: 初始化用户数据流时创建
- **延长**: 每30分钟自动延长（避免60分钟过期）
- **删除**: 连接关闭时删除

### 错误处理

- **连接断开**: 自动重连（5秒后）
- **API错误**: 记录日志并返回错误
- **消息解析失败**: 记录日志，继续处理其他消息

## 使用示例

### 订阅账户更新

```java
BinanceAdapter adapter = ...;
adapter.initialize(apiKey, secretKey, null);

Flux<AccountInfo> accountUpdates = adapter.subscribeAccountUpdates(userId);
accountUpdates.subscribe(
    accountInfo -> {
        System.out.println("账户余额: " + accountInfo.getTotalBalance());
        System.out.println("可用余额: " + accountInfo.getAvailableBalance());
    },
    error -> System.err.println("错误: " + error.getMessage())
);
```

### 订阅市场数据

```java
Flux<Map<String, Object>> marketData = adapter.subscribeMarketData("BTCUSDT");
marketData.subscribe(
    data -> {
        System.out.println("当前价格: " + data.get("c"));
        System.out.println("24小时涨跌: " + data.get("P") + "%");
    }
);
```

## 注意事项

1. **Rate Limits**: 
   - WebSocket连接限制：每5分钟每IP最多300次连接尝试
   - 每连接最多订阅1024个流
   - 每秒最多5条控制消息（ping/pong/subscribe等）

2. **连接有效期**: 
   - 单个连接最多24小时有效
   - 需要定期重连

3. **ListenKey有效期**: 
   - 60分钟过期
   - 需要每30分钟延长一次

4. **符号格式**: 
   - 所有符号必须小写
   - 例如：`BTCUSDT` → `btcusdt`

5. **时区**: 
   - 所有时间戳为毫秒
   - 可通过`timeUnit=MICROSECOND`参数获取微秒精度

## 参考文档

- [Binance WebSocket Streams](https://developers.binance.com/docs/binance-spot-api-docs/web-socket-streams)
- [Binance User Data Stream](https://developers.binance.com/docs/binance-spot-api-docs/user-data-stream)
- [Binance REST API](https://developers.binance.com/docs/binance-spot-api-docs/rest-api)



