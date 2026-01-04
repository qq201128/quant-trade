# 1Panel部署快速参考

> 快速查阅关键步骤和命令

## 🚀 快速开始（5分钟概览）

### 1. 安装1Panel
```bash
curl -sSL https://resource.fit2cloud.com/1panel/package/quick_start.sh -o quick_start.sh && sudo bash quick_start.sh
```

### 2. 通过1Panel安装必需软件
- **应用商店** → 安装：`Maven`、`Python 3.10`、`MySQL 8.0`、`Redis`
- **运行环境** → 创建Java 17运行环境（推荐）或通过应用商店安装OpenJDK

### 3. 上传项目
- **文件** → 上传到 `/opt/quant-trading-system`

### 4. 部署方式选择

**方式A：使用1Panel运行环境（推荐，容器化）**
1. **运行环境** → **创建运行环境**
2. 配置：Java 17、项目目录、启动命令、端口、环境变量
3. 详细步骤：参考 `docs/1panel_runtime_environment_guide.md`

**方式B：使用Systemd服务（传统方式）**
```bash
cd /opt/quant-trading-system/deploy
chmod +x *.sh
sudo ./quick_deploy.sh
```

---

## 📋 详细步骤清单

### 阶段1：环境准备

- [ ] 安装1Panel并登录
- [ ] 安装Java 17（**运行环境**创建或应用商店安装）
- [ ] 安装Maven
- [ ] 安装Python 3.8+
- [ ] 安装MySQL 8.0
- [ ] 安装Redis
- [ ] 运行环境检查：`sudo ./deploy/check_environment.sh`

### 阶段2：数据库配置

- [ ] 创建数据库 `quant_trading`
- [ ] 记录MySQL root密码
- [ ] 测试连接：`mysql -u root -p`

### 阶段3：项目部署

- [ ] 上传项目到 `/opt/quant-trading-system`
- [ ] 配置环境变量：`sudo nano /etc/quant-trading.env`
- [ ] 构建Java后端：`cd java-backend && mvn clean package -Pprod`
- [ ] 安装Python依赖：`cd python-strategies && pip3 install -r requirements.txt`

### 阶段4：服务配置

**如果使用1Panel运行环境：**
- [ ] 在1Panel中创建Java运行环境
- [ ] 配置端口映射（8080）
- [ ] 配置环境变量（DB_PASSWORD、JWT_SECRET等）
- [ ] 启动运行环境

**如果使用Systemd服务：**
- [ ] 运行服务配置脚本：`sudo ./deploy/setup_services.sh`
- [ ] 启动服务：`sudo systemctl start quant-trading-backend quant-trading-python`
- [ ] 设置开机自启：`sudo systemctl enable quant-trading-backend quant-trading-python`

### 阶段5：验证

- [ ] 检查Java后端：`curl http://localhost:8080/api/health`
- [ ] 检查Python服务：`curl http://localhost:8000/health`
- [ ] 查看服务状态：`sudo systemctl status quant-trading-backend`

---

## 🔧 常用命令

### 服务管理

**如果使用1Panel运行环境：**
- 在1Panel界面中管理：**运行环境** → 选择运行环境 → 启动/停止/重启
- 查看日志：**运行环境** → 选择运行环境 → **日志**标签

**如果使用Systemd服务：**
```bash
# 启动服务
sudo systemctl start quant-trading-backend
sudo systemctl start quant-trading-python

# 停止服务
sudo systemctl stop quant-trading-backend
sudo systemctl stop quant-trading-python

# 重启服务
sudo systemctl restart quant-trading-backend
sudo systemctl restart quant-trading-python

# 查看状态
sudo systemctl status quant-trading-backend
sudo systemctl status quant-trading-python

# 查看日志
sudo journalctl -u quant-trading-backend -f
sudo journalctl -u quant-trading-python -f
```

### 端口检查
```bash
# 检查端口占用
sudo netstat -tlnp | grep -E '8080|8000|3306|6379'
# 或
sudo ss -tlnp | grep -E '8080|8000|3306|6379'
```

### 数据库操作
```bash
# 登录MySQL
mysql -u root -p

# 查看数据库
SHOW DATABASES;

# 查看表
USE quant_trading;
SHOW TABLES;
```

### 项目更新
```bash
# 更新Java后端
cd /opt/quant-trading-system/java-backend
git pull  # 或上传新文件
mvn clean package -Pprod -DskipTests
sudo systemctl restart quant-trading-backend

# 更新Python服务
cd /opt/quant-trading-system/python-strategies
git pull  # 或上传新文件
source venv/bin/activate
pip3 install -r requirements.txt
sudo systemctl restart quant-trading-python
```

---

## 📁 重要文件路径

