# WebSocket使用指南

## 连接WebSocket

### JavaScript客户端示例

```javascript
// 使用SockJS和STOMP
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

// 连接
stompClient.connect({userId: 'user123'}, function(frame) {
    console.log('连接成功: ' + frame);
    
    // 订阅账户信息
    stompClient.subscribe('/user/user123/account', function(message) {
        const accountInfo = JSON.parse(message.body);
        console.log('账户更新:', accountInfo);
        // 更新UI显示账户余额、持仓等
    });
});

// 请求账户信息
stompClient.send('/app/account/request', {}, {});
```

### 账户信息数据结构

```json
{
  "userId": "user123",
  "totalBalance": 100000.00,
  "availableBalance": 80000.00,
  "frozenBalance": 20000.00,
  "equity": 102000.00,
  "unrealizedPnl": 2000.00,
  "positions": [
    {
      "symbol": "BTC-USDT",
      "side": "LONG",
      "quantity": 1.5,
      "available": 1.5,
      "avgPrice": 50000.00,
      "currentPrice": 51000.00,
      "unrealizedPnl": 1500.00,
      "pnlPercentage": 2.0,
      "leverage": 10,
      "margin": 7500.00
    }
  ],
  "timestamp": 1234567890123
}
```

## 设置交易所

### REST API

```bash
# 设置用户使用OKX交易所
curl -X POST "http://localhost:8080/api/user/user123/exchange" \
  -d "exchangeType=OKX" \
  -d "apiKey=your_api_key" \
  -d "secretKey=your_secret_key" \
  -d "passphrase=your_passphrase"

# 设置用户使用Binance交易所
curl -X POST "http://localhost:8080/api/user/user123/exchange" \
  -d "exchangeType=BINANCE" \
  -d "apiKey=your_api_key" \
  -d "secretKey=your_secret_key"
```

设置后，系统会自动：
1. 初始化交易所连接
2. 订阅账户和仓位更新
3. 通过WebSocket实时推送数据



