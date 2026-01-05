# 量化交易系统 - Electron前端

基于Electron的量化交易系统前端应用，提供实时账户监控、持仓管理、策略配置等功能。

## 功能特性

- ✅ **实时数据推送**: WebSocket实时获取账户和仓位数据
- ✅ **多交易所支持**: 支持OKX和Binance交易所
- ✅ **账户监控**: 实时显示账户余额、盈亏等信息
- ✅ **持仓管理**: 查看和管理持仓信息
- ✅ **策略配置**: 配置和管理交易策略
- ✅ **现代化UI**: 美观的用户界面

## 安装和运行

### 安装依赖

```bash
cd electron-app
npm install
```

### 开发模式运行

```bash
npm run dev
```

### 生产模式运行

```bash
npm start
```

### 打包应用

**快速打包**:

```bash
# Windows
npm run build:win

# macOS
npm run build:mac

# Linux
npm run build:linux

# 自动检测平台
npm run build
```

**打包输出**: 打包后的文件位于 `dist/` 目录

- Windows: `量化交易系统 Setup 1.0.0.exe` (安装程序)
- macOS: `量化交易系统-1.0.0.dmg` (安装镜像)
- Linux: `量化交易系统-1.0.0.AppImage` (可执行文件)

📖 **详细打包指南**: 请查看 [BUILD_GUIDE.md](./BUILD_GUIDE.md) 获取完整的打包说明、常见问题解决方案和高级配置。

## 使用说明

### 1. 配置交易所

1. 打开"系统设置"页面
2. 选择交易所（OKX或Binance）
3. 输入API Key、Secret Key
4. 如果是OKX，还需要输入Passphrase
5. 输入后端服务地址（默认：http://localhost:8080）
6. 输入用户ID
7. 点击"保存配置"

### 2. 查看账户信息

- 在"仪表盘"页面查看账户概览
- 在"账户信息"页面查看详细账户数据
- 数据会通过WebSocket实时更新

### 3. 管理持仓

- 在"持仓管理"页面查看所有持仓
- 可以查看持仓盈亏、杠杆等信息
- 支持平仓操作

### 4. 配置策略

- 在"策略配置"页面管理交易策略
- 支持普通策略、网格策略、双向策略等
- 可以启动、停止、配置策略

## 项目结构

```
electron-app/
├── main.js              # Electron主进程
├── preload.js           # 预加载脚本
├── index.html           # 主页面
├── package.json         # 项目配置
├── styles/
│   └── main.css         # 样式文件
├── js/
│   ├── app.js           # 主应用逻辑
│   ├── websocket.js     # WebSocket管理
│   └── ui.js            # UI工具函数
└── assets/              # 资源文件
```

## 技术栈

- **Electron**: 跨平台桌面应用框架
- **SockJS**: WebSocket客户端
- **STOMP**: WebSocket消息协议
- **原生JavaScript**: 无框架依赖，轻量高效

## 配置说明

应用配置保存在浏览器的localStorage中，包括：
- 后端服务地址
- 用户ID
- 交易所类型

## 注意事项

1. 确保后端服务（Java Spring Boot）正在运行
2. 确保后端WebSocket服务正常启动
3. 首次使用需要配置交易所API密钥
4. WebSocket连接状态显示在侧边栏底部

## 开发

### 添加新功能

1. 在`js/app.js`中添加业务逻辑
2. 在`index.html`中添加UI元素
3. 在`styles/main.css`中添加样式

### 调试

开发模式下会自动打开开发者工具，可以：
- 查看控制台日志
- 调试WebSocket连接
- 检查网络请求

## 许可证

MIT