| 文件/目录 | 路径 | 说明 |
|---------|------|------|
| 项目目录 | `/opt/quant-trading-system` | 项目根目录 |
| Java后端 | `/opt/quant-trading-system/java-backend` | Java项目 |
| Python服务 | `/opt/quant-trading-system/python-strategies` | Python项目 |
| 环境变量 | `/etc/quant-trading.env` | 环境配置 |
| Java服务配置 | `/etc/systemd/system/quant-trading-backend.service` | systemd服务 |
| Python服务配置 | `/etc/systemd/system/quant-trading-python.service` | systemd服务 |
| Java日志 | `/opt/quant-trading-system/java-backend/logs/` | 应用日志 |
| 系统日志 | `journalctl -u quant-trading-*` | systemd日志 |

---

## 🔐 环境变量配置

编辑文件：`sudo nano /etc/quant-trading.env`

```bash
# 数据库配置
DB_PASSWORD=your_mysql_password
DB_USERNAME=root

# Redis配置（如果设置了密码）
REDIS_PASSWORD=

# JWT密钥（重要：使用强随机字符串）
JWT_SECRET=your-very-long-random-secret-key

# Python API地址
PYTHON_API_URL=http://localhost:8000

# 代理配置
PROXY_ENABLED=false
```

---

## 🌐 Nginx反向代理配置

### 通过1Panel配置

1. **网站** → **创建网站**
2. 填写域名或IP
3. **反向代理** → 配置：
   - 后端地址：`http://localhost:8080`
   - 路径：`/api/`

### 手动配置

编辑：`/etc/nginx/sites-available/quant-trading`

```nginx
server {
    listen 80;
    server_name your-domain.com;

    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /ws/ {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

---

## 🛡️ 防火墙配置

### 通过1Panel

**安全** → **防火墙** → 开放端口：
- 80 (HTTP)
- 443 (HTTPS)
- 8080 (Java后端，如直接访问)
- 1Panel面板端口

### 通过命令行

```bash
# Ubuntu/Debian
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 8080/tcp
sudo ufw enable

# CentOS
sudo firewall-cmd --permanent --add-port=80/tcp
sudo firewall-cmd --permanent --add-port=443/tcp
sudo firewall-cmd --reload
```

---

## ❗ 常见问题快速解决

### 服务无法启动

```bash
# 1. 查看日志
sudo journalctl -u quant-trading-backend -n 50

# 2. 检查端口占用
sudo netstat -tlnp | grep 8080

# 3. 检查环境变量
sudo cat /etc/quant-trading.env

# 4. 检查JAR文件
ls -l /opt/quant-trading-system/java-backend/target/*.jar
```

### 数据库连接失败

```bash
# 1. 检查MySQL服务
sudo systemctl status mysql

# 2. 测试连接
mysql -u root -p

# 3. 检查数据库是否存在
mysql -u root -p -e "SHOW DATABASES LIKE 'quant_trading';"
```

### Python依赖安装失败

```bash
# ta-lib安装失败时，使用替代库
pip3 install ta  # 替代ta-lib

# 或安装系统依赖
sudo apt install ta-lib -y  # Ubuntu/Debian
pip3 install TA-Lib
```

### 端口被占用

```bash
# 查找占用进程
sudo lsof -i :8080
sudo lsof -i :8000

# 结束进程
sudo kill -9 [PID]
```

---

## 📊 健康检查

### API健康检查

```bash
# Java后端
curl http://localhost:8080/api/health

# Python服务
curl http://localhost:8000/health

# 查看可用策略
curl http://localhost:8000/api/strategies
```

### 服务状态检查

```bash
# 运行环境检查脚本
sudo ./deploy/check_environment.sh

# 查看系统资源
htop
# 或
top
```

---

## 🔄 备份与恢复

### 数据库备份

```bash
# 手动备份
mysqldump -u root -p quant_trading > backup_$(date +%Y%m%d).sql

# 通过1Panel自动备份
# 数据库 → MySQL → 选择数据库 → 备份 → 设置计划
```

### 项目文件备份

```bash
# 备份项目
tar -czf quant-trading-backup-$(date +%Y%m%d).tar.gz /opt/quant-trading-system

# 恢复
tar -xzf quant-trading-backup-YYYYMMDD.tar.gz -C /
```

---

## 📚 相关文档

- **详细部署指南**：`docs/1panel_deployment_guide.md`
- **部署脚本说明**：`deploy/README.md`
- **架构文档**：`docs/architecture.md`
- **快速开始**：`docs/quick_start.md`

---

## 🆘 获取帮助

1. 查看详细文档：`docs/1panel_deployment_guide.md`
2. 运行环境检查：`sudo ./deploy/check_environment.sh`
3. 查看服务日志：`sudo journalctl -u quant-trading-backend -f`
4. 检查1Panel日志：1Panel界面 → 日志

---

**最后更新：** 2024年

