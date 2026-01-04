# 1Panel运行环境配置指南

> 专门针对使用1Panel"运行环境"功能部署Java后端的详细指南

## 📋 概述

1Panel的"运行环境"功能提供了容器化的Java运行环境，可以方便地管理和部署Java应用。本指南将详细说明如何使用此功能部署量化交易系统的Java后端。

## 🎯 前置条件

1. ✅ 1Panel已安装并可以访问
2. ✅ JAR文件已准备好（两种方式）：
   - **方式A**：已在本地构建好JAR文件，直接上传JAR
   - **方式B**：上传完整项目，在服务器上构建JAR
3. ✅ MySQL和Redis已安装并运行

## 📦 准备JAR文件

### 方式A：只上传JAR文件（推荐，如果已构建好）

如果您已经在本地使用 `mvn clean package -Pprod` 构建好了JAR文件：

1. **创建目录结构**
   ```bash
   # 通过1Panel文件管理器或SSH创建
   mkdir -p /opt/quant-trading-system/java-backend/target
   ```

2. **上传JAR文件**
   - 通过1Panel文件管理器上传 `trading-backend-1.0.0.jar`
   - 上传到：`/opt/quant-trading-system/java-backend/target/`

3. **验证文件**
   ```bash
   ls -lh /opt/quant-trading-system/java-backend/target/trading-backend-1.0.0.jar
   ```

**优点：** 快速、文件小、无需在服务器安装Maven  
**适用：** 本地已构建好，直接部署

### 方式B：上传完整项目并在服务器构建

如果您上传了完整项目源代码：

1. **上传项目**
   - 通过1Panel文件管理器上传整个项目
   - 解压到 `/opt/quant-trading-system`

2. **在服务器上构建**
   ```bash
   cd /opt/quant-trading-system/java-backend
   mvn clean package -Pprod -DskipTests
   ```

**优点：** 可以在服务器上重新构建、查看源代码  
**适用：** 需要频繁更新或修改配置

## 📝 详细配置步骤

### 步骤1：进入运行环境管理

1. 登录1Panel管理界面
2. 点击左侧菜单 `运行环境`
3. 点击右上角 `创建运行环境` 按钮

### 步骤2：填写基本信息

在"创建运行环境"表单中填写：

#### 2.1 名称
- **字段**：名称（必填）
- **填写**：`quant-trading-java` 或自定义名称
- **说明**：用于标识此运行环境

#### 2.2 应用类型
- **字段**：应用（必填）
- **选择**：
  - 左侧下拉框：选择 `Java`
  - 右侧下拉框：选择 `17`（Java版本）
- **说明**：选择Java 17运行环境

#### 2.3 项目目录
- **字段**：项目目录（必填）
- **填写**：`/opt/quant-trading-system/java-backend`
- **说明**：
  - 指向包含JAR文件的目录
  - 目录中要包含JAR包，子目录中包含也可
  - **如果JAR在target目录**：确保 `target/trading-backend-1.0.0.jar` 文件存在
  - **如果JAR在根目录**：确保 `trading-backend-1.0.0.jar` 文件存在

#### 2.4 启动命令
- **字段**：启动命令（必填）
- **填写**（根据JAR文件位置选择）：

**如果JAR在target目录：**
```bash
java -jar target/trading-backend-1.0.0.jar --spring.profiles.active=prod
```

**如果JAR在项目根目录：**
```bash
java -jar trading-backend-1.0.0.jar --spring.profiles.active=prod
```

**添加JVM参数（推荐）：**
```bash
java -Xmx1024M -Xms256M -jar target/trading-backend-1.0.0.jar --spring.profiles.active=prod
```

**参数说明：**
- `-Xmx1024M`：最大堆内存1024MB（根据服务器内存调整）
- `-Xms256M`：初始堆内存256MB
- `--spring.profiles.active=prod`：激活生产环境配置

#### 2.5 容器名称
- **字段**：容器名称（必填）
- **填写**：`quant-trading-backend` 或自定义
- **说明**：Docker容器名称

#### 2.6 备注
- **字段**：备注（可选）
- **填写**：`量化交易系统Java后端` 或留空

### 步骤3：配置端口映射

