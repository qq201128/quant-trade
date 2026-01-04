# OKX API 实现说明

## 概述

根据 [OKX官方API文档](https://www.okx.com/docs-v5/en/#overview-websocket) 实现的OKX交易所适配器。

## 核心组件

### 1. OkxApiClient

REST API客户端，负责：
- 获取账户余额
- 获取持仓信息
- 下单操作
- 签名认证（HMAC SHA256）

**关键API端点：**
- `GET /api/v5/account/balance` - 获取账户余额
- `GET /api/v5/account/positions` - 获取持仓信息
- `POST /api/v5/trade/order` - 下单

**认证方式：**
- `OK-ACCESS-KEY`: API Key
- `OK-ACCESS-SIGN`: 签名
- `OK-ACCESS-TIMESTAMP`: ISO 8601格式时间戳
- `OK-ACCESS-PASSPHRASE`: Passphrase

### 2. OkxWebSocketClient

WebSocket客户端，符合OKX规范：

#### 公共频道

- **端点**: `wss://ws.okx.com:8443/ws/v5/public`
- **用途**: 市场数据（ticker、depth、trade等）
- **无需认证**: 直接连接即可订阅

#### 私有频道

- **端点**: `wss://ws.okx.com:8443/ws/v5/private`
- **用途**: 账户信息、持仓、订单等
- **需要认证**: 先登录，再订阅频道

#### 登录流程

1. 连接私有频道WebSocket
2. 发送登录消息：
```json
{
  "op": "login",
  "args": [{
    "apiKey": "your_api_key",
    "passphrase": "your_passphrase",
    "timestamp": "1627384800",
    "sign": "signature"
  }]
}
```

3. 收到登录成功响应后，订阅频道：
```json
{
  "op": "subscribe",
  "args": [{
    "channel": "account",
    "ccy": "USDT"
  }]
}
```

#### Ping/Pong保持连接

- **规则**: 如果30秒内没有收到消息，发送字符串 `"ping"`
- **响应**: 服务器返回字符串 `"pong"`
- **实现**: 定时器每30秒检查，如果超时则发送ping

### 3. OkxAdapter

交易所适配器实现，统一接口：
- `getAccountInfo()` - 通过REST API获取账户信息
- `subscribeAccountUpdates()` - 订阅用户数据流
- `subscribeMarketData()` - 订阅市场数据流

## 数据格式

### 账户更新消息 (account channel)

```json
{
  "arg": {
    "channel": "account",
    "ccy": "USDT"
  },
  "data": [{
    "ccy": "USDT",
    "eq": "100000.00",
    "cashBal": "100000.00",
    "uTime": "1627384800000",
    "isoEq": "0",
    "availEq": "80000.00",
    "disEq": "100000.00",
    "availBal": "80000.00",
    "frozenBal": "20000.00",
    "ordFrozen": "20000.00",
    "liab": "0",
    "upl": "0",
    "uplLiab": "0",
    "crossLiab": "0",
    "isoLiab": "0",
    "mgnRatio": "0",
    "interest": "0",
    "twap": "0",
    "maxLoan": "0",
    "eqUsd": "100000.00",
    "notionalLever": "0",
    "stgyEq": "0",
    "isoUpl": "0",
    "details": [{
      "ccy": "USDT",
      "eq": "100000.00",
      "cashBal": "100000.00",
      "uTime": "1627384800000",
      "isoEq": "0",
      "availEq": "80000.00",
      "disEq": "100000.00",
      "availBal": "80000.00",
      "frozenBal": "20000.00"
    }]
  }]
}
```

**字段说明：**
- `eq`: 总权益
- `availEq`: 可用权益
- `availBal`: 可用余额
- `frozenBal`: 冻结余额
- `details`: 各币种余额详情

### 持仓更新消息 (positions channel)

```json
{
  "arg": {
    "channel": "positions",
    "instType": "SWAP"
  },
  "data": [{
    "instId": "BTC-USDT-SWAP",
    "posId": "123456789",
    "tradeId": "987654321",
    "instType": "SWAP",
    "mgnMode": "isolated",
    "posSide": "long",
    "pos": "1.5",
    "ccy": "USDT",
    "posCcy": "BTC",
    "availPos": "1.5",
    "avgPx": "50000.00",
    "markPx": "51000.00",
    "upl": "1500.00",
    "uplRatio": "0.02",
    "lever": "10",
    "liqPx": "45000.00",
    "imr": "7500.00",
    "margin": "7500.00",
    "mgnRatio": "0.15",
    "mmr": "500.00",
    "liab": "0",
    "liabCcy": "USDT",
    "interest": "0",
    "notionalUsd": "76500.00",
    "optVal": "0",
    "adl": "1",
    "ccy": "USDT",
    "last": "51000.00",
    "idxPx": "51000.00",
    "usdPx": "51000.00",
    "cTime": "1627384800000",
    "uTime": "1627384800000"
  }]
}
```

## 实现细节

### 签名生成

OKX使用HMAC SHA256签名：

```java
String message = timestamp + method + requestPath + (body != null ? body : "");
Mac mac = Mac.getInstance("HmacSHA256");
SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");
mac.init(secretKeySpec);
byte[] hash = mac.doFinal(message.getBytes());
String sign = Base64.getEncoder().encodeToString(hash);
```

### 时间戳格式

- **REST API**: ISO 8601格式（如：`2020-12-08T09:08:57.715Z`）
- **WebSocket登录**: Unix时间戳（秒）

### 连接管理

- **连接限制**: 每秒最多3次连接请求（基于IP）
- **订阅限制**: 每个连接最多480次订阅/取消订阅/登录请求/小时
- **自动重连**: 连接断开后5秒自动重连
- **Ping/Pong**: 30秒无消息则发送ping保持连接

## 使用示例

### 订阅账户更新

```java
OkxAdapter adapter = ...;
adapter.initialize(apiKey, secretKey, passphrase);

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
Flux<Map<String, Object>> marketData = adapter.subscribeMarketData("BTC-USDT-SWAP");
marketData.subscribe(
    data -> {
        Map<String, Object> arg = (Map<String, Object>) data.get("arg");
        if ("tickers".equals(arg.get("channel"))) {
            List<Map<String, Object>> tickers = (List<Map<String, Object>>) data.get("data");
            // 处理ticker数据
        }
    }
);
```

## 注意事项

1. **Rate Limits**: 
   - 每秒最多3次连接请求
   - 每个连接最多480次订阅/取消订阅/登录请求/小时

2. **连接保持**: 
   - 30秒内无消息需发送ping
   - 未收到pong响应需重连

3. **认证**: 
   - 私有频道必须先登录
   - 登录成功后才能订阅账户/持仓频道

4. **符号格式**: 
   - 现货：`BTC-USDT`
   - 合约：`BTC-USDT-SWAP`
   - 期权：`BTC-USDT-240329-50000-C`

5. **时间戳**: 
   - REST API使用ISO 8601格式
   - WebSocket登录使用Unix时间戳（秒）

## 参考文档

- [OKX WebSocket API](https://www.okx.com/docs-v5/en/#websocket-api)
- [OKX REST API](https://www.okx.com/docs-v5/en/#rest-api)
- [OKX 账户频道](https://www.okx.com/docs-v5/en/#websocket-api-account-channel)
- [OKX 持仓频道](https://www.okx.com/docs-v5/en/#websocket-api-positions-channel)



