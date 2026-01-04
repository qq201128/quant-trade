#!/bin/bash

# 系统服务配置脚本
# 自动创建和配置systemd服务

set -e

echo "=========================================="
echo "量化交易系统 - 服务配置脚本"
echo "=========================================="
echo ""

PROJECT_DIR="/opt/quant-trading-system"
ENV_FILE="/etc/quant-trading.env"
JAVA_BACKEND_SERVICE="/etc/systemd/system/quant-trading-backend.service"
PYTHON_SERVICE="/etc/systemd/system/quant-trading-python.service"

# 检查是否为root用户
if [ "$EUID" -ne 0 ]; then 
    echo "错误: 请使用sudo运行此脚本"
    exit 1
fi

# 1. 检查项目目录
if [ ! -d "$PROJECT_DIR" ]; then
    echo "错误: 项目目录不存在: $PROJECT_DIR"
    echo "请先上传项目文件"
    exit 1
fi

# 2. 检查环境变量文件
if [ ! -f "$ENV_FILE" ]; then
    echo "警告: 环境变量文件不存在: $ENV_FILE"
    echo "正在创建模板文件..."
    
    cat > "$ENV_FILE" << 'EOF'
# 数据库配置
DB_PASSWORD=your_mysql_password
DB_USERNAME=root

# Redis配置（如果设置了密码）
REDIS_PASSWORD=

# JWT密钥（重要：请使用强随机字符串）
JWT_SECRET=your-very-long-random-secret-key-change-this-in-production

# Python API地址
PYTHON_API_URL=http://localhost:8000

# 代理配置（如果需要）
PROXY_ENABLED=false
EOF
    
    chmod 600 "$ENV_FILE"
    echo "已创建环境变量模板文件: $ENV_FILE"
    echo "请编辑此文件并填入正确的配置值！"
    read -p "按Enter继续（请确保已配置环境变量）..."
fi

# 3. 检查Java JAR文件
JAR_FILE="$PROJECT_DIR/java-backend/target/trading-backend-1.0.0.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "警告: JAR文件不存在: $JAR_FILE"
    echo "正在尝试构建..."
    cd "$PROJECT_DIR/java-backend"
    mvn clean package -Pprod -DskipTests
    if [ ! -f "$JAR_FILE" ]; then
        echo "错误: 构建失败，请手动构建后再运行此脚本"
        exit 1
    fi
    echo "构建成功！"
fi

# 4. 检查Java可执行文件
JAVA_CMD=$(which java)
if [ -z "$JAVA_CMD" ]; then
    echo "错误: 未找到Java，请先安装Java 17"
    exit 1
fi

# 5. 创建Java后端服务
echo "创建Java后端服务..."
cat > "$JAVA_BACKEND_SERVICE" << EOF
[Unit]
Description=Quant Trading Backend Service
After=network.target mysql.service redis.service

[Service]
Type=simple
User=root
WorkingDirectory=$PROJECT_DIR/java-backend
EnvironmentFile=$ENV_FILE
Environment="SPRING_PROFILES_ACTIVE=prod"
ExecStart=$JAVA_CMD -jar $JAR_FILE
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

echo "✓ Java后端服务配置已创建"

# 6. 检查Python环境
PYTHON_VENV="$PROJECT_DIR/python-strategies/venv"
if [ -d "$PYTHON_VENV" ]; then
    UVICORN_CMD="$PYTHON_VENV/bin/uvicorn"
    USE_VENV=true
else
    UVICORN_CMD=$(which uvicorn 2>/dev/null || echo "python3 -m uvicorn")
    USE_VENV=false
fi

# 7. 创建Python策略服务
echo "创建Python策略服务..."
if [ "$USE_VENV" = true ]; then
    cat > "$PYTHON_SERVICE" << EOF
[Unit]
Description=Quant Trading Python Strategy Service
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=$PROJECT_DIR/python-strategies
Environment="PATH=$PYTHON_VENV/bin:/usr/local/bin:/usr/bin:/bin"
ExecStart=$UVICORN_CMD api_server:app --host 0.0.0.0 --port 8000 --workers 4
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF
else
    cat > "$PYTHON_SERVICE" << EOF
[Unit]
Description=Quant Trading Python Strategy Service
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=$PROJECT_DIR/python-strategies
ExecStart=$UVICORN_CMD api_server:app --host 0.0.0.0 --port 8000 --workers 4
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF
fi

echo "✓ Python策略服务配置已创建"

# 8. 重新加载systemd
echo "重新加载systemd配置..."
systemctl daemon-reload
echo "✓ systemd配置已重新加载"

# 9. 启用服务（开机自启）
echo "启用服务（开机自启）..."
systemctl enable quant-trading-backend
systemctl enable quant-trading-python
echo "✓ 服务已设置为开机自启"

# 10. 询问是否立即启动
echo ""
read -p "是否立即启动服务？(y/n): " -n 1 -r
echo ""
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "启动Java后端服务..."
    systemctl start quant-trading-backend
    sleep 2
    if systemctl is-active --quiet quant-trading-backend; then
        echo "✓ Java后端服务启动成功"
    else
        echo "✗ Java后端服务启动失败，请查看日志: journalctl -u quant-trading-backend -n 50"
    fi
    
    echo "启动Python策略服务..."
    systemctl start quant-trading-python
    sleep 2
    if systemctl is-active --quiet quant-trading-python; then
        echo "✓ Python策略服务启动成功"
    else
        echo "✗ Python策略服务启动失败，请查看日志: journalctl -u quant-trading-python -n 50"
    fi
fi

# 11. 显示服务状态
echo ""
echo "=========================================="
echo "服务状态"
echo "=========================================="
systemctl status quant-trading-backend --no-pager -l
echo ""
systemctl status quant-trading-python --no-pager -l

echo ""
echo "=========================================="
echo "配置完成！"
echo "=========================================="
echo ""
echo "常用命令："
echo "  查看Java后端状态: systemctl status quant-trading-backend"
echo "  查看Python服务状态: systemctl status quant-trading-python"
echo "  查看Java后端日志: journalctl -u quant-trading-backend -f"
echo "  查看Python服务日志: journalctl -u quant-trading-python -f"
echo "  重启Java后端: systemctl restart quant-trading-backend"
echo "  重启Python服务: systemctl restart quant-trading-python"
echo ""

