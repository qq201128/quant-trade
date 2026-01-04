# 部署脚本说明

本目录包含用于快速部署量化交易系统的自动化脚本。

## 脚本列表

### 1. `check_environment.sh` - 环境检查脚本

**功能：** 检查服务器环境是否满足部署要求

**使用方法：**
```bash
chmod +x check_environment.sh
sudo ./check_environment.sh
```

**检查项目：**
- Java 17 安装和配置
- Maven 安装
- Python 3.8+ 安装
- MySQL 和 Redis 服务状态
- 端口占用情况
- 磁盘空间和内存
- 项目文件完整性
- 系统服务配置状态

### 2. `setup_services.sh` - 服务配置脚本

**功能：** 自动创建和配置systemd服务

**使用方法：**
```bash
chmod +x setup_services.sh
sudo ./setup_services.sh
```

**执行操作：**
- 检查项目目录和环境变量
- 自动构建Java后端（如需要）
- 创建Java后端systemd服务
- 创建Python策略服务systemd服务
- 启用服务开机自启
- 可选择立即启动服务

### 3. `quick_deploy.sh` - 快速部署脚本

**功能：** 一键执行完整部署流程

**使用方法：**
```bash
chmod +x quick_deploy.sh
sudo ./quick_deploy.sh
```

**执行流程：**
1. 环境检查
2. 数据库配置（创建数据库）
3. 构建Java后端
4. 安装Python依赖
5. 配置环境变量
6. 配置系统服务

**注意：** 此脚本需要交互输入MySQL root密码

## 使用建议

### 第一次部署

1. **先运行环境检查：**
   ```bash
   sudo ./check_environment.sh
   ```
   确保所有必需项都通过

2. **手动配置环境变量：**
   ```bash
   sudo nano /etc/quant-trading.env
   ```
   填入正确的数据库密码、JWT密钥等

3. **运行快速部署：**
   ```bash
   sudo ./quick_deploy.sh
   ```

### 仅配置服务

如果项目已经部署，只需要配置服务：
```bash
sudo ./setup_services.sh
```

### 仅检查环境

定期检查环境状态：
```bash
sudo ./check_environment.sh
```

## 前置条件

- 服务器已安装1Panel（或手动安装所需软件）
- 项目文件已上传到 `/opt/quant-trading-system`
- 具有root或sudo权限

## 故障排查

### 脚本执行失败

1. **检查权限：** 确保使用sudo运行
2. **检查路径：** 确保项目在 `/opt/quant-trading-system`
3. **查看错误信息：** 根据错误提示解决问题

### 服务启动失败

1. **查看日志：**
   ```bash
   sudo journalctl -u quant-trading-backend -n 50
   sudo journalctl -u quant-trading-python -n 50
   ```

2. **检查环境变量：**
   ```bash
   sudo cat /etc/quant-trading.env
   ```

3. **检查端口占用：**
   ```bash
   sudo netstat -tlnp | grep -E '8080|8000'
   ```

### 数据库连接失败

1. **检查MySQL服务：**
   ```bash
   sudo systemctl status mysql
   ```

2. **测试连接：**
   ```bash
   mysql -u root -p
   ```

3. **检查环境变量中的密码是否正确**

## 手动部署

如果脚本无法使用，请参考：
- `../docs/1panel_deployment_guide.md` - 详细的手动部署指南

## 注意事项

1. **安全性：**
   - 环境变量文件包含敏感信息，确保权限为600
   - JWT密钥应使用强随机字符串
   - 生产环境建议使用专用数据库用户

2. **备份：**
   - 部署前建议备份现有配置
   - 定期备份数据库和配置文件

3. **更新：**
   - 更新项目时，先停止服务
   - 重新构建后再重启服务

## 支持

如遇问题，请：
1. 查看详细部署文档：`../docs/1panel_deployment_guide.md`
2. 检查服务日志
3. 运行环境检查脚本诊断问题

