# 1Panel 服务器部署完整指南

> 本指南将帮助您使用1Panel将量化交易系统完整部署到服务器端，适合第一次部署服务的新手。

## 📋 目录

1. [准备工作](#准备工作)
2. [1Panel安装与配置](#1panel安装与配置)
3. [环境准备](#环境准备)
4. [数据库配置](#数据库配置)
5. [项目部署](#项目部署)
6. [服务启动与验证](#服务启动与验证)
7. [反向代理配置](#反向代理配置)
8. [防火墙与安全](#防火墙与安全)
9. [监控与维护](#监控与维护)
10. [常见问题](#常见问题)

---

## 准备工作

### 1.1 服务器要求

**最低配置：**
- CPU: 2核
- 内存: 4GB
- 硬盘: 50GB
- 操作系统: Ubuntu 20.04+ / CentOS 7+ / Debian 10+

**推荐配置：**
- CPU: 4核
- 内存: 8GB
- 硬盘: 100GB SSD
- 操作系统: Ubuntu 22.04 LTS

### 1.2 需要准备的信息

- 服务器IP地址
- 服务器root密码或sudo权限
- 域名（可选，用于HTTPS）
- 交易所API密钥（部署后配置）

---

## 1Panel安装与配置

### 2.1 安装1Panel

**通过SSH连接到服务器，执行以下命令：**

```bash
# 下载并安装1Panel
curl -sSL https://resource.fit2cloud.com/1panel/package/quick_start.sh -o quick_start.sh && sudo bash quick_start.sh

# 安装完成后，会显示访问地址和初始密码
# 例如：http://your-server-ip:端口号
# 默认端口通常是：端口号（请记录显示的端口）
```

**安装完成后，您会看到类似信息：**
```
===============================================
1Panel 安装完成！
访问地址: http://your-server-ip:端口号
用户名: admin
密码: [随机生成的密码，请保存]
===============================================
```

### 2.2 首次登录配置

1. **打开浏览器访问** `http://your-server-ip:端口号`
2. **使用默认用户名和密码登录**
3. **首次登录会要求修改密码**（请设置一个强密码并妥善保存）
4. **完成基础设置**（时区、语言等）

### 2.3 安全设置（重要）

1. **修改访问端口**（可选但推荐）
   - 进入 `设置` → `安全设置`
   - 修改面板端口为自定义端口（如：12345）
   - 保存后使用新端口访问

2. **配置防火墙**
   - 进入 `安全` → `防火墙`
   - 确保以下端口开放：
     - 1Panel面板端口（默认或自定义）
     - 8080（Java后端）
     - 8000（Python策略服务）
     - 3306（MySQL，仅内网访问）
     - 6379（Redis，仅内网访问）
     - 80/443（HTTP/HTTPS，如果使用反向代理）

---

## 环境准备

### 3.1 安装Java 17

**方法一：通过1Panel运行环境创建（推荐，容器化方式）**

这是1Panel提供的容器化运行环境管理功能，适合在容器中运行Java应用：

1. 进入 `运行环境` → `创建运行环境`
2. 填写配置信息：
   - **名称**：`quant-trading-java`（自定义名称）
   - **应用**：选择 `Java` → `17`（选择Java版本）
   - **项目目录**：`/opt/quant-trading-system/java-backend`（指向包含JAR文件的目录）
   - **启动命令**：`java -jar target/trading-backend-1.0.0.jar --spring.profiles.active=prod`
     - 如果JAR文件在子目录，使用：`java -jar target/trading-backend-1.0.0.jar`
     - 可以添加JVM参数：`java -Xmx1024M -Xms256M -jar target/trading-backend-1.0.0.jar --spring.profiles.active=prod`
   - **容器名称**：`quant-trading-backend`（自定义）
   - **备注**：`量化交易系统Java后端`（可选）
3. **配置端口**（点击"端口"标签）：
   - 点击 `添加`
   - **容器端口**：`8080`
   - **主机端口**：`8080`（或自定义，如`18080`）
   - **协议**：`TCP`
4. **配置环境变量**（点击"环境变量"标签）：
   - 点击 `添加`，添加以下环境变量：
     - `SPRING_PROFILES_ACTIVE` = `prod`
     - `DB_PASSWORD` = `your_mysql_password`
     - `DB_USERNAME` = `root`
     - `JWT_SECRET` = `your-jwt-secret-key`
     - `PYTHON_API_URL` = `http://host.docker.internal:8000`（如果Python服务在宿主机）
     - `REDIS_HOST` = `host.docker.internal`（如果Redis在宿主机）
     - `REDIS_PORT` = `6379`
5. 点击 `确认` 创建运行环境

**注意：**
- 使用容器方式时，访问宿主机服务需要使用 `host.docker.internal` 作为主机名
- 如果MySQL也在容器中，需要配置网络连接
- 这种方式会自动管理Java进程，无需手动配置systemd服务

**方法二：通过1Panel应用商店安装（传统方式）**

1. 进入 `应用商店`
2. 搜索 `OpenJDK` 或 `Java`
3. 选择 `OpenJDK 17` 并安装
4. 安装完成后，记录安装路径（通常在 `/usr/lib/jvm/java-17-openjdk`）

**方法三：通过命令行安装**

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-17-jdk -y

# CentOS/RHEL
sudo yum install java-17-openjdk-devel -y

# 验证安装
java -version
# 应该显示：openjdk version "17.x.x"
```

**配置JAVA_HOME环境变量：**

```bash
# 查找Java安装路径
sudo update-alternatives --config java
# 或
which java

# 编辑环境变量文件
sudo nano /etc/environment

# 添加以下内容（根据实际路径修改）
JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
PATH="$PATH:$JAVA_HOME/bin"

# 使环境变量生效
source /etc/environment

# 验证
echo $JAVA_HOME
```

### 3.2 安装Maven

**通过1Panel应用商店：**

1. 进入 `应用商店`
2. 搜索 `Maven`
3. 安装 `Apache Maven`

**或通过命令行：**

```bash
# 下载Maven
cd /tmp
wget https://dlcdn.apache.org/maven/maven-3/3.9.5/binaries/apache-maven-3.9.5-bin.tar.gz

# 解压
sudo tar -xzf apache-maven-3.9.5-bin.tar.gz -C /opt

# 创建软链接
sudo ln -s /opt/apache-maven-3.9.5 /opt/maven

# 配置环境变量
sudo nano /etc/environment
# 添加：
MAVEN_HOME="/opt/maven"
PATH="$PATH:$MAVEN_HOME/bin"

# 使环境变量生效
source /etc/environment

# 验证
mvn -version
```

### 3.3 安装Python 3.8+

**通过1Panel应用商店：**

1. 进入 `应用商店`
2. 搜索 `Python`
3. 安装 `Python 3.10` 或更高版本

**或通过命令行：**

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install python3 python3-pip python3-venv -y

# CentOS/RHEL
sudo yum install python3 python3-pip -y

# 验证
python3 --version
pip3 --version
```

### 3.4 安装MySQL

**通过1Panel应用商店（推荐）：**

1. 进入 `应用商店`
2. 搜索 `MySQL`
3. 选择 `MySQL 8.0` 并安装
4. **重要：记录root密码**（安装时会显示）

**安装后配置：**

1. 进入 `数据库` → `MySQL`
2. 点击 `root` 用户，记录密码或修改密码
3. 确保MySQL服务正在运行

### 3.5 安装Redis

**通过1Panel应用商店：**

1. 进入 `应用商店`
2. 搜索 `Redis`
3. 选择 `Redis` 并安装

**验证Redis运行：**

```bash
# 测试Redis连接
redis-cli ping
# 应该返回：PONG
```

---

## 数据库配置

### 4.1 创建数据库

**方法一：通过1Panel界面**

1. 进入 `数据库` → `MySQL`
2. 点击 `创建数据库`
3. 填写信息：
   - **数据库名**: `quant_trading`
   - **字符集**: `utf8mb4`
   - **排序规则**: `utf8mb4_unicode_ci`
4. 点击 `确认`

**方法二：通过命令行**

```bash
# 登录MySQL（使用1Panel显示的root密码）
mysql -u root -p

# 创建数据库
CREATE DATABASE quant_trading CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 创建专用用户（可选，更安全）
CREATE USER 'quant_user'@'localhost' IDENTIFIED BY 'your_strong_password';
GRANT ALL PRIVILEGES ON quant_trading.* TO 'quant_user'@'localhost';
FLUSH PRIVILEGES;

# 退出
EXIT;
```

### 4.2 导入数据库表结构

数据库表结构会在Java应用首次启动时自动创建（Flyway迁移），但您也可以手动导入：

```bash
# 进入项目目录（稍后会部署）
cd /opt/quant-trading-system/java-backend/src/main/resources/db/migration

# 登录MySQL
mysql -u root -p quant_trading

# 在MySQL中执行（或使用source命令）
source V1__create_users_table.sql;
source V2__add_exchange_configs_table.sql;
source V3__add_strategy_configs_table.sql;
source V4__add_strategy_enabled_and_exchange_type.sql;
```

---

## 项目部署

### 5.1 上传项目文件

根据您的情况，有两种上传方式：

**方式A：只上传JAR文件（如果已在本地构建好）**

如果您已经在本地使用 `mvn clean package` 构建好了JAR文件：

1. 进入 `文件`
2. 创建目录结构：
   ```
   /opt/quant-trading-system/
   └── java-backend/
       └── target/
           └── trading-backend-1.0.0.jar
   ```
3. 上传JAR文件到 `/opt/quant-trading-system/java-backend/target/` 目录
4. 确保JAR文件名为 `trading-backend-1.0.0.jar`（或修改启动命令中的文件名）

**优点：** 上传速度快，文件小  
**缺点：** 无法在服务器上重新构建，无法查看源代码

**方式B：上传完整项目（推荐）**

上传整个项目源代码，在服务器上构建：

1. 进入 `文件`
2. 创建项目目录：`/opt/quant-trading-system`
3. 使用 `上传` 功能上传项目压缩包（包含所有源代码）
4. 解压到 `/opt/quant-trading-system`
5. 在服务器上构建JAR文件（见5.4节）

**优点：** 可以在服务器上重新构建、查看源代码、修改配置  
**缺点：** 上传文件较大

**推荐：** 如果第一次部署，建议上传完整项目，方便后续维护和更新。

**方法二：通过1Panel文件管理器上传完整项目**

**方法三：通过Git（推荐）**

```bash
# 安装Git
sudo apt install git -y  # Ubuntu/Debian
# 或
sudo yum install git -y  # CentOS

# 克隆项目（如果有Git仓库）
cd /opt
git clone [your-git-repo-url] quant-trading-system

# 或直接创建目录并上传文件
sudo mkdir -p /opt/quant-trading-system
cd /opt/quant-trading-system
# 然后通过1Panel文件管理器上传项目文件
```

**方法四：通过SCP（从本地电脑）**

```bash
# 在本地电脑执行（Windows使用PowerShell或Git Bash）
scp -r ./quant-trading-system root@your-server-ip:/opt/
```

### 5.2 项目目录结构

确保项目结构如下：

```
/opt/quant-trading-system/
├── java-backend/
│   ├── pom.xml
│   ├── src/
│   └── ...
├── python-strategies/
│   ├── api_server.py
│   ├── requirements.txt
│   ├── strategies/
│   └── ...
└── docs/
```

### 5.3 配置Java后端

**5.3.1 修改生产环境配置**

```bash
# 编辑生产环境配置文件
nano /opt/quant-trading-system/java-backend/src/main/resources/application-prod.yml
```

**修改以下配置（根据实际情况）：**

**如果使用1Panel运行环境（容器化方式）：**

```yaml
# 数据库配置（容器内访问宿主机MySQL）
spring:
  datasource:
    url: jdbc:mysql://host.docker.internal:3306/quant_trading?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root  # 或使用专用用户 quant_user
    password: ${DB_PASSWORD}  # 使用环境变量，更安全

# Redis配置（容器内访问宿主机Redis）
  data:
    redis:
      host: host.docker.internal
      port: 6379
      password: ${REDIS_PASSWORD:}  # 如果Redis设置了密码

# Python策略服务地址（容器内访问宿主机Python服务）
python:
  strategy:
    api:
      url: http://host.docker.internal:8000
```

**如果使用传统方式（Systemd服务）：**

```yaml
# 数据库配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/quant_trading?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root  # 或使用专用用户 quant_user
    password: ${DB_PASSWORD}  # 使用环境变量，更安全

# Redis配置
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD:}  # 如果Redis设置了密码

# Python策略服务地址（如果Python服务在同一服务器）
python:
  strategy:
    api:
      url: http://localhost:8000
```

**重要提示：**
- 使用1Panel运行环境时，容器内访问宿主机服务需要使用 `host.docker.internal` 作为主机名
- 如果MySQL/Redis也在容器中，需要配置Docker网络连接
- 环境变量需要在1Panel运行环境的"环境变量"标签中配置

**5.3.2 设置环境变量**

创建环境变量文件：

```bash
sudo nano /etc/quant-trading.env
```

添加以下内容：

```bash
# 数据库配置
DB_PASSWORD=your_mysql_password
DB_USERNAME=root

# Redis配置（如果设置了密码）
REDIS_PASSWORD=your_redis_password

# JWT密钥（重要：请使用强随机字符串）
JWT_SECRET=your-very-long-random-secret-key-change-this-in-production

# Python API地址
PYTHON_API_URL=http://localhost:8000

# 代理配置（如果需要）
PROXY_ENABLED=false
```

**设置文件权限：**

```bash
sudo chmod 600 /etc/quant-trading.env
```

### 5.4 构建Java后端

**如果您上传了完整项目，需要在服务器上构建JAR文件：**

```bash
cd /opt/quant-trading-system/java-backend

# 清理并构建项目
mvn clean package -Pprod -DskipTests

# 构建成功后，JAR文件在：
# target/trading-backend-1.0.0.jar
```

**如果构建失败，检查：**
- Java版本是否正确（`java -version`）
- Maven是否正确安装（`mvn -version`）
- 网络连接（Maven需要下载依赖）

**如果您只上传了JAR文件，可以跳过此步骤，直接进入5.5节配置Python服务。**

### 5.5 配置Python策略服务

**5.5.1 安装Python依赖**

```bash
cd /opt/quant-trading-system/python-strategies

# 创建虚拟环境（推荐）
python3 -m venv venv
source venv/bin/activate

# 安装依赖
pip3 install -r requirements.txt

# 注意：如果ta-lib安装失败，可以跳过或使用替代方案
# pip3 install ta  # 替代ta-lib
```

**5.5.2 配置Python服务**

Python服务默认配置即可，无需额外配置。确保 `api_server.py` 中的端口为 `8000`。

---

## 服务启动与验证

### 6.1 启动服务

根据您选择的Java安装方式，有两种启动方式：

**方式一：使用1Panel运行环境（推荐，如果使用容器化方式）**

如果您通过1Panel运行环境创建了Java环境：

1. **确保JAR文件已构建**
   ```bash
   cd /opt/quant-trading-system/java-backend
   mvn clean package -Pprod -DskipTests
   ```

2. **在1Panel中启动运行环境**
   - 进入 `运行环境`
   - 找到您创建的Java运行环境（如：`quant-trading-java`）
   - 点击 `启动` 按钮
   - 服务会自动启动并运行

3. **查看运行状态**
   - 在运行环境列表中查看状态（运行中/已停止）
   - 点击运行环境名称，可以查看：
     - 运行日志
     - 资源使用情况
     - 端口映射

4. **管理服务**
   - **启动**：点击 `启动` 按钮
   - **停止**：点击 `停止` 按钮
   - **重启**：点击 `重启` 按钮
   - **查看日志**：点击运行环境名称 → `日志` 标签

5. **配置自动启动**
   - 在运行环境详情页面，可以设置开机自启
   - 或通过1Panel的 `计划任务` 配置启动脚本

**方式二：使用Systemd服务（传统方式）**

如果您通过命令行或应用商店安装了Java，使用以下方式：

### 6.2 创建系统服务（Systemd）

**6.2.1 创建Java后端服务**

```bash
sudo nano /etc/systemd/system/quant-trading-backend.service
```

添加以下内容：

```ini
[Unit]
Description=Quant Trading Backend Service
After=network.target mysql.service redis.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/quant-trading-system/java-backend
EnvironmentFile=/etc/quant-trading.env
Environment="SPRING_PROFILES_ACTIVE=prod"
ExecStart=/usr/bin/java -jar /opt/quant-trading-system/java-backend/target/trading-backend-1.0.0.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

**6.2.2 创建Python策略服务**

```bash
sudo nano /etc/systemd/system/quant-trading-python.service
```

添加以下内容：

```ini
[Unit]
Description=Quant Trading Python Strategy Service
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/quant-trading-system/python-strategies
Environment="PATH=/opt/quant-trading-system/python-strategies/venv/bin:/usr/local/bin:/usr/bin:/bin"
ExecStart=/opt/quant-trading-system/python-strategies/venv/bin/uvicorn api_server:app --host 0.0.0.0 --port 8000 --workers 4
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

**如果没有使用虚拟环境，使用：**

```ini
ExecStart=/usr/bin/python3 /opt/quant-trading-system/python-strategies/api_server.py
```

### 6.3 启动服务（Systemd方式）

```bash
# 重新加载systemd配置
sudo systemctl daemon-reload

# 启动Java后端
sudo systemctl start quant-trading-backend
sudo systemctl enable quant-trading-backend  # 开机自启

# 启动Python策略服务
sudo systemctl start quant-trading-python
sudo systemctl enable quant-trading-python  # 开机自启

# 检查服务状态
sudo systemctl status quant-trading-backend
sudo systemctl status quant-trading-python
```

### 6.4 查看日志

```bash
# 查看Java后端日志
sudo journalctl -u quant-trading-backend -f

# 查看Python服务日志
sudo journalctl -u quant-trading-python -f

# 或查看应用日志文件
tail -f /opt/quant-trading-system/java-backend/logs/trading-backend-prod.log
```

### 6.5 验证服务

**6.5.1 检查Java后端**

```bash
# 检查端口是否监听
sudo netstat -tlnp | grep 8080
# 或
sudo ss -tlnp | grep 8080

# 测试API
curl http://localhost:8080/api/health
# 或
curl http://your-server-ip:8080/api/health
```

**6.5.2 检查Python策略服务**

```bash
# 检查端口是否监听
sudo netstat -tlnp | grep 8000

# 测试API
curl http://localhost:8000/health
# 应该返回：{"status":"ok"}

# 查看可用策略
curl http://localhost:8000/api/strategies
```

**6.5.3 检查数据库连接**

```bash
# 登录MySQL检查表是否创建
mysql -u root -p quant_trading
SHOW TABLES;
# 应该看到：users, exchange_configs, strategy_configs 等表
```

---

## 反向代理配置

### 7.1 安装Nginx

**通过1Panel应用商店：**

1. 进入 `应用商店`
2. 搜索 `Nginx`
3. 安装 `Nginx`

### 7.2 配置Nginx反向代理

**方法一：通过1Panel界面**

1. 进入 `网站` → `创建网站`
2. 填写域名（或使用IP）
3. 在 `反向代理` 中配置：
   - **后端地址**: `http://localhost:8080`
   - **路径**: `/`

**方法二：手动配置**

```bash
sudo nano /etc/nginx/sites-available/quant-trading
```

添加以下配置：

```nginx
server {
    listen 80;
    server_name your-domain.com;  # 或使用服务器IP

    # Java后端API
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # WebSocket支持
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # WebSocket连接
    location /ws/ {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # Python策略服务（如果需要外部访问）
    location /python-api/ {
        proxy_pass http://localhost:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

**启用配置：**

```bash
# 创建软链接
sudo ln -s /etc/nginx/sites-available/quant-trading /etc/nginx/sites-enabled/

# 测试配置
sudo nginx -t

# 重启Nginx
sudo systemctl restart nginx
```

### 7.3 配置HTTPS（可选但推荐）

**通过1Panel申请SSL证书：**

1. 进入 `网站` → 选择您的网站
2. 点击 `SSL`
3. 选择 `Let's Encrypt` 免费证书
4. 填写域名邮箱，申请证书
5. 启用 `强制HTTPS`

---

## 防火墙与安全

### 8.1 配置防火墙

**通过1Panel：**

1. 进入 `安全` → `防火墙`
2. 确保以下端口规则：
   - **入站规则**：
     - 80 (HTTP)
     - 443 (HTTPS)
     - 8080 (Java后端，如果直接访问)
     - 1Panel面板端口
   - **出站规则**：全部允许

**或通过命令行（UFW）：**

```bash
# Ubuntu/Debian
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 8080/tcp
sudo ufw allow [1panel-port]/tcp
sudo ufw enable
```

### 8.2 安全建议

1. **修改默认密码**
   - MySQL root密码
   - 1Panel登录密码
   - 服务器root密码

2. **禁用不必要的服务**
   - 只开放必要的端口

3. **定期更新系统**
   ```bash
   sudo apt update && sudo apt upgrade -y  # Ubuntu/Debian
   sudo yum update -y  # CentOS
   ```

4. **配置自动备份**
   - 通过1Panel配置数据库自动备份
   - 备份路径：`/opt/1panel/db/backup/`

5. **监控日志**
   - 定期检查应用日志
   - 检查系统日志：`sudo journalctl -xe`

---

## 监控与维护

### 9.1 服务管理命令

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
sudo journalctl -u quant-trading-backend -n 100
sudo journalctl -u quant-trading-python -n 100
```

### 9.2 性能监控

**通过1Panel：**

1. 进入 `监控` → `系统监控`
2. 查看CPU、内存、磁盘使用情况

**或使用命令行：**

```bash
# 查看系统资源
htop
# 或
top

# 查看Java进程
ps aux | grep java

# 查看Python进程
ps aux | grep python
```

### 9.3 日志管理

```bash
# 查看Java应用日志
tail -f /opt/quant-trading-system/java-backend/logs/trading-backend-prod.log

# 清理旧日志（1Panel会自动管理，也可手动）
# 日志保留策略在 application-prod.yml 中配置
```

### 9.4 备份策略

**数据库备份：**

通过1Panel：
1. 进入 `数据库` → `MySQL`
2. 选择 `quant_trading` 数据库
3. 点击 `备份` → 设置自动备份计划

**项目文件备份：**

```bash
# 创建备份脚本
sudo nano /opt/backup-quant-trading.sh
```

```bash
#!/bin/bash
BACKUP_DIR="/opt/backups/quant-trading"
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p $BACKUP_DIR

# 备份项目文件
tar -czf $BACKUP_DIR/project_$DATE.tar.gz /opt/quant-trading-system

# 备份数据库（需要MySQL密码）
mysqldump -u root -p[password] quant_trading > $BACKUP_DIR/db_$DATE.sql

# 删除7天前的备份
find $BACKUP_DIR -type f -mtime +7 -delete

echo "备份完成: $DATE"
```

```bash
# 设置执行权限
sudo chmod +x /opt/backup-quant-trading.sh

# 添加到定时任务（每天凌晨2点备份）
sudo crontab -e
# 添加：0 2 * * * /opt/backup-quant-trading.sh
```

---

## 常见问题

### 10.1 Java服务启动失败

**问题：服务无法启动**

```bash
# 检查日志
sudo journalctl -u quant-trading-backend -n 50

# 常见原因：
# 1. 数据库连接失败 - 检查 application-prod.yml 中的数据库配置
# 2. 端口被占用 - 检查：sudo netstat -tlnp | grep 8080
# 3. Java版本不对 - 检查：java -version（需要17+）
# 4. JAR文件不存在 - 检查：ls -l /opt/quant-trading-system/java-backend/target/
```

**解决方案：**

```bash
# 如果端口被占用
sudo lsof -i :8080
sudo kill -9 [PID]

# 如果数据库连接失败
mysql -u root -p
# 检查数据库是否存在，用户权限是否正确
```

### 10.2 Python服务启动失败

**问题：Python依赖安装失败**

```bash
# 特别是ta-lib，如果安装失败：
# 方法1：使用替代库
pip3 install ta  # 替代ta-lib

# 方法2：安装系统依赖后重试
# Ubuntu/Debian
sudo apt install ta-lib -y
pip3 install TA-Lib

# CentOS
sudo yum install ta-lib-devel -y
pip3 install TA-Lib
```

**问题：端口被占用**

```bash
# 检查端口
sudo netstat -tlnp | grep 8000
sudo kill -9 [PID]
```

### 10.3 数据库连接问题

**问题：无法连接MySQL**

```bash
# 检查MySQL服务
sudo systemctl status mysql

# 检查MySQL是否监听
sudo netstat -tlnp | grep 3306

# 测试连接
mysql -u root -p

# 检查用户权限
mysql -u root -p
SELECT User, Host FROM mysql.user;
```

### 10.4 WebSocket连接失败

**问题：前端无法连接WebSocket**

1. **检查Nginx配置**：确保WebSocket代理配置正确
2. **检查防火墙**：确保8080端口开放
3. **检查Java后端日志**：查看WebSocket连接错误

### 10.5 性能问题

**问题：服务响应慢**

1. **检查系统资源**：`htop` 查看CPU和内存
2. **检查数据库连接池**：调整 `application-prod.yml` 中的连接池大小
3. **检查Redis**：确保Redis正常运行
4. **检查日志**：查看是否有错误或警告

### 10.6 更新项目

**更新Java后端：**

```bash
cd /opt/quant-trading-system/java-backend
git pull  # 如果有Git
# 或重新上传新文件

# 重新构建
mvn clean package -Pprod -DskipTests

# 重启服务
sudo systemctl restart quant-trading-backend
```

**更新Python服务：**

```bash
cd /opt/quant-trading-system/python-strategies
git pull  # 如果有Git

# 更新依赖（如果有新依赖）
source venv/bin/activate
pip3 install -r requirements.txt

# 重启服务
sudo systemctl restart quant-trading-python
```

---

## 📝 部署检查清单

部署完成后，请确认以下项目：

- [ ] 1Panel已安装并可以访问
- [ ] Java 17已安装并配置
- [ ] Maven已安装
- [ ] Python 3.8+已安装
- [ ] MySQL已安装并运行
- [ ] Redis已安装并运行
- [ ] 数据库 `quant_trading` 已创建
- [ ] 项目文件已上传到 `/opt/quant-trading-system`
- [ ] Java后端已构建（JAR文件存在）
- [ ] Python依赖已安装
- [ ] 环境变量文件已配置（`/etc/quant-trading.env`）
- [ ] 系统服务已创建并启动
- [ ] Java后端服务运行正常（端口8080）
- [ ] Python策略服务运行正常（端口8000）
- [ ] 数据库表已创建
- [ ] Nginx反向代理已配置（如需要）
- [ ] 防火墙规则已配置
- [ ] 日志可以正常查看
- [ ] 备份策略已配置

---

## 🎉 部署完成

恭喜！您的量化交易系统已经成功部署到服务器。

**下一步：**

1. **配置交易所API**：通过前端或API配置交易所密钥
2. **测试功能**：创建用户、配置策略、测试交易
3. **监控运行**：定期检查日志和系统资源
4. **优化性能**：根据实际使用情况调整配置

**获取帮助：**

- 查看项目文档：`/opt/quant-trading-system/docs/`
- 查看应用日志：`sudo journalctl -u quant-trading-backend -f`
- 1Panel官方文档：https://1panel.cn/docs/

---

**最后更新：** 2024年
**适用版本：** 1Panel 最新版本

