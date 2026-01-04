# 1Panel部署方案总结

## 📦 已创建的部署文件

### 1. 详细部署指南
**文件：** `docs/1panel_deployment_guide.md`

**内容：**
- 完整的10个章节详细步骤
- 从1Panel安装到服务运行的完整流程
- 包含环境准备、数据库配置、项目部署、服务配置等
- 详细的故障排查和常见问题解答
- 适合第一次部署的新手

### 2. 快速参考文档
**文件：** `docs/deployment_quick_reference.md`

**内容：**
- 快速查阅关键步骤和命令
- 常用命令速查表
- 重要文件路径
- 常见问题快速解决
- 适合有经验的用户快速查阅

### 3. 部署脚本

#### 3.1 环境检查脚本
**文件：** `deploy/check_environment.sh`

**功能：**
- 检查Java、Maven、Python安装
- 检查MySQL、Redis服务状态
- 检查端口占用情况
- 检查磁盘空间和内存
- 检查项目文件完整性
- 生成详细的环境检查报告

**使用方法：**
```bash
chmod +x deploy/check_environment.sh
sudo ./deploy/check_environment.sh
```

#### 3.2 服务配置脚本
**文件：** `deploy/setup_services.sh`

**功能：**
- 自动创建systemd服务文件
- 配置Java后端服务
- 配置Python策略服务
- 启用服务开机自启
- 可选择立即启动服务

**使用方法：**
```bash
chmod +x deploy/setup_services.sh
sudo ./deploy/setup_services.sh
```

#### 3.3 快速部署脚本
**文件：** `deploy/quick_deploy.sh`

**功能：**
- 一键执行完整部署流程
- 自动执行环境检查、数据库配置、构建、服务配置等
- 交互式输入MySQL密码
- 自动化整个部署过程

**使用方法：**
```bash
chmod +x deploy/quick_deploy.sh
sudo ./deploy/quick_deploy.sh
```

#### 3.4 脚本说明文档
**文件：** `deploy/README.md`

**内容：**
- 各脚本的详细说明
- 使用方法和注意事项
- 故障排查指南

---

## 🚀 部署流程概览

### 方式一：使用快速部署脚本（推荐新手）

```bash
# 1. 上传项目到服务器
# 2. 通过1Panel安装基础软件（Java、MySQL、Redis等）
# 3. 运行快速部署脚本
cd /opt/quant-trading-system/deploy
chmod +x *.sh
sudo ./quick_deploy.sh
```

### 方式二：手动部署（推荐有经验用户）

1. **按照详细指南逐步操作**
   - 参考：`docs/1panel_deployment_guide.md`
   - 每个步骤都有详细说明

2. **使用脚本辅助**
   - 环境检查：`sudo ./deploy/check_environment.sh`
   - 服务配置：`sudo ./deploy/setup_services.sh`

---

## 📋 部署前准备清单

### 服务器要求
- [ ] CPU: 2核+（推荐4核）
- [ ] 内存: 4GB+（推荐8GB）
- [ ] 硬盘: 50GB+（推荐100GB SSD）
- [ ] 操作系统: Ubuntu 20.04+ / CentOS 7+ / Debian 10+

### 需要准备的信息
- [ ] 服务器IP地址
- [ ] 服务器root密码或sudo权限
- [ ] 域名（可选，用于HTTPS）
- [ ] MySQL root密码（或准备创建专用用户）

---

## 🔧 部署步骤摘要

### 阶段1：环境准备（约10-15分钟）

1. **安装1Panel**
   ```bash
   curl -sSL https://resource.fit2cloud.com/1panel/package/quick_start.sh -o quick_start.sh && sudo bash quick_start.sh
   ```

2. **通过1Panel安装软件**
   - OpenJDK 17
   - Maven
   - Python 3.10+
   - MySQL 8.0
   - Redis

3. **运行环境检查**
   ```bash
   sudo ./deploy/check_environment.sh
   ```

### 阶段2：项目部署（约5-10分钟）

1. **上传项目**
   - 通过1Panel文件管理器上传到 `/opt/quant-trading-system`
   - 或使用Git克隆

2. **配置环境变量**
   ```bash
   sudo nano /etc/quant-trading.env
   ```

3. **构建和安装**
   - Java后端：`cd java-backend && mvn clean package -Pprod`
   - Python依赖：`cd python-strategies && pip3 install -r requirements.txt`