1. 点击表单下方的 `端口` 标签
2. 点击 `添加` 按钮
3. 填写端口信息：
   - **容器端口**：`8080`
   - **主机端口**：`8080`（或自定义，如 `18080`）
   - **协议**：`TCP`
4. 点击 `确认` 保存端口配置

**说明：**
- 容器端口：Java应用在容器内监听的端口
- 主机端口：外部访问的端口
- 如果主机端口设置为 `18080`，访问地址为 `http://your-server-ip:18080`

### 步骤4：配置环境变量

1. 点击 `环境变量` 标签
2. 点击 `添加` 按钮，逐个添加以下环境变量：

#### 必需的环境变量

| 变量名 | 值 | 说明 |
|--------|-----|------|
| `SPRING_PROFILES_ACTIVE` | `prod` | 激活生产环境配置 |
| `DB_PASSWORD` | `your_mysql_password` | MySQL root密码 |
| `DB_USERNAME` | `root` | MySQL用户名 |
| `JWT_SECRET` | `your-very-long-random-secret-key` | JWT密钥（使用强随机字符串） |

#### 可选的环境变量

| 变量名 | 值 | 说明 |
|--------|-----|------|
| `REDIS_PASSWORD` | `your_redis_password` | Redis密码（如果设置了） |
| `REDIS_HOST` | `host.docker.internal` | Redis主机（容器内访问宿主机） |
| `REDIS_PORT` | `6379` | Redis端口 |
| `PYTHON_API_URL` | `http://host.docker.internal:8000` | Python策略服务地址 |
| `PROXY_ENABLED` | `false` | 是否启用代理 |

**重要提示：**
- 容器内访问宿主机服务需要使用 `host.docker.internal` 作为主机名
- JWT_SECRET应使用强随机字符串，例如：`openssl rand -hex 32`

### 步骤5：配置挂载（可选）

如果需要挂载外部目录或文件：

1. 点击 `挂载` 标签
2. 点击 `添加` 按钮
3. 配置挂载：
   - **主机路径**：`/opt/quant-trading-system/java-backend/logs`
   - **容器路径**：`/app/logs`
   - **类型**：`目录` 或 `文件`

**常用挂载：**
- 日志目录：将容器内日志挂载到宿主机，方便查看
- 配置文件：如果需要外部配置文件

### 步骤6：配置主机映射（可选）

如果需要配置hosts映射：

1. 点击 `主机映射` 标签
2. 点击 `添加` 按钮
3. 填写：
   - **主机名**：`mysql.local`
   - **IP地址**：`172.17.0.1`（Docker默认网关）

**说明：**
- 通常不需要配置，使用 `host.docker.internal` 即可访问宿主机

### 步骤7：创建并启动

1. 检查所有配置无误
2. 点击右下角 `确认` 按钮
3. 运行环境会自动创建并启动
4. 等待启动完成（通常几秒钟）

### 步骤8：验证运行状态

1. 返回 `运行环境` 列表
2. 查看运行环境状态：
   - ✅ **运行中**：绿色，表示服务正常运行
   - ❌ **已停止**：灰色，表示服务已停止
   - ⚠️ **异常**：红色，表示服务启动失败

3. 点击运行环境名称，进入详情页面：
   - **概览**：查看基本信息
   - **日志**：查看实时运行日志
   - **监控**：查看资源使用情况（CPU、内存）
   - **终端**：进入容器终端（调试用）

## 🔧 配置数据库连接

由于Java应用运行在容器中，需要特殊配置才能访问宿主机的MySQL。

### 方法一：使用host.docker.internal（推荐）

在 `application-prod.yml` 中配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://host.docker.internal:3306/quant_trading?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
```

### 方法二：使用Docker网络IP

1. 查找Docker默认网关IP：
   ```bash
   docker network inspect bridge | grep Gateway
   ```
   通常为：`172.17.0.1`

2. 在配置中使用该IP：
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://172.17.0.1:3306/quant_trading?...
   ```

### 方法三：使用host网络模式（不推荐）

在创建运行环境时，可以选择使用host网络模式，这样容器直接使用宿主机网络，可以使用 `localhost`。

## 📊 管理运行环境

### 启动服务

1. 进入 `运行环境` 列表
2. 找到目标运行环境
3. 点击 `启动` 按钮

### 停止服务

