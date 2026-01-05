# 量化交易系统 - 后端管理页面

这是一个基于 Vue 3 + Element Plus 的后端管理页面，用于查看用户信息和交易历史记录。

## 功能特性

- ✅ 用户信息查看（基本信息、账户信息）
- ✅ 持仓信息查看
- ✅ 平仓历史记录查看
- ✅ 数据统计和汇总
- ✅ 响应式设计

## 技术栈

- Vue 3 (Composition API)
- Vue Router 4
- Element Plus
- Axios
- Vite

## 快速开始

### 安装依赖

```bash
npm install
```

### 开发模式

```bash
npm run dev
```

访问 http://localhost:3000

### 构建生产版本

```bash
npm run build
```

构建完成后，会在 `dist` 目录生成静态文件。

### 部署到 1Panel

详细的 1Panel 部署指南请查看：[DEPLOY_1PANEL.md](./DEPLOY_1PANEL.md)

**快速部署步骤：**
1. 运行 `deploy.bat`（Windows）或 `npm run build`（所有平台）
2. 在 1Panel 中创建静态网站
3. 上传 `dist` 文件夹内容到网站根目录
4. 配置 Nginx 反向代理（参考 `nginx.conf.example`）

## 项目结构

```
vue-backend-admin/
├── src/
│   ├── components/        # 组件目录
│   │   ├── UserInfo.vue          # 用户信息组件
│   │   ├── AccountInfo.vue       # 账户信息组件
│   │   ├── PositionList.vue      # 持仓列表组件
│   │   └── CloseHistory.vue      # 平仓历史组件
│   ├── views/            # 页面视图
│   │   └── Dashboard.vue        # 主仪表板
│   ├── services/         # API 服务
│   │   └── api.js                # API 接口封装
│   ├── router/           # 路由配置
│   │   └── index.js              # 路由定义
│   ├── App.vue           # 根组件
│   └── main.js           # 入口文件
├── index.html            # HTML 模板
├── vite.config.js        # Vite 配置
└── package.json          # 项目配置
```

## API 接口

后端 API 基础路径：`http://localhost:8080/api`

- `GET /api/user/{userId}` - 获取用户信息
- `GET /api/account/info/{userId}` - 获取账户信息
- `GET /api/account/positions/{userId}` - 获取持仓列表
- `GET /api/account/close-positions/{userId}` - 获取平仓历史记录

