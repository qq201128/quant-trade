// 主应用逻辑
class TradingApp {
    constructor() {
        this.currentPage = 'login';
        this.backendUrl = 'http://localhost:8080';
        this.userId = null;
        this.username = null;
        this.token = null;
        this.exchangeType = '';
        this.stompClient = null;
        this.socket = null;
        this.refreshInterval = null;  // 定时刷新器
        this.isWebSocketConnected = false;  // WebSocket连接状态
        this.equityChart = null;  // 账户权益曲线图表
        this.positionChart = null;  // 持仓分布图表
        this.equityHistory = [];  // 权益历史数据
        
        this.init();
    }
    
    async init() {
        // 从Electron主进程获取后端服务地址
        if (window.electronAPI && window.electronAPI.getBackendUrl) {
            try {
                this.backendUrl = await window.electronAPI.getBackendUrl();
                console.log('从主进程获取后端地址:', this.backendUrl);
            } catch (error) {
                console.error('获取后端地址失败，使用默认值:', error);
                this.backendUrl = 'http://localhost:8080';
            }
        }
        
        // 加载配置和Token
        await this.loadConfig();
        
        // 检查是否已登录
        if (this.token && this.userId) {
            // 验证Token是否有效
            if (await this.validateToken()) {
                this.showMainApp();
            } else {
                this.showLogin();
            }
        } else {
            this.showLogin();
        }
        
        // 初始化UI
        this.initAuthForms();
        this.initNavigation();
        this.initExchangeSelector();
        this.initSettings();
        this.initLogout();
        this.initCharts();  // 初始化图表
        this.initStrategies();  // 初始化策略配置
    }
    
    initLogout() {
        const logoutBtn = document.getElementById('logoutBtn');
        if (logoutBtn) {
            logoutBtn.addEventListener('click', () => {
                this.logout();
            });
        }
    }
    
    logout() {
        // 清除Token和用户信息
        this.token = null;
        this.userId = null;
        this.username = null;
        this.saveConfig();
        
        // 断开WebSocket
        if (this.stompClient) {
            this.stompClient.disconnect();
            this.stompClient = null;
        }
        
        // 更新UI
        if (document.getElementById('userId')) {
            document.getElementById('userId').textContent = '未登录';
        }
        if (document.getElementById('logoutBtn')) {
            document.getElementById('logoutBtn').style.display = 'none';
        }
        
        // 显示登录页面
        this.showLogin();
        
        // 清空表单
        document.getElementById('loginForm').reset();
        document.getElementById('registerForm').reset();
        document.getElementById('loginError').textContent = '';
        document.getElementById('registerError').textContent = '';
    }
    
    showLogin() {
        // 显示登录页面，隐藏主应用
        document.getElementById('authPage').style.display = 'flex';
        document.getElementById('appContainer').style.display = 'none';
        this.currentPage = 'login';
    }
    
    showMainApp() {
        // 隐藏登录页面，显示主应用
        document.getElementById('authPage').style.display = 'none';
        document.getElementById('appContainer').style.display = 'flex';
        
        // 确保仪表盘页面显示
        document.querySelectorAll('.page').forEach(page => {
            page.classList.remove('active');
        });
        document.getElementById('dashboardPage').classList.add('active');
        
        // 更新导航状态
        document.querySelectorAll('.nav-item').forEach(item => {
            item.classList.remove('active');
            if (item.dataset.page === 'dashboard') {
                item.classList.add('active');
            }
        });
        
        this.currentPage = 'dashboard';
        
            // 更新用户信息显示
            this.updateUserInfo();

            // 加载用户详细信息
            this.loadUserInfo();

            // 连接WebSocket（仅通过WebSocket获取数据，不使用REST API）
            this.connectWebSocket();

            // 不设置定时刷新，完全依赖WebSocket推送
            // WebSocket会自动推送更新，后端定时刷新会持续推送数据
    }
    
    async loadConfig() {
        // 从localStorage加载配置（后端服务地址由主进程管理，不从localStorage加载）
        const config = localStorage.getItem('tradingConfig');
        if (config) {
            const parsed = JSON.parse(config);
            // 不加载backendUrl，使用从主进程获取的值
            this.userId = parsed.userId || null;
            this.username = parsed.username || null;
            this.token = parsed.token || null;
            this.exchangeType = parsed.exchangeType || '';
            
            // 如果已登录，加载用户信息
            if (this.token && this.userId) {
                this.loadUserInfo();
            }
            if (this.exchangeType) {
                const exchangeSelect = document.getElementById('exchangeSelect');
                const settingsExchangeSelect = document.getElementById('settingsExchangeSelect');
                if (exchangeSelect) exchangeSelect.value = this.exchangeType;
                if (settingsExchangeSelect) settingsExchangeSelect.value = this.exchangeType;
            }
        }
    }
    
    saveConfig() {
        const config = {
            // 后端服务地址由主进程管理，不需要保存到本地配置
            userId: this.userId,
            username: this.username,
            token: this.token,
            exchangeType: this.exchangeType
        };
        localStorage.setItem('tradingConfig', JSON.stringify(config));
    }
    
    async validateToken() {
        if (!this.token || !this.userId) {
            return false;
        }
        
        try {
            const response = await fetch(`${this.backendUrl}/api/user/${this.userId}`, {
                headers: {
                    'Authorization': `Bearer ${this.token}`
                }
            });
            return response.ok;
        } catch (error) {
            return false;
        }
    }
    