1. 进入 `运行环境` 列表
2. 找到目标运行环境
3. 点击 `停止` 按钮

### 重启服务

1. 进入 `运行环境` 列表
2. 找到目标运行环境
3. 点击 `重启` 按钮

### 查看日志

1. 点击运行环境名称，进入详情
2. 点击 `日志` 标签
3. 查看实时日志输出

### 编辑配置

1. 点击运行环境名称，进入详情
2. 点击 `编辑` 按钮
3. 修改配置后保存
4. 需要重启服务使配置生效

### 删除运行环境

1. 先停止运行环境
2. 点击 `删除` 按钮
3. 确认删除

## 🐛 故障排查

### 问题1：服务无法启动

**检查步骤：**

1. **查看日志**
   - 进入运行环境详情 → `日志` 标签
   - 查看错误信息

2. **检查JAR文件**
   ```bash
   ls -l /opt/quant-trading-system/java-backend/target/*.jar
   ```
   确保JAR文件存在

3. **检查启动命令**
   - 确认启动命令中的JAR文件路径正确
   - 确认JAR文件名与实际文件名一致

4. **检查端口占用**
   ```bash
   sudo netstat -tlnp | grep 8080
   ```
   如果端口被占用，修改主机端口

### 问题2：无法连接数据库

**检查步骤：**

1. **确认MySQL运行**
   ```bash
   sudo systemctl status mysql
   ```

2. **测试网络连接**
   - 在运行环境详情 → `终端` 标签
   - 进入容器终端
   - 测试连接：`ping host.docker.internal`

3. **检查环境变量**
   - 确认 `DB_PASSWORD` 等环境变量已正确配置
   - 确认 `DB_USERNAME` 正确

4. **检查数据库配置**
   - 确认 `application-prod.yml` 中使用 `host.docker.internal`
   - 确认数据库允许远程连接（如果需要）

### 问题3：容器内无法访问宿主机服务

**解决方案：**

1. **使用host.docker.internal**
   - 这是最简单的方式
   - 在配置中使用 `host.docker.internal` 作为主机名

2. **使用Docker网关IP**
   ```bash
   docker network inspect bridge | grep Gateway
   ```
   使用返回的IP地址

3. **使用host网络模式**
   - 在创建运行环境时选择host网络
   - 这样容器直接使用宿主机网络

### 问题4：内存不足

**解决方案：**

1. **调整JVM参数**
   - 在启动命令中修改 `-Xmx` 参数
   - 例如：`-Xmx512M`（减少内存使用）

2. **检查服务器内存**
   ```bash
   free -h
   ```
   确保有足够内存

3. **限制容器内存**
   - 在运行环境配置中设置内存限制

## 📝 配置示例

### 完整配置示例

**基本信息：**
- 名称：`quant-trading-java`
- 应用：`Java 17`
- 项目目录：`/opt/quant-trading-system/java-backend`
- 启动命令：`java -Xmx1024M -Xms256M -jar target/trading-backend-1.0.0.jar --spring.profiles.active=prod`
- 容器名称：`quant-trading-backend`

**端口配置：**
- 容器端口：`8080`
- 主机端口：`8080`
- 协议：`TCP`

**环境变量：**
```
SPRING_PROFILES_ACTIVE=prod
DB_PASSWORD=your_mysql_password
DB_USERNAME=root
JWT_SECRET=your-very-long-random-secret-key
REDIS_HOST=host.docker.internal
REDIS_PORT=6379
PYTHON_API_URL=http://host.docker.internal:8000
```

## ✅ 验证清单

部署完成后，请确认：

- [ ] 运行环境状态为"运行中"
- [ ] 日志中无错误信息
- [ ] 端口8080可以访问
- [ ] API测试通过：`curl http://localhost:8080/api/health`
- [ ] 数据库连接正常（查看日志确认）
- [ ] 环境变量配置正确

## 🎉 完成

恭喜！您已成功使用1Panel运行环境部署Java后端！

**下一步：**
1. 配置Python策略服务
2. 配置Nginx反向代理
3. 配置防火墙规则
4. 测试完整功能

**相关文档：**
- 详细部署指南：`docs/1panel_deployment_guide.md`
- 快速参考：`docs/deployment_quick_reference.md`

