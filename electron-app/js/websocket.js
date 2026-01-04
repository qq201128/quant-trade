// WebSocket连接管理
class WebSocketManager {
    constructor(backendUrl, userId) {
        this.backendUrl = backendUrl;
        this.userId = userId;
        this.socket = null;
        this.stompClient = null;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
        this.reconnectDelay = 3000;
    }
    
    connect() {
        try {
            // 在URL中添加userId参数，用于WebSocket握手认证
            const wsUrl = `${this.backendUrl}/ws?userId=${encodeURIComponent(this.userId)}`;
            this.socket = new SockJS(wsUrl);
            this.stompClient = Stomp.over(this.socket);
            
            // 禁用调试日志
            this.stompClient.debug = () => {};
            
            this.stompClient.connect(
                { userId: this.userId },
                () => {
                    console.log('WebSocket连接成功');
                    this.reconnectAttempts = 0;
                    this.onConnected();
                },
                (error) => {
                    console.error('WebSocket连接失败:', error);
                    this.onDisconnected();
                    this.scheduleReconnect();
                }
            );
        } catch (error) {
            console.error('WebSocket连接错误:', error);
            this.scheduleReconnect();
        }
    }
    
    onConnected() {
        // 订阅账户信息
        this.stompClient.subscribe(`/user/${this.userId}/account`, (message) => {
            const accountInfo = JSON.parse(message.body);
            if (window.app) {
                window.app.updateAccountInfo(accountInfo);
            }
        });
        
        // 请求当前账户信息
        this.requestAccountInfo();
    }
    
    onDisconnected() {
        if (window.app) {
            window.app.updateConnectionStatus(false);
        }
    }
    
    scheduleReconnect() {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            setTimeout(() => {
                console.log(`尝试重连 (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);
                this.connect();
            }, this.reconnectDelay);
        } else {
            console.error('达到最大重连次数，停止重连');
        }
    }
    
    requestAccountInfo() {
        if (this.stompClient && this.stompClient.connected) {
            this.stompClient.send('/app/account/request', {}, {});
        }
    }
    
    disconnect() {
        if (this.stompClient) {
            this.stompClient.disconnect();
        }
        if (this.socket) {
            this.socket.close();
        }
    }
}

// 导出到全局
window.WebSocketManager = WebSocketManager;