    initAuthForms() {
        // 登录/注册标签切换
        document.querySelectorAll('.tab-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const tab = btn.dataset.tab;
                document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                
                if (tab === 'login') {
                    document.getElementById('loginForm').style.display = 'block';
                    document.getElementById('registerForm').style.display = 'none';
                } else {
                    document.getElementById('loginForm').style.display = 'none';
                    document.getElementById('registerForm').style.display = 'block';
                }
            });
        });
        
        // 登录表单
        document.getElementById('loginForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            await this.handleLogin();
        });
        
        // 注册表单
        document.getElementById('registerForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            await this.handleRegister();
        });
    }
    
    async handleLogin() {
        const username = document.getElementById('loginUsername').value;
        const password = document.getElementById('loginPassword').value;
        const errorDiv = document.getElementById('loginError');
        errorDiv.textContent = '';
        
        try {
            const response = await fetch(`${this.backendUrl}/api/auth/login`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ username, password })
            });
            
            if (response.ok) {
                const data = await response.json();
                this.token = data.token;
                this.userId = data.userId;
                this.username = data.username;
                this.saveConfig();
                
                // 更新UI
                this.updateUserInfo();
                
                // 显示主应用
                this.showMainApp();
            } else {
                errorDiv.textContent = '用户名或密码错误';
            }
        } catch (error) {
            errorDiv.textContent = '登录失败: ' + error.message;
        }
    }
    
    async handleRegister() {
        const username = document.getElementById('registerUsername').value;
        const password = document.getElementById('registerPassword').value;
        const email = document.getElementById('registerEmail').value;
        const phone = document.getElementById('registerPhone').value;
        const errorDiv = document.getElementById('registerError');
        errorDiv.textContent = '';
        
        try {
            const response = await fetch(`${this.backendUrl}/api/auth/register`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ username, password, email, phone })
            });
            
            if (response.ok) {
                const data = await response.json();
                this.token = data.token;
                this.userId = data.userId;
                this.username = data.username;
                this.saveConfig();
                
                // 更新UI
                this.updateUserInfo();
                
                // 显示主应用
                this.showMainApp();
            } else {
                const errorData = await response.json().catch(() => ({}));
                errorDiv.textContent = errorData.message || '注册失败';
            }
        } catch (error) {
            errorDiv.textContent = '注册失败: ' + error.message;
        }
    }
    
    updateUserInfo() {
        if (document.getElementById('userId')) {
            document.getElementById('userId').textContent = `用户: ${this.username || '未登录'}`;
        }
        if (document.getElementById('logoutBtn')) {
            document.getElementById('logoutBtn').style.display = 'inline-flex';
        }
    }
    
    /**
     * 从后端加载用户详细信息
     */
    async loadUserInfo() {
        if (!this.token || !this.userId) {
            console.log('无法加载用户信息: token或userId为空');
            return;
        }
        
        try {
            console.log('开始加载用户信息:', this.userId);
            const response = await fetch(`${this.backendUrl}/api/user/${this.userId}`, {
                headers: {
                    'Authorization': `Bearer ${this.token}`
                }
            });
            
            if (response.ok) {
                const userInfo = await response.json();
                console.log('用户信息加载成功:', userInfo);
                
                // 更新用户信息显示（注意字段名可能不同）
                const usernameEl = document.getElementById('usernameInput');
                const emailEl = document.getElementById('emailInput');
                const phoneEl = document.getElementById('phoneInput');
                const createdAtEl = document.getElementById('createdAtInput');
                
                if (usernameEl) {
                    usernameEl.value = userInfo.username || '';
                }
                if (emailEl) {
                    emailEl.value = userInfo.email || '';
                }
                if (phoneEl) {
                    phoneEl.value = userInfo.phone || '';
                }
                if (createdAtEl) {
                    if (userInfo.createdAt || userInfo.created_at) {
                        const dateStr = userInfo.createdAt || userInfo.created_at;
                        try {
                            const date = new Date(dateStr);
                            if (!isNaN(date.getTime())) {
                                createdAtEl.value = date.toLocaleString('zh-CN', {
                                    year: 'numeric',
                                    month: '2-digit',
                                    day: '2-digit',
                                    hour: '2-digit',
                                    minute: '2-digit'
                                });
                            } else {
                                createdAtEl.value = dateStr || '';
                            }
                        } catch (e) {
                            createdAtEl.value = dateStr || '';
                        }
                    } else {
                        createdAtEl.value = '';
                    }
                }
                
                // 更新交易所选择
                if (userInfo.exchangeType || userInfo.exchange_type) {
                    const exchangeType = userInfo.exchangeType || userInfo.exchange_type;
                    this.exchangeType = exchangeType;
                    const exchangeSelect = document.getElementById('exchangeSelect');
                    const settingsExchangeSelect = document.getElementById('settingsExchangeSelect');
                    if (exchangeSelect) exchangeSelect.value = exchangeType;
                    if (settingsExchangeSelect) {
                        settingsExchangeSelect.value = exchangeType;
                        // 如果当前在设置页面，加载该交易所的配置
                        if (this.currentPage === 'settings') {
                            this.loadExchangeConfig(exchangeType);
                        }
                    }
                }
            } else {
                const errorText = await response.text();
                console.error('加载用户信息失败:', response.status, errorText);
                // 显示错误提示
                if (window.UIUtils) {
                    UIUtils.showNotification('加载用户信息失败', 'error');
                }
            }
        } catch (error) {
            console.error('加载用户信息异常:', error);
            if (window.UIUtils) {
                UIUtils.showNotification('加载用户信息失败: ' + error.message, 'error');
            }
        }
    }
    
    initNavigation() {
        const navItems = document.querySelectorAll('.nav-item');
        navItems.forEach(item => {
            item.addEventListener('click', (e) => {
                e.preventDefault();
                const page = item.dataset.page;
                this.switchPage(page);
            });
        });
    }
    
    switchPage(page) {
        // 更新导航状态
        document.querySelectorAll('.nav-item').forEach(item => {
            item.classList.remove('active');
            if (item.dataset.page === page) {
                item.classList.add('active');
            }
        });
        
        // 更新页面显示
        document.querySelectorAll('.page').forEach(p => {
            p.classList.remove('active');
        });
        document.getElementById(page + 'Page').classList.add('active');
        
        // 更新标题
        const titles = {
            dashboard: '仪表盘',
            account: '账户信息',
            positions: '持仓管理',
            strategies: '策略配置',
            settings: '系统设置'
        };
        document.getElementById('pageTitle').textContent = titles[page] || '仪表盘';
        
        this.currentPage = page;
        
        // 页面切换时不主动刷新数据，完全依赖WebSocket推送
        // 如果需要刷新，可以通过WebSocket请求
        if (page === 'dashboard' || page === 'account' || page === 'positions') {
            // 如果WebSocket已连接，通过WebSocket请求数据
            if (this.stompClient && this.stompClient.connected && this.isWebSocketConnected) {
                this.stompClient.send('/app/account/request', {}, {});
                console.log('页面切换，通过WebSocket请求账户信息');
            } else {
                console.warn('WebSocket未连接，无法刷新数据');
            }
        }
        
        // 切换到设置页面时，重新加载用户信息和交易所配置
        if (page === 'settings') {
            this.loadUserInfo();
            // 如果有当前交易所，加载其配置
            if (this.exchangeType) {
                const settingsExchangeSelect = document.getElementById('settingsExchangeSelect');
                if (settingsExchangeSelect) {
                    settingsExchangeSelect.value = this.exchangeType;
                }
                this.loadExchangeConfig(this.exchangeType);
            }
        }
        
        // 切换到策略页面时，重新初始化策略按钮（确保动态添加的按钮也能绑定事件）
        if (page === 'strategies') {
            // 延迟初始化，确保DOM已完全渲染
            setTimeout(() => {
                this.initStrategies();
            }, 100);
        }
    }
    
    initExchangeSelector() {
        const selector = document.getElementById('exchangeSelect');
        if (selector) {
            selector.addEventListener('change', async (e) => {
                const newExchangeType = e.target.value;
                if (newExchangeType) {
                    // 切换交易所时，加载对应的配置
                    await this.loadExchangeConfig(newExchangeType);
                    this.exchangeType = newExchangeType;
                    this.saveConfig();
                    // 重新连接WebSocket
                    this.connectWebSocket();
                }
            });
        }
        
        // 设置页面的交易所选择器
        const settingsExchangeSelect = document.getElementById('settingsExchangeSelect');
        if (settingsExchangeSelect) {
            settingsExchangeSelect.addEventListener('change', async (e) => {
                const exchangeType = e.target.value;
                // 切换时加载对应交易所的配置
                if (exchangeType) {
                    await this.loadExchangeConfig(exchangeType);
                } else {
                    // 清空输入框
                    document.getElementById('apiKeyInput').value = '';
                    document.getElementById('secretKeyInput').value = '';
                    document.getElementById('passphraseInput').value = '';
                    document.getElementById('passphraseGroup').style.display = 'none';
                }
            });
        }
    }
    
    /**
     * 显示配置提示信息
     */
    showConfigHint(message) {
        // 移除旧的提示
        this.hideConfigHint();
        
        // 创建提示元素
        const hint = document.createElement('div');
        hint.id = 'configHint';
        hint.className = 'config-hint';
        hint.textContent = message;
        hint.style.cssText = 'margin-top: 10px; padding: 8px; background-color: #e3f2fd; color: #1976d2; border-radius: 4px; font-size: 14px;';
        
        // 插入到表单中
        const form = document.getElementById('exchangeConfigForm');
        if (form) {
            form.appendChild(hint);
        }
    }
    
    /**
     * 隐藏配置提示信息
     */
    hideConfigHint() {
        const hint = document.getElementById('configHint');
        if (hint) {
            hint.remove();
        }
    }
    
    /**
     * 加载指定交易所的配置
     */
    async loadExchangeConfig(exchangeType) {
        if (!this.token || !this.userId || !exchangeType) {
            return;
        }
        
        try {
            const response = await fetch(
                `${this.backendUrl}/api/user/${this.userId}/exchange/${exchangeType}`,
                {
                    headers: {
                        'Authorization': `Bearer ${this.token}`
                    }
                }
            );
            
            if (response.ok) {
                const config = await response.json();
                
                // 如果有配置，显示掩码的API Key和提示
                if (config.hasConfig) {
                    // 显示掩码的API Key（让用户知道已有配置）
                    const apiKeyInput = document.getElementById('apiKeyInput');
                    if (apiKeyInput && config.apiKey) {
                        apiKeyInput.value = config.apiKey;  // 显示掩码的API Key（如：abcd****1234）
                        apiKeyInput.placeholder = '已有配置（显示掩码）';
                    }
                    
                    // Secret Key和Passphrase不显示，但添加提示
                    const secretKeyInput = document.getElementById('secretKeyInput');
                    if (secretKeyInput) {
                        secretKeyInput.value = '';
                        secretKeyInput.placeholder = '已有配置（输入新值可更新）';
                    }
                    
                    const passphraseInput = document.getElementById('passphraseInput');
                    if (passphraseInput) {
                        passphraseInput.value = '';
                        passphraseInput.placeholder = '已有配置（输入新值可更新）';
                    }
                    
                    // 显示提示信息
                    this.showConfigHint('该交易所已有配置，输入新值可更新配置');
                } else {
                    // 没有配置，清空输入框
                    const apiKeyInput = document.getElementById('apiKeyInput');
                    const secretKeyInput = document.getElementById('secretKeyInput');
                    const passphraseInput = document.getElementById('passphraseInput');
                    
                    if (apiKeyInput) {
                        apiKeyInput.value = '';
                        apiKeyInput.placeholder = '请输入API Key';
                    }
                    if (secretKeyInput) {
                        secretKeyInput.value = '';
                        secretKeyInput.placeholder = '请输入Secret Key';
                    }
                    if (passphraseInput) {
                        passphraseInput.value = '';
                        passphraseInput.placeholder = '请输入Passphrase';
                    }
                    
                    // 隐藏提示
                    this.hideConfigHint();
                }
                
                // 根据交易所类型显示/隐藏Passphrase输入框
                if (exchangeType === 'OKX') {
                    document.getElementById('passphraseGroup').style.display = 'block';
                } else {
                    document.getElementById('passphraseGroup').style.display = 'none';
                }
            }
        } catch (error) {
            console.error('加载交易所配置失败:', error);
        }
    }
    
    initSettings() {
        const form = document.getElementById('exchangeConfigForm');
        form.addEventListener('submit', (e) => {
            e.preventDefault();
            this.saveExchangeConfig();
        });
        
        // 初始化时加载当前交易所的配置
        if (this.exchangeType) {
            this.loadExchangeConfig(this.exchangeType).then(() => {
                // 设置选择器值
                const settingsExchangeSelect = document.getElementById('settingsExchangeSelect');
                if (settingsExchangeSelect) {
                    settingsExchangeSelect.value = this.exchangeType;
                }
            });
        }
        
        // 刷新按钮（仅通过WebSocket）
        document.getElementById('refreshAccountBtn')?.addEventListener('click', () => {
            if (this.stompClient && this.stompClient.connected && this.isWebSocketConnected) {
                this.stompClient.send('/app/account/request', {}, {});
                console.log('手动刷新：通过WebSocket请求账户信息');
            } else {
                alert('WebSocket未连接，无法刷新数据');
            }
        });

        document.getElementById('refreshPositionsBtn')?.addEventListener('click', () => {
            // 持仓数据通过WebSocket推送，通过WebSocket请求刷新
            if (this.stompClient && this.stompClient.connected && this.isWebSocketConnected) {
                this.stompClient.send('/app/account/request', {}, {});
                console.log('手动刷新持仓：通过WebSocket请求账户信息');
            } else {
                alert('WebSocket未连接，无法刷新数据');
            }
        });
    }
    
    async saveExchangeConfig() {
        const exchangeType = document.getElementById('settingsExchangeSelect').value;
        const apiKey = document.getElementById('apiKeyInput').value;
        const secretKey = document.getElementById('secretKeyInput').value;
        const passphrase = document.getElementById('passphraseInput').value;
        
        if (!exchangeType || !apiKey || !secretKey) {
            alert('请填写完整的交易所配置信息');
            return;
        }
        
        if (!this.userId || !this.token) {
            alert('请先登录');
            return;
        }
        
        try {
            // 调用后端API设置交易所（使用从主进程获取的后端地址）
            const response = await fetch(`${this.backendUrl}/api/user/${this.userId}/exchange`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'Authorization': `Bearer ${this.token}`
                },
                body: new URLSearchParams({
                    exchangeType,
                    apiKey,
                    secretKey,
                    passphrase: passphrase || ''
                })
            });
            
            if (response.ok) {
                this.exchangeType = exchangeType;
                this.saveConfig();
                
                // 更新顶部选择器
                document.getElementById('exchangeSelect').value = exchangeType;
                
                alert('配置保存成功！');
                
                // 重新加载用户信息
                await this.loadUserInfo();
                
                // 重新加载交易所配置（显示掩码的API Key）
                await this.loadExchangeConfig(exchangeType);
                
                // 重新连接WebSocket
                this.connectWebSocket();
            } else {
                const errorData = await response.json().catch(() => ({}));
                alert('配置保存失败: ' + (errorData.message || '请检查后端服务是否运行'));
            }
        } catch (error) {
            console.error('保存配置失败:', error);
            alert('配置保存失败: ' + error.message);
        }
    }
    
    connectWebSocket() {
        if (!this.userId || !this.backendUrl || !this.token) {
            console.warn('WebSocket连接跳过: 缺少必要参数', { userId: !!this.userId, backendUrl: !!this.backendUrl, token: !!this.token });
            return;
        }

        // 先验证 Token 是否有效
        this.validateToken().then(isValid => {
            if (!isValid) {
                console.warn('Token已过期，跳转到登录页面');
                this.logout();
                return;
            }
            this.doConnectWebSocket();
        });
    }

    doConnectWebSocket() {
        // 断开旧连接
        if (this.stompClient) {
            this.stompClient.disconnect();
        }
        
        try {
            // 在URL中添加token参数，用于WebSocket握手认证
            const wsUrl = `${this.backendUrl}/ws?token=${encodeURIComponent(this.token)}`;
            const socket = new SockJS(wsUrl);
            this.stompClient = Stomp.over(socket);
            
            // 禁用STOMP调试日志（避免控制台打印 <<< MESSAGE 等日志）
            this.stompClient.debug = () => {};
            
            // 监听 SockJS 底层连接事件，用于检测握手失败
            let handshakeTimeout = null;
            let connectionEstablished = false;
            
            // 设置握手超时（5秒）
            handshakeTimeout = setTimeout(() => {
                if (!connectionEstablished) {
                    console.error('WebSocket握手超时，可能是认证失败');
                    this.isWebSocketConnected = false;
                    this.updateConnectionStatus(false);
                    
                    // 检查Token是否有效
                    this.validateToken().then(isValid => {
                        if (!isValid) {
                            console.warn('Token验证失败，跳转到登录页面');
                            this.logout();
                        } else {
                            // Token有效但握手失败，可能是其他原因，尝试重新连接
                            console.warn('WebSocket握手失败，但Token有效，将在3秒后重试');
                            setTimeout(() => {
                                this.connectWebSocket();
                            }, 3000);
                        }
                    });
                }
            }, 5000);
            
            // 监听 SockJS 连接关闭事件（包括握手失败）
            socket.onclose = (event) => {
                clearTimeout(handshakeTimeout);
                
                // 如果连接还未建立就关闭了，可能是握手失败
                if (!connectionEstablished) {
                    console.error('WebSocket握手失败，连接被关闭:', event);
                    this.isWebSocketConnected = false;
                    this.updateConnectionStatus(false);
                    
                    // 检查是否是认证失败（HTTP 403 或 401）
                    if (event.code === 403 || event.code === 401 || event.code === 1008) {
                        console.warn('WebSocket认证失败（HTTP ' + event.code + '），Token可能已过期或无效');
                        // 验证Token并决定是否重新登录
                        this.validateToken().then(isValid => {
                            if (!isValid) {
                                console.warn('Token验证失败，跳转到登录页面');
                                this.logout();
                            } else {
                                // Token有效但认证失败，可能是密钥不匹配，清除Token并重新登录
                                console.warn('Token有效但认证失败，可能是密钥不匹配，需要重新登录');
                                this.logout();
                            }
                        });
                    } else {
                        // 其他原因导致的连接失败，尝试重新连接
                        console.warn('WebSocket连接失败（错误码: ' + event.code + '），将在3秒后重试');
                        setTimeout(() => {
                            this.connectWebSocket();
                        }, 3000);
                    }
                } else {
                    // 连接已建立后关闭
                    console.log('WebSocket连接已断开');
                    this.isWebSocketConnected = false;
                    this.updateConnectionStatus(false);
                }
            };
            
            // 监听 SockJS 错误事件
            socket.onerror = (error) => {
                console.error('WebSocket连接错误:', error);
                clearTimeout(handshakeTimeout);
                this.isWebSocketConnected = false;
                this.updateConnectionStatus(false);
                
                // 检查是否是认证相关错误
                if (error && (error.toString().includes('403') || error.toString().includes('401') || 
                    error.toString().includes('认证') || error.toString().includes('auth'))) {
                    console.warn('WebSocket认证失败，Token可能已过期或无效');
                    this.validateToken().then(isValid => {
                        if (!isValid) {
                            console.warn('Token验证失败，跳转到登录页面');
                            this.logout();
                        } else {
                            console.warn('Token有效但认证失败，需要重新登录');
                            this.logout();
                        }
                    });
                }
            };
            
            this.stompClient.connect(
                { 
                    userId: this.userId,
                    Authorization: `Bearer ${this.token}`
                },
                (frame) => {
                    // 连接成功，清除超时
                    clearTimeout(handshakeTimeout);
                    connectionEstablished = true;
                    
                    // console.log('========== WebSocket连接成功 ==========');
                    // console.log('连接帧信息:', frame);
                    // console.log('用户ID:', this.userId);
                    this.isWebSocketConnected = true;
                    this.updateConnectionStatus(true);
                    
                    // 订阅账户信息
                    const subscribePath = `/user/${this.userId}/account`;
                    // console.log('准备订阅路径:', subscribePath);
                    
                    const subscription = this.stompClient.subscribe(subscribePath, (message) => {
                        try {
                            // console.log('========== 收到WebSocket消息 ==========');
                            // console.log('消息头:', message.headers);
                            // console.log('消息体长度:', message.body ? message.body.length : 0);
                            // console.log('消息体前200字符:', message.body ? message.body.substring(0, 200) : 'null');
                            
                            const accountInfo = JSON.parse(message.body);
                            // console.log('========== 收到WebSocket账户更新 ==========');
                            // console.log('账户信息:', {
                            //     totalBalance: accountInfo.totalBalance,
                            //     positionsCount: accountInfo.positions ? accountInfo.positions.length : 0,
                            //     positions: accountInfo.positions
                            // });
                            this.updateAccountInfo(accountInfo);
                            // console.log('========== 账户信息更新完成 ==========');
                        } catch (error) {
                            console.error('解析WebSocket消息失败:', error);
                            console.error('原始消息体:', message.body);
                        }
                    });
                    
                    // console.log('========== WebSocket订阅已建立 ==========');
                    // console.log('订阅ID:', subscription.id);
                    // console.log('订阅路径:', subscribePath);
                    // console.log('订阅对象:', subscription);
                    
                    // // 请求当前账户信息（只请求一次，后续通过WebSocket推送）
                    // console.log('发送账户信息请求到: /app/account/request');
                    this.stompClient.send('/app/account/request', {}, {});
                    // console.log('账户信息请求已发送');
                    
                    // 监听WebSocket断开事件（连接建立后的断开）
                    this.stompClient.ws.onclose = () => {
                        console.log('WebSocket连接已断开');
                        this.isWebSocketConnected = false;
                        this.updateConnectionStatus(false);
                    };
                },
                (error) => {
                    clearTimeout(handshakeTimeout);
                    connectionEstablished = false;
                    
                    console.error('WebSocket STOMP连接失败:', error);
                    this.isWebSocketConnected = false;
                    this.updateConnectionStatus(false);

                    // 如果是认证失败，可能是Token过期，尝试重新登录
                    if (error && (error.toString().includes('认证') || error.toString().includes('auth') ||
                        error.toString().includes('403') || error.toString().includes('401'))) {
                        console.warn('WebSocket认证失败，Token可能已过期或无效');
                        this.validateToken().then(isValid => {
                            if (!isValid) {
                                console.warn('Token验证失败，跳转到登录页面');
                                this.logout();
                            } else {
                                console.warn('Token有效但认证失败，需要重新登录');
                                this.logout();
                            }
                        });
                    }
                }
            );
        } catch (error) {
            console.error('WebSocket连接错误:', error);
            this.updateConnectionStatus(false);
        }
    }
    
    updateConnectionStatus(connected) {
        this.isWebSocketConnected = connected;
        const statusEl = document.getElementById('connectionStatus');
        if (!statusEl) return;
        
        const icon = statusEl.querySelector('i');
        const text = statusEl.querySelector('span');
        
        if (connected) {
            statusEl.classList.add('connected');
            if (text) text.textContent = '已连接';
        } else {
            statusEl.classList.remove('connected');
            if (text) text.textContent = '未连接';
        }
    }
    
    updateAccountInfo(accountInfo) {
        // console.log('更新账户信息:', {
        //     totalBalance: accountInfo.totalBalance,
        //     positionsCount: accountInfo.positions ? accountInfo.positions.length : 0,
        //     positions: accountInfo.positions
        // });
        
        // 更新仪表盘
        document.getElementById('totalBalance').textContent = 
            this.formatCurrency(accountInfo.totalBalance);
        document.getElementById('availableBalance').textContent = 
            this.formatCurrency(accountInfo.availableBalance);
        document.getElementById('unrealizedPnl').textContent = 
            this.formatNumber(accountInfo.unrealizedPnl) + ' USDT';
        
        const pnlChange = document.getElementById('pnlChange');
        if (accountInfo.unrealizedPnl) {
            const pnl = parseFloat(accountInfo.unrealizedPnl);
            pnlChange.textContent = (pnl >= 0 ? '+' : '') + pnl.toFixed(2) + '%';
            pnlChange.className = 'stat-change ' + (pnl >= 0 ? 'positive' : 'negative');
        }
        
        document.getElementById('positionCount').textContent = 
            accountInfo.positions ? accountInfo.positions.length : 0;
        
        // 更新账户页面
        document.getElementById('accountTotalBalance').textContent = 
            this.formatCurrency(accountInfo.totalBalance);
        document.getElementById('accountAvailableBalance').textContent = 
            this.formatCurrency(accountInfo.availableBalance);
        document.getElementById('accountFrozenBalance').textContent = 
            this.formatCurrency(accountInfo.frozenBalance);
        document.getElementById('accountEquity').textContent = 
            this.formatCurrency(accountInfo.equity);
        document.getElementById('accountUnrealizedPnl').textContent = 
            this.formatNumber(accountInfo.unrealizedPnl) + ' USDT';
        document.getElementById('accountUpdateTime').textContent = 
            new Date(accountInfo.timestamp).toLocaleString();
        
        // 更新持仓表格
        this.updatePositionsTable(accountInfo.positions || []);
        
        // 更新图表
        this.updateEquityChart(accountInfo);
        this.updatePositionChart(accountInfo.positions || []);
    }
    
    updatePositionsTable(positions) {
        // console.log('更新持仓表格:', positions);
        const tbody = document.getElementById('positionsTableBody');
        
        // 在更新前，保存所有输入框的值和焦点状态（使用 symbol + side 作为唯一标识）
        const savedInputValues = new Map();
        let focusedInputKey = null; // 记录当前有焦点的输入框
        let focusedInputType = null; // 记录焦点类型：'quantity' 或 'margin'
        let cursorPosition = null; // 记录光标位置
        
        tbody.querySelectorAll('tr').forEach(row => {
            const quantityInput = row.querySelector('.position-quantity-input');
            const marginInput = row.querySelector('.position-margin-input');
            if (quantityInput && marginInput) {
                const symbol = quantityInput.dataset.symbol;
                const side = quantityInput.dataset.side;
                const key = `${symbol}_${side}`;
                
                // 保存输入框的值
                savedInputValues.set(key, {
                    quantity: quantityInput.value,
                    margin: marginInput.value
                });
                
                // 检查哪个输入框有焦点
                if (document.activeElement === quantityInput) {
                    focusedInputKey = key;
                    focusedInputType = 'quantity';
                    cursorPosition = quantityInput.selectionStart;
                } else if (document.activeElement === marginInput) {
                    focusedInputKey = key;
                    focusedInputType = 'margin';
                    cursorPosition = marginInput.selectionStart;
                }
            }
        });
        
        // 格式化价格（完整显示，不格式化，保留原始值）
        const formatPrice = (value) => {
            if (value == null || value === undefined) return '--';
            // 直接返回原始值，不进行任何格式化
            return String(value);
        };
        
        // 先构建完整的 HTML 字符串，避免白屏
        let html = '';
        
        if (!positions || positions.length === 0) {
            // console.log('持仓数据为空，显示空状态');
            html = '<tr><td colspan="11" class="empty-state">暂无持仓数据</td></tr>';
        } else {
            // console.log(`更新 ${positions.length} 个持仓`);
            // 为每个持仓构建行 HTML
            positions.forEach((pos, index) => {
                // 计算盈亏比例（乘以杠杆倍数）
                const pnlPercentage = parseFloat(pos.pnlPercentage) || 0;
                const leverage = parseInt(pos.leverage) || 1;
                const adjustedPnlPercentage = pnlPercentage * leverage;
                
                // 计算保证金（如果margin字段不存在，则根据公式计算）
                const margin = pos.margin || (parseFloat(pos.quantity) * parseFloat(pos.currentPrice) / (parseInt(pos.leverage) || 1));
                
                html += `
                    <tr style="height: auto;">
                        <td style="white-space: nowrap;">${pos.symbol}</td>
                        <td style="white-space: nowrap;"><span class="badge ${pos.side === 'LONG' ? 'badge-success' : 'badge-danger'}">${pos.side}</span></td>
                        <td style="white-space: nowrap; font-family: monospace; min-width: 100px;">${String(pos.quantity)}</td>
                        <td style="white-space: nowrap; font-family: monospace; min-width: 120px;">${String(margin)} USDT</td>
                        <td style="white-space: nowrap; font-family: monospace; min-width: 120px;">${formatPrice(pos.avgPrice)}</td>
                        <td style="white-space: nowrap; font-family: monospace; min-width: 120px;">${formatPrice(pos.currentPrice)}</td>
                        <td class="${parseFloat(pos.unrealizedPnl) >= 0 ? 'positive' : 'negative'}" style="white-space: nowrap; font-family: monospace; min-width: 120px;">
                            ${String(pos.unrealizedPnl)} USDT
                        </td>
                        <td class="${adjustedPnlPercentage >= 0 ? 'positive' : 'negative'}" style="white-space: nowrap; min-width: 80px;">
                            ${adjustedPnlPercentage.toFixed(2)}%
                        </td>
                        <td style="white-space: nowrap;">${pos.leverage}x</td>
                        <td style="white-space: nowrap; min-width: 100px;">
                            <button class="btn btn-sm btn-danger close-position-btn" 
                                    data-symbol="${pos.symbol}" 
                                    data-side="${pos.side}"
                                    data-quantity="${pos.quantity}"
                                    data-margin="${margin}"
                                    data-leverage="${pos.leverage}"
                                    data-current-price="${pos.currentPrice}">全部平仓</button>
                        </td>
                        <td style="white-space: normal; min-width: 300px; width: 300px;">
                            <div style="display: flex; gap: 8px; align-items: center; flex-wrap: nowrap;">
                                <div style="display: flex; flex-direction: column; gap: 3px; flex: 0 0 90px;">
                                    <label style="font-size: 11px; color: #666; white-space: nowrap;">数量:</label>
                                    <input type="number" 
                                           class="position-quantity-input" 
                                           data-symbol="${pos.symbol}" 
                                           data-side="${pos.side}"
                                           data-leverage="${pos.leverage}"
                                           data-current-price="${pos.currentPrice}"
                                           placeholder="数量" 
                                           min="0" 
                                           step="0.01"
                                           style="width: 100%; padding: 4px; border: 1px solid #ddd; border-radius: 4px; box-sizing: border-box;">
                                </div>
                                <div style="display: flex; flex-direction: column; gap: 3px; flex: 0 0 90px;">
                                    <label style="font-size: 11px; color: #666; white-space: nowrap;">保证金:</label>
                                    <input type="number" 
                                           class="position-margin-input" 
                                           data-symbol="${pos.symbol}" 
                                           data-side="${pos.side}"
                                           data-leverage="${pos.leverage}"
                                           data-current-price="${pos.currentPrice}"
                                           placeholder="保证金" 
                                           min="0" 
                                           step="0.01"
                                           style="width: 100%; padding: 4px; border: 1px solid #ddd; border-radius: 4px; box-sizing: border-box;">
                                </div>
                                <div style="display: flex; flex-direction: column; gap: 5px; justify-content: flex-end; flex: 0 0 90px;">
                                    <button class="btn btn-sm btn-success add-position-btn" 
                                            data-symbol="${pos.symbol}" 
                                            data-side="${pos.side}"
                                            style="white-space: nowrap; padding: 6px 12px; width: 100%;">加仓</button>
                                    <button class="btn btn-sm btn-warning partial-close-btn" 
                                            data-symbol="${pos.symbol}" 
                                            data-side="${pos.side}"
                                            style="white-space: nowrap; padding: 6px 12px; width: 100%;">部分平仓</button>
                                </div>
                            </div>
                        </td>
                    </tr>
                `;
            });
        }
        
        // 一次性替换整个表格内容，避免白屏
        tbody.innerHTML = html;
        // console.log('持仓表格HTML已更新，行数:', positions ? positions.length : 0);
        
        // 恢复保存的输入框值和焦点
        tbody.querySelectorAll('tr').forEach(row => {
            const quantityInput = row.querySelector('.position-quantity-input');
            const marginInput = row.querySelector('.position-margin-input');
            if (quantityInput && marginInput) {
                const symbol = quantityInput.dataset.symbol;
                const side = quantityInput.dataset.side;
                const key = `${symbol}_${side}`;
                const savedValues = savedInputValues.get(key);
                if (savedValues) {
                    // 恢复保存的值
                    if (savedValues.quantity) {
                        quantityInput.value = savedValues.quantity;
                    }
                    if (savedValues.margin) {
                        marginInput.value = savedValues.margin;
                    }
                }
                
                // 恢复焦点（使用 setTimeout 确保 DOM 已完全更新）
                if (focusedInputKey === key) {
                    setTimeout(() => {
                        let targetInput = null;
                        if (focusedInputType === 'quantity') {
                            targetInput = quantityInput;
                        } else if (focusedInputType === 'margin') {
                            targetInput = marginInput;
                        }
                        
                        if (targetInput) {
                            targetInput.focus();
                            // 恢复光标位置
                            if (cursorPosition !== null && cursorPosition >= 0) {
                                try {
                                    targetInput.setSelectionRange(cursorPosition, cursorPosition);
                                } catch (e) {
                                    // 某些浏览器可能不支持 setSelectionRange，忽略错误
                                    console.debug('无法设置光标位置:', e);
                                }
                            }
                        }
                    }, 0);
                }
            }
        });
        
        // 为所有平仓按钮绑定点击事件
        tbody.querySelectorAll('.close-position-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const symbol = e.target.dataset.symbol;
                const side = e.target.dataset.side;
                const quantity = parseFloat(e.target.dataset.quantity);
                this.closePosition(symbol, side, quantity);
            });
        });
        
        // 为数量输入框和保证金输入框添加联动（输入保证金时自动计算数量，输入数量时自动计算保证金）
        tbody.querySelectorAll('.position-quantity-input, .position-margin-input').forEach(input => {
            input.addEventListener('input', (e) => {
                const row = e.target.closest('tr');
                const quantityInput = row.querySelector('.position-quantity-input');
                const marginInput = row.querySelector('.position-margin-input');
                const leverage = parseFloat(e.target.dataset.leverage) || 1;
                const currentPrice = parseFloat(e.target.dataset.currentPrice) || 0;
                
                if (currentPrice <= 0) {
                    return; // 当前价格无效，不计算
                }
                
                if (e.target === quantityInput) {
                    // 输入数量，计算保证金
                    const quantity = parseFloat(quantityInput.value) || 0;
                    if (quantity > 0) {
                        const margin = quantity * currentPrice / leverage;
                        marginInput.value = margin.toFixed(8);
                    } else {
                        marginInput.value = '';
                    }
                } else if (e.target === marginInput) {
                    // 输入保证金，计算数量
                    const margin = parseFloat(marginInput.value) || 0;
                    if (margin > 0) {
                        const quantity = margin * leverage / currentPrice;
                        quantityInput.value = quantity.toFixed(8);
                    } else {
                        quantityInput.value = '';
                    }
                }
            });
        });
        
        // 为所有加仓按钮绑定点击事件
        tbody.querySelectorAll('.add-position-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const symbol = e.target.dataset.symbol;
                const side = e.target.dataset.side;
                const row = e.target.closest('tr');
                const quantityInput = row.querySelector('.position-quantity-input');
                const marginInput = row.querySelector('.position-margin-input');
                const quantity = parseFloat(quantityInput.value);
                const margin = parseFloat(marginInput.value);
                
                // 优先使用保证金，如果没有则使用数量
                if (margin && margin > 0) {
                    this.addPositionByMargin(symbol, side, margin);
                } else if (quantity && quantity > 0) {
                    this.addPosition(symbol, side, quantity);
                } else {
                    alert('请输入有效的加仓数量或保证金');
                    return;
                }
            });
        });
        
        // 为所有部分平仓按钮绑定点击事件
        tbody.querySelectorAll('.partial-close-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const symbol = e.target.dataset.symbol;
                const side = e.target.dataset.side;
                const row = e.target.closest('tr');
                const quantityInput = row.querySelector('.position-quantity-input');
                const marginInput = row.querySelector('.position-margin-input');
                
                // 获取输入值（转换为数字，如果为空或无效则为 NaN）
                const quantityValue = quantityInput ? quantityInput.value.trim() : '';
                const marginValue = marginInput ? marginInput.value.trim() : '';
                const quantity = quantityValue ? parseFloat(quantityValue) : NaN;
                const margin = marginValue ? parseFloat(marginValue) : NaN;
                
                // 检查输入是否有效
                const hasValidQuantity = !isNaN(quantity) && quantity > 0;
                const hasValidMargin = !isNaN(margin) && margin > 0;
                
                // 优先使用保证金，如果没有则使用数量
                if (hasValidMargin) {
                    console.log('使用保证金平仓:', symbol, side, margin);
                    this.closePositionByMargin(symbol, side, margin);
                } else if (hasValidQuantity) {
                    console.log('使用数量平仓:', symbol, side, quantity);
                    this.closePosition(symbol, side, quantity);
                } else {
                    alert('请输入有效的平仓数量或保证金');
                    return;
                }
            });
        });
        
        // console.log('========== 更新持仓表格完成 ==========');
    }
    
    /**
     * 平仓操作（支持部分平仓）
     * @param {string} symbol 交易对
     * @param {string} side 持仓方向 (LONG/SHORT)
     * @param {number} quantity 平仓数量，如果为null或undefined则平全部
     */
    async closePosition(symbol, side, quantity = null) {
        if (!this.userId || !this.token) {
            alert('请先登录');
            return;
        }
        
        const quantityText = quantity ? ` ${quantity} 个` : '全部';
        if (!confirm(`确定要平仓 ${symbol} ${side} 持仓${quantityText}吗？`)) {
            return;
        }
        
        try {
            const params = new URLSearchParams({
                symbol: symbol,
                side: side
            });
            
            // 如果指定了数量，添加quantity参数
            if (quantity != null && quantity > 0) {
                params.append('quantity', quantity.toString());
            }
            
            const response = await fetch(`${this.backendUrl}/api/account/positions/${this.userId}/close`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'Authorization': `Bearer ${this.token}`
                },
                body: params
            });
            
            const result = await response.json();
            
            if (result.success) {
                alert('平仓成功！');
                // 通过WebSocket刷新持仓数据
                if (this.stompClient && this.stompClient.connected && this.isWebSocketConnected) {
                    this.stompClient.send('/app/account/request', {}, {});
                    console.log('平仓成功，通过WebSocket请求刷新账户信息');
                } else {
                    console.warn('WebSocket未连接，无法刷新数据');
                }
            } else {
                alert('平仓失败: ' + (result.message || '未知错误'));
            }
        } catch (error) {
            console.error('平仓请求失败:', error);
            alert('平仓失败: ' + error.message);
        }
    }
    
    /**
     * 按保证金数量加仓
     * @param {string} symbol 交易对
     * @param {string} side 持仓方向 (LONG/SHORT)
     * @param {number} margin 保证金数量（USDT）
     */
    async addPositionByMargin(symbol, side, margin) {
        if (!this.userId || !this.token) {
            alert('请先登录');
            return;
        }
        
        if (!margin || margin <= 0) {
            alert('请输入有效的保证金数量');
            return;
        }
        
        if (!confirm(`确定要使用 ${margin} USDT 保证金加仓 ${symbol} ${side} 吗？`)) {
            return;
        }
        
        try {
            const response = await fetch(`${this.backendUrl}/api/account/positions/${this.userId}/open`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'Authorization': `Bearer ${this.token}`
                },
                body: new URLSearchParams({
                    symbol: symbol,
                    side: side,
                    margin: margin.toString()
                })
            });
            
            const result = await response.json();
            
            if (result.success) {
                alert('加仓成功！');
                // 通过WebSocket刷新持仓数据
                if (this.stompClient && this.stompClient.connected && this.isWebSocketConnected) {
                    this.stompClient.send('/app/account/request', {}, {});
                    console.log('加仓成功，通过WebSocket请求刷新账户信息');
                } else {
                    console.warn('WebSocket未连接，无法刷新数据');
                }
            } else {
                alert('加仓失败: ' + (result.message || '未知错误'));
            }
        } catch (error) {
            console.error('加仓请求失败:', error);
            alert('加仓失败: ' + error.message);
        }
    }
    
    /**
     * 按保证金数量平仓
     * @param {string} symbol 交易对
     * @param {string} side 持仓方向 (LONG/SHORT)
     * @param {number} margin 保证金数量（USDT）
     */
    async closePositionByMargin(symbol, side, margin) {
        if (!this.userId || !this.token) {
            alert('请先登录');
            return;
        }
        
        if (!margin || margin <= 0) {
            alert('请输入有效的保证金数量');
            return;
        }
        
        if (!confirm(`确定要使用 ${margin} USDT 保证金平仓 ${symbol} ${side} 吗？`)) {
            return;
        }
        
        try {
            const response = await fetch(`${this.backendUrl}/api/account/positions/${this.userId}/close`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'Authorization': `Bearer ${this.token}`
                },
                body: new URLSearchParams({
                    symbol: symbol,
                    side: side,
                    margin: margin.toString()
                })
            });
            
            const result = await response.json();
            
            if (result.success) {
                alert('平仓成功！');
                // 通过WebSocket刷新持仓数据
                if (this.stompClient && this.stompClient.connected && this.isWebSocketConnected) {
                    this.stompClient.send('/app/account/request', {}, {});
                    console.log('平仓成功，通过WebSocket请求刷新账户信息');
                } else {
                    console.warn('WebSocket未连接，无法刷新数据');
                }
            } else {
                alert('平仓失败: ' + (result.message || '未知错误'));
            }
        } catch (error) {
            console.error('平仓请求失败:', error);
            alert('平仓失败: ' + error.message);
        }
    }
    
    /**
     * 加仓操作（开仓）
     * @param {string} symbol 交易对
     * @param {string} side 持仓方向 (LONG/SHORT)
     * @param {number} quantity 加仓数量
     */
    async addPosition(symbol, side, quantity) {
        if (!this.userId || !this.token) {
            alert('请先登录');
            return;
        }
        
        if (!quantity || quantity <= 0) {
            alert('请输入有效的加仓数量');
            return;
        }
        
        if (!confirm(`确定要加仓 ${symbol} ${side} ${quantity} 个吗？`)) {
            return;
        }
        
        try {
            const response = await fetch(`${this.backendUrl}/api/account/positions/${this.userId}/open`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'Authorization': `Bearer ${this.token}`
                },
                body: new URLSearchParams({
                    symbol: symbol,
                    side: side,
                    quantity: quantity.toString()
                })
            });
            
            const result = await response.json();
            
            if (result.success) {
                alert('加仓成功！');
                // 通过WebSocket刷新持仓数据
                if (this.stompClient && this.stompClient.connected && this.isWebSocketConnected) {
                    this.stompClient.send('/app/account/request', {}, {});
                    console.log('加仓成功，通过WebSocket请求刷新账户信息');
                } else {
                    console.warn('WebSocket未连接，无法刷新数据');
                }
            } else {
                alert('加仓失败: ' + (result.message || '未知错误'));
            }
        } catch (error) {
            console.error('加仓请求失败:', error);
            alert('加仓失败: ' + error.message);
        }
    }
    
    async refreshData() {
        // 仅通过WebSocket获取数据，不使用REST API
        if (this.stompClient && this.stompClient.connected && this.isWebSocketConnected) {
            // 通过WebSocket请求账户信息（包含持仓）
            try {
                this.stompClient.send('/app/account/request', {}, {});
                console.log('通过WebSocket请求账户信息（含持仓）');
            } catch (wsError) {
                console.error('WebSocket请求失败:', wsError);
                console.warn('WebSocket请求失败，请检查连接状态');
            }
        } else {
            console.warn('WebSocket未连接，无法刷新数据。请等待WebSocket连接建立。');
        }
    }
    
    
    /**
     * 更新持仓数据（兼容方法）
     */
    updatePositions(positions) {
        this.updatePositionsTable(positions);
    }
    
    formatCurrency(value) {
        if (value == null || value === undefined) return '--';
        return parseFloat(value).toLocaleString('zh-CN', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }) + ' USDT';
    }
    
    formatNumber(value) {
        if (value == null || value === undefined) return '--';
        const num = parseFloat(value);
        if (isNaN(num)) return '--';
        // 转换为字符串，自动去除尾随零，保留原始精度
        return parseFloat(num.toFixed(8)).toString();
    }
    
    /**
     * 初始化图表
     */
    initCharts() {
        // 初始化账户权益曲线图表
        const equityCtx = document.getElementById('equityChart');
        if (equityCtx && typeof Chart !== 'undefined') {
            this.equityChart = new Chart(equityCtx, {
                type: 'line',
                data: {
                    labels: [],
                    datasets: [{
                        label: '账户权益 (USDT)',
                        data: [],
                        borderColor: 'rgb(75, 192, 192)',
                        backgroundColor: 'rgba(75, 192, 192, 0.2)',
                        tension: 0.1,
                        fill: true
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: {
                            display: true,
                            position: 'top'
                        },
                        tooltip: {
                            mode: 'index',
                            intersect: false
                        }
                    },
                    scales: {
                        y: {
                            beginAtZero: false,
                            ticks: {
                                callback: function(value) {
                                    return value.toLocaleString('zh-CN', {
                                        minimumFractionDigits: 2,
                                        maximumFractionDigits: 2
                                    }) + ' USDT';
                                },
                                // 确保Y轴标签动态更新
                                autoSkip: false,
                                maxTicksLimit: 10
                            },
                            // 确保Y轴范围自动调整
                            afterDataLimits: function(scale) {
                                // 允许Chart.js自动计算范围
                            }
                        },
                        x: {
                            ticks: {
                                maxRotation: 45,
                                minRotation: 45
                            }
                        }
                    }
                }
            });
        }
        
        // 初始化持仓分布图表
        const positionCtx = document.getElementById('positionChart');
        if (positionCtx) {
            this.positionChart = new Chart(positionCtx, {
                type: 'doughnut',
                data: {
                    labels: [],
                    datasets: [{
                        data: [],
                        backgroundColor: [
                            'rgba(255, 99, 132, 0.8)',
                            'rgba(54, 162, 235, 0.8)',
                            'rgba(255, 206, 86, 0.8)',
                            'rgba(75, 192, 192, 0.8)',
                            'rgba(153, 102, 255, 0.8)',
                            'rgba(255, 159, 64, 0.8)'
                        ],
                        borderWidth: 2
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: {
                            display: true,
                            position: 'right'
                        },
                        tooltip: {
                            callbacks: {
                                label: function(context) {
                                    const label = context.label || '';
                                    const value = context.parsed || 0;
                                    return label + ': ' + value.toLocaleString('zh-CN', {
                                        minimumFractionDigits: 2,
                                        maximumFractionDigits: 2
                                    }) + ' USDT';
                                }
                            }
                        }
                    }
                }
            });
        }
    }
    
    /**
     * 更新账户权益曲线图表
     */
    updateEquityChart(accountInfo) {
        if (!this.equityChart || !accountInfo) {
            return;
        }
        
        // 使用账户权益（equity），如果没有则使用总资产（totalBalance）
        // 账户权益 = 总资产 + 未实现盈亏
        const equity = parseFloat(accountInfo.equity) || parseFloat(accountInfo.totalBalance) || 0;
        
        if (equity <= 0) {
            return;
        }
        
        const now = new Date();
        const timeLabel = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
        
        // 添加新数据点
        this.equityHistory.push({
            time: timeLabel,
            value: equity
        });
        
        // 只保留最近30个数据点
        if (this.equityHistory.length > 30) {
            this.equityHistory.shift();
        }
        
        // 更新图表
        this.equityChart.data.labels = this.equityHistory.map(h => h.time);
        this.equityChart.data.datasets[0].data = this.equityHistory.map(h => h.value);
        
        // 确保Y轴自动调整范围
        this.equityChart.options.scales.y.ticks.callback = function(value) {
            return value.toLocaleString('zh-CN', {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            }) + ' USDT';
        };
        
        // 更新图表（使用 'none' 模式不显示动画，提高性能）
        this.equityChart.update('none');
    }
    
    /**
     * 初始化策略配置页面
     */
    initStrategies() {
        // 使用事件委托，在策略列表容器上监听所有按钮点击
        const strategyList = document.getElementById('strategyList');
        if (strategyList && !strategyList.dataset.listenerAttached) {
            strategyList.dataset.listenerAttached = 'true';
            
            strategyList.addEventListener('click', (e) => {
                const target = e.target;
                
                // 只处理按钮点击
                if (!target.matches('button')) return;
                
                e.preventDefault();
                e.stopPropagation();
                
                // 找到按钮所在的策略卡片
                const card = target.closest('.strategy-card');
                if (!card) return;
                
                const strategyName = card.querySelector('h3')?.textContent || '未知策略';
                const strategyType = card.querySelector('.strategy-badge')?.textContent || '';
                const btnText = target.textContent.trim();
                
                // 根据按钮文本和样式判断操作类型
                if (btnText.includes('配置') && target.classList.contains('btn-primary')) {
                    this.handleStrategyConfig(strategyName, card);
                } else if ((btnText.includes('启动') || btnText === '启动') && target.classList.contains('btn-success')) {
                    this.handleStrategyStart(strategyName, strategyType, target);
                } else if ((btnText.includes('运行中') || btnText === '运行中') && target.classList.contains('btn-warning')) {
                    // 运行中的按钮点击不做任何操作，或者可以显示状态信息
                    console.log('策略正在运行中:', strategyName);
                } else if ((btnText.includes('停止') || btnText === '停止') && target.classList.contains('btn-danger')) {
                    this.handleStrategyStop(strategyName, strategyType, target);
                }
            });
        }
        
        // 添加策略按钮
        const addStrategyBtn = document.getElementById('addStrategyBtn');
        if (addStrategyBtn && !addStrategyBtn.dataset.listenerAttached) {
            addStrategyBtn.dataset.listenerAttached = 'true';
            addStrategyBtn.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                this.handleAddStrategy();
            });
        }
        
        // 刷新策略状态
        this.refreshStrategiesStatus();
    }
    
    /**
     * 刷新策略状态
     */
    async refreshStrategiesStatus() {
        if (!this.userId || !this.token) {
            return;
        }
        
        try {
            const response = await fetch(`${this.backendUrl}/api/strategy/status?userId=${encodeURIComponent(this.userId)}`, {
                method: 'GET',
                headers: {
                    'Authorization': `Bearer ${this.token}`
                }
            });
            
            if (response.ok) {
                const result = await response.json();
                if (result.success && result.strategies) {
                    // 更新每个策略的按钮状态
                    const strategyCards = document.querySelectorAll('.strategy-card');
                    strategyCards.forEach(card => {
                        const strategyName = card.querySelector('h3')?.textContent;
                        if (!strategyName) return;
                        
                        const status = result.strategies[strategyName];
                        if (status) {
                            const actionsDiv = card.querySelector('.strategy-actions');
                            if (actionsDiv) {
                                const startBtn = Array.from(actionsDiv.querySelectorAll('button')).find(
                                    btn => btn.classList.contains('btn-success') || btn.classList.contains('btn-warning')
                                );
                                
                                if (startBtn) {
                                    if (status.running || status.enabled) {
                                        // 策略正在运行
                                        startBtn.classList.remove('btn-success');
                                        startBtn.classList.add('btn-warning');
                                        startBtn.textContent = '运行中';
                                        startBtn.disabled = false;
                                        card.classList.add('strategy-running');
                                    } else {
                                        // 策略未运行
                                        startBtn.classList.remove('btn-warning');
                                        startBtn.classList.add('btn-success');
                                        startBtn.textContent = '启动';
                                        startBtn.disabled = false;
                                        card.classList.remove('strategy-running');
                                    }
                                }
                            }
                        }
                    });
                }
            }
        } catch (error) {
            console.error('刷新策略状态失败:', error);
        }
    }
    
    /**
     * 处理策略配置
     */
    handleStrategyConfig(strategyName, cardElement) {
        console.log('配置策略:', strategyName);
        
        // 获取策略类型
        const strategyType = cardElement.querySelector('.strategy-badge')?.textContent || '';
        
        // 显示配置模态框
        this.showStrategyConfigModal(strategyName, strategyType);
    }
    
    /**
     * 显示策略配置模态框
     */
    async showStrategyConfigModal(strategyName, strategyType) {
        const modal = document.getElementById('strategyConfigModal');
        const title = document.getElementById('strategyConfigTitle');
        const nameInput = document.getElementById('strategyConfigName');
        const typeInput = document.getElementById('strategyConfigType');
        const symbolsTextarea = document.getElementById('strategyConfigSymbols');
        const paramsTextarea = document.getElementById('strategyConfigParams');
        const errorDiv = document.getElementById('strategyConfigError');
        const closeBtn = document.getElementById('strategyConfigClose');
        const cancelBtn = document.getElementById('strategyConfigCancel');
        const saveBtn = document.getElementById('strategyConfigSave');
        
        if (!modal) {
            console.error('策略配置模态框不存在');
            return;
        }
        
        // 设置基本信息
        title.textContent = `配置 ${strategyName}`;
        nameInput.value = strategyName;
        typeInput.value = strategyType;
        errorDiv.style.display = 'none';
        errorDiv.textContent = '';
        
        // 先显示模态框
        modal.style.display = 'flex';
        modal.classList.add('show');
        
        // 确保输入框可编辑
        symbolsTextarea.removeAttribute('readonly');
        symbolsTextarea.removeAttribute('disabled');
        paramsTextarea.removeAttribute('readonly');
        paramsTextarea.removeAttribute('disabled');
        
        // 先设置默认值，允许用户立即输入
        symbolsTextarea.value = 'BTC/USDT';
        paramsTextarea.value = '{}';
        
        // 从后端获取已保存的配置（异步，不阻塞用户输入）
        (async () => {
            try {
                const configUrl = `${this.backendUrl}/api/strategy/config?userId=${encodeURIComponent(this.userId)}&strategyName=${encodeURIComponent(strategyName)}`;
                const response = await fetch(configUrl, {
                    method: 'GET',
                    headers: {
                        'Authorization': `Bearer ${this.token}`
                    }
                });
                
                if (response.ok) {
                    const result = await response.json();
                    if (result.success && result.config) {
                        // 填充已保存的配置
                        const config = result.config;
                        
                        // 填充交易对列表（只有在用户还没有修改时才更新）
                        if (config.symbols && Array.isArray(config.symbols)) {
                            const currentValue = symbolsTextarea.value.trim();
                            // 如果用户还没有修改（仍然是默认值），则更新
                            if (currentValue === 'BTC/USDT' || currentValue === '') {
                                symbolsTextarea.value = config.symbols.join('\n');
                            }
                        }
                        
                        // 填充其他参数（排除symbols字段）
                        const otherParams = { ...config };
                        delete otherParams.symbols;
                        if (Object.keys(otherParams).length > 0) {
                            const currentParams = paramsTextarea.value.trim();
                            // 如果用户还没有修改（仍然是默认值），则更新
                            if (currentParams === '{}' || currentParams === '') {
                                paramsTextarea.value = JSON.stringify(otherParams, null, 2);
                            }
                        }
                    }
                }
            } catch (error) {
                // 获取配置出错，不影响用户输入
                console.error('获取策略配置出错:', error);
            }
        })();
        
        // 关闭按钮事件
        const closeModal = () => {
            modal.style.display = 'none';
            modal.classList.remove('show');
        };
        
        // 绑定事件（移除旧的事件监听器）
        const newCloseBtn = closeBtn.cloneNode(true);
        closeBtn.parentNode.replaceChild(newCloseBtn, closeBtn);
        newCloseBtn.addEventListener('click', closeModal);
        
        const newCancelBtn = cancelBtn.cloneNode(true);
        cancelBtn.parentNode.replaceChild(newCancelBtn, cancelBtn);
        newCancelBtn.addEventListener('click', closeModal);
        
        const newSaveBtn = saveBtn.cloneNode(true);
        saveBtn.parentNode.replaceChild(newSaveBtn, saveBtn);
        newSaveBtn.addEventListener('click', () => {
            this.handleSaveStrategyConfig(strategyName, strategyType, symbolsTextarea.value, paramsTextarea.value, errorDiv, closeModal);
        });
        
        // 点击背景关闭
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                closeModal();
            }
        });
        
        // ESC键关闭
        const escHandler = (e) => {
            if (e.key === 'Escape' && modal.classList.contains('show')) {
                closeModal();
                document.removeEventListener('keydown', escHandler);
            }
        };
        document.addEventListener('keydown', escHandler);
        
        // 聚焦到交易对输入框
        setTimeout(() => {
            symbolsTextarea.focus();
        }, 100);
    }
    
    /**
     * 处理保存策略配置
     */
    handleSaveStrategyConfig(strategyName, strategyType, symbolsText, configText, errorDiv, closeModal) {
        // 隐藏错误
        errorDiv.style.display = 'none';
        errorDiv.textContent = '';
        
        // 解析交易对列表
        const symbols = symbolsText.trim()
            .split('\n')
            .map(s => s.trim())
            .filter(s => s.length > 0);
        
        // 解析其他配置参数（JSON格式）
        let otherParams = {};
        if (configText && configText.trim()) {
            try {
                otherParams = JSON.parse(configText);
            } catch (error) {
                errorDiv.style.display = 'block';
                errorDiv.textContent = '配置参数格式错误，请输入有效的JSON格式';
                return;
            }
        }
        
        // 合并配置：交易对列表 + 其他参数
        const params = {
            symbols: symbols.length > 0 ? symbols : ['BTC/USDT'], // 如果没有配置，使用默认
            ...otherParams
        };
        
        // 保存配置
        this.saveStrategyConfig(strategyName, strategyType, params).then(() => {
            closeModal();
        }).catch((error) => {
            errorDiv.style.display = 'block';
            errorDiv.textContent = error.message || '保存失败';
        });
    }
    
    /**
     * 处理策略启动
     */
    async handleStrategyStart(strategyName, strategyType, buttonElement) {
        console.log('启动策略:', strategyName, strategyType);
        
        if (!this.userId || !this.token) {
            alert('请先登录');
            return;
        }
        
        // 更新按钮状态
        const originalText = buttonElement.textContent;
        buttonElement.disabled = true;
        buttonElement.textContent = '启动中...';
        
        try {
            // 调用后端API启动策略
            const response = await fetch(`${this.backendUrl}/api/strategy/start`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${this.token}`
                },
                body: JSON.stringify({
                    userId: this.userId,
                    strategyName: strategyName,
                    strategyType: strategyType,
                    exchangeType: this.exchangeType
                })
            });
            
            if (response.ok) {
                const result = await response.json();
                if (result.success) {
                    // 更新按钮状态
                    buttonElement.classList.remove('btn-success');
                    buttonElement.classList.add('btn-warning');
                    buttonElement.textContent = '运行中';
                    buttonElement.disabled = false;
                    
                    // 更新卡片状态
                    const card = buttonElement.closest('.strategy-card');
                    if (card) {
                        card.classList.add('strategy-running');
                    }
                    
                    // 刷新策略状态（确保状态同步）
                    setTimeout(() => {
                        this.refreshStrategiesStatus();
                    }, 500);
                    
                    if (window.UIUtils) {
                        UIUtils.showNotification(`${strategyName} 启动成功`, 'success');
                    } else {
                        alert(`${strategyName} 启动成功`);
                    }
                } else {
                    throw new Error(result.message || '启动失败');
                }
            } else {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.message || `HTTP ${response.status}`);
            }
        } catch (error) {
            console.error('启动策略失败:', error);
            
            // 恢复按钮状态
            buttonElement.disabled = false;
            buttonElement.textContent = originalText;
            
            // 显示错误提示
            if (error.message.includes('404') || error.message.includes('Not Found')) {
                alert(`策略启动功能尚未实现\n\n策略: ${strategyName}\n类型: ${strategyType}\n\n后端API: POST /api/strategy/start`);
            } else {
                alert(`启动策略失败: ${error.message}`);
            }
        }
    }
    
    /**
     * 处理策略停止
     */
    async handleStrategyStop(strategyName, strategyType, buttonElement) {
        console.log('停止策略:', strategyName, strategyType);
        
        if (!confirm(`确定要停止 ${strategyName} 吗？`)) {
            return;
        }
        
        if (!this.userId || !this.token) {
            alert('请先登录');
            return;
        }
        
        // 更新按钮状态
        const originalText = buttonElement.textContent;
        buttonElement.disabled = true;
        buttonElement.textContent = '停止中...';
        
        try {
            // 调用后端API停止策略
            const response = await fetch(`${this.backendUrl}/api/strategy/stop`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${this.token}`
                },
                body: JSON.stringify({
                    userId: this.userId,
                    strategyName: strategyName,
                    strategyType: strategyType
                })
            });
            
            if (response.ok) {
                const result = await response.json();
                if (result.success) {
                    // 更新按钮状态
                    buttonElement.classList.remove('btn-warning');
                    buttonElement.classList.add('btn-success');
                    buttonElement.textContent = '启动';
                    buttonElement.disabled = false;
                    
                    // 更新卡片状态
                    const card = buttonElement.closest('.strategy-card');
                    if (card) {
                        card.classList.remove('strategy-running');
                    }
                    
                    // 刷新策略状态（确保状态同步）
                    setTimeout(() => {
                        this.refreshStrategiesStatus();
                    }, 500);
                    
                    if (window.UIUtils) {
                        UIUtils.showNotification(`${strategyName} 已停止`, 'info');
                    } else {
                        alert(`${strategyName} 已停止`);
                    }
                } else {
                    throw new Error(result.message || '停止失败');
                }
            } else {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.message || `HTTP ${response.status}`);
            }
        } catch (error) {
            console.error('停止策略失败:', error);
            
            // 恢复按钮状态
            buttonElement.disabled = false;
            buttonElement.textContent = originalText;
            
            // 显示错误提示
            if (error.message.includes('404') || error.message.includes('Not Found')) {
                alert(`策略停止功能尚未实现\n\n策略: ${strategyName}\n类型: ${strategyType}\n\n后端API: POST /api/strategy/stop`);
            } else {
                alert(`停止策略失败: ${error.message}`);
            }
        }
    }
    
    /**
     * 保存策略配置
     */
    async saveStrategyConfig(strategyName, strategyType, params) {
        if (!this.userId || !this.token) {
            throw new Error('请先登录');
        }
        
        try {
            const response = await fetch(`${this.backendUrl}/api/strategy/config`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${this.token}`
                },
                body: JSON.stringify({
                    userId: this.userId,
                    strategyName: strategyName,
                    strategyType: strategyType,
                    params: params
                })
            });
            
            if (response.ok) {
                if (window.UIUtils) {
                    UIUtils.showNotification(`${strategyName} 配置保存成功`, 'success');
                } else {
                    console.log(`${strategyName} 配置保存成功`);
                }
            } else {
                const errorData = await response.json().catch(() => ({}));
                if (response.status === 404) {
                    throw new Error(`策略配置功能尚未实现\n\n策略: ${strategyName}\n类型: ${strategyType}\n\n后端API: POST /api/strategy/config`);
                } else {
                    throw new Error(errorData.message || '未知错误');
                }
            }
        } catch (error) {
            console.error('保存策略配置失败:', error);
            throw error;
        }
    }
    
    /**
     * 处理添加策略
     */
    handleAddStrategy() {
        alert('添加策略功能开发中...\n\n当前支持三种策略类型：\n1. 普通策略 (NORMAL)\n2. 网格策略 (GRID)\n3. 双向策略 (DUAL_DIRECTION)');
    }
    
    /**
     * 更新持仓分布图表
     */
    updatePositionChart(positions) {
        if (!this.positionChart) {
            return;
        }
        
        if (!positions || positions.length === 0) {
            // 没有持仓时显示空状态
            this.positionChart.data.labels = ['暂无持仓'];
            this.positionChart.data.datasets[0].data = [1];
            this.positionChart.data.datasets[0].backgroundColor = ['rgba(200, 200, 200, 0.5)'];
            this.positionChart.update('none');
            return;
        }
        
        // 计算每个持仓的价值
        const positionData = positions.map(pos => {
            const quantity = parseFloat(pos.quantity) || 0;
            const currentPrice = parseFloat(pos.currentPrice) || 0;
            const value = quantity * currentPrice;
            return {
                symbol: pos.symbol,
                value: value
            };
        }).filter(p => p.value > 0);
        
        // 更新图表数据
        this.positionChart.data.labels = positionData.map(p => p.symbol);
        this.positionChart.data.datasets[0].data = positionData.map(p => p.value);
        this.positionChart.data.datasets[0].backgroundColor = [
            'rgba(255, 99, 132, 0.8)',
            'rgba(54, 162, 235, 0.8)',
            'rgba(255, 206, 86, 0.8)',
            'rgba(75, 192, 192, 0.8)',
            'rgba(153, 102, 255, 0.8)',
            'rgba(255, 159, 64, 0.8)'
        ].slice(0, positionData.length);
        this.positionChart.update('none');
    }
}

// 全局错误处理，捕获未定义的 dragEvent 错误
window.addEventListener('error', (event) => {
    // 忽略 dragEvent 未定义的错误（可能是 Electron 或浏览器引擎的内部问题）
    if (event.message && event.message.includes('dragEvent is not defined')) {
        event.preventDefault();
        console.debug('已忽略 dragEvent 未定义错误（可能是 Electron 内部问题）');
        return true;
    }
    return false;
});

// 初始化应用
let app;
document.addEventListener('DOMContentLoaded', () => {
    app = new TradingApp();
});