### 阶段3：服务配置（约5分钟）

1. **运行服务配置脚本**
   ```bash
   sudo ./deploy/setup_services.sh
   ```

2. **启动服务**
   ```bash
   sudo systemctl start quant-trading-backend
   sudo systemctl start quant-trading-python
   ```

3. **验证服务**
   ```bash
   curl http://localhost:8080/api/health
   curl http://localhost:8000/health
   ```

### 阶段4：反向代理（可选，约5分钟）

1. **通过1Panel配置Nginx**
   - 网站 → 创建网站 → 配置反向代理

2. **或手动配置**
   - 参考：`docs/1panel_deployment_guide.md` 第7章

---

## 📚 文档使用指南

### 新手用户
1. **首先阅读**：`docs/1panel_deployment_guide.md`
   - 完整的详细步骤
   - 每个操作都有说明
   - 包含故障排查

2. **遇到问题**：查看文档中的"常见问题"章节

3. **快速查阅**：`docs/deployment_quick_reference.md`
   - 常用命令
   - 快速解决方案

### 有经验用户
1. **快速参考**：`docs/deployment_quick_reference.md`
   - 直接查看命令和配置

2. **使用脚本**：`deploy/` 目录下的自动化脚本
   - 快速部署：`quick_deploy.sh`
   - 环境检查：`check_environment.sh`
   - 服务配置：`setup_services.sh`

---

## 🎯 关键配置点

### 1. 环境变量文件
**路径：** `/etc/quant-trading.env`

**必须配置：**
- `DB_PASSWORD`: MySQL密码
- `JWT_SECRET`: JWT密钥（使用强随机字符串）

### 2. 数据库配置
- 数据库名：`quant_trading`
- 字符集：`utf8mb4`
- 表结构：自动创建（Flyway迁移）

### 3. 服务端口
- Java后端：`8080`
- Python策略服务：`8000`
- MySQL：`3306`（仅内网）
- Redis：`6379`（仅内网）

### 4. 系统服务
- Java后端：`quant-trading-backend.service`
- Python服务：`quant-trading-python.service`

---

## ✅ 部署验证清单

部署完成后，请确认：

- [ ] 1Panel可以正常访问
- [ ] Java后端服务运行正常（`systemctl status quant-trading-backend`）
- [ ] Python策略服务运行正常（`systemctl status quant-trading-python`）
- [ ] Java API可以访问（`curl http://localhost:8080/api/health`）
- [ ] Python API可以访问（`curl http://localhost:8000/health`）
- [ ] 数据库表已创建（`mysql -u root -p quant_trading -e "SHOW TABLES;"`）
- [ ] 日志正常输出（`journalctl -u quant-trading-backend -n 20`）
- [ ] 服务开机自启已启用（`systemctl is-enabled quant-trading-backend`）

---

## 🆘 获取帮助

### 文档资源
1. **详细部署指南**：`docs/1panel_deployment_guide.md`
2. **快速参考**：`docs/deployment_quick_reference.md`
3. **脚本说明**：`deploy/README.md`

### 故障排查
1. **运行环境检查**：`sudo ./deploy/check_environment.sh`
2. **查看服务日志**：`sudo journalctl -u quant-trading-backend -f`
3. **检查服务状态**：`sudo systemctl status quant-trading-backend`

### 常见问题
- 查看 `docs/1panel_deployment_guide.md` 第10章
- 查看 `docs/deployment_quick_reference.md` 常见问题部分

---

## 📝 后续维护

### 日常维护
- 定期检查服务状态
- 查看日志文件
- 监控系统资源使用

### 更新项目
- 参考 `docs/deployment_quick_reference.md` 中的"项目更新"部分

### 备份策略
- 数据库备份：通过1Panel设置自动备份
- 项目文件备份：定期备份 `/opt/quant-trading-system`

---

## 🎉 完成

恭喜！您已经拥有了完整的1Panel部署方案，包括：
- ✅ 详细的部署指南
- ✅ 快速参考文档
- ✅ 自动化部署脚本
- ✅ 完整的故障排查指南

**开始部署：**
1. 阅读 `docs/1panel_deployment_guide.md`
2. 或直接运行 `deploy/quick_deploy.sh`

**祝部署顺利！** 🚀

