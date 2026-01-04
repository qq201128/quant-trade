#!/bin/bash

# 快速部署脚本
# 自动化执行部署流程

set -e

echo "=========================================="
echo "量化交易系统 - 快速部署脚本"
echo "=========================================="
echo ""
echo "此脚本将执行以下操作："
echo "  1. 检查环境"
echo "  2. 配置数据库"
echo "  3. 构建Java后端"
echo "  4. 安装Python依赖"
echo "  5. 配置系统服务"
echo "  6. 启动服务"
echo ""
read -p "按Enter继续，或Ctrl+C取消..."

# 脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="/opt/quant-trading-system"

# 1. 环境检查
echo ""
echo "=========================================="
echo "步骤 1/6: 环境检查"
echo "=========================================="
if [ -f "$SCRIPT_DIR/check_environment.sh" ]; then
    bash "$SCRIPT_DIR/check_environment.sh"
    if [ $? -ne 0 ]; then
        echo "环境检查失败，请先解决环境问题"
        exit 1
    fi
else
    echo "警告: 环境检查脚本不存在，跳过..."
fi

# 2. 数据库配置
echo ""
echo "=========================================="
echo "步骤 2/6: 数据库配置"
echo "=========================================="
echo "请确保MySQL已安装并运行"
read -p "MySQL root密码: " -s MYSQL_PASSWORD
echo ""

# 创建数据库
mysql -u root -p"$MYSQL_PASSWORD" << EOF
CREATE DATABASE IF NOT EXISTS quant_trading CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
SHOW DATABASES LIKE 'quant_trading';
EOF

if [ $? -eq 0 ]; then
    echo "✓ 数据库创建成功"
else
    echo "✗ 数据库创建失败，请检查MySQL配置"
    exit 1
fi

# 3. 构建Java后端
echo ""
echo "=========================================="
echo "步骤 3/6: 构建Java后端"
echo "=========================================="
if [ ! -d "$PROJECT_DIR/java-backend" ]; then
    echo "错误: Java后端目录不存在: $PROJECT_DIR/java-backend"
    exit 1
fi

cd "$PROJECT_DIR/java-backend"
echo "正在构建Java后端（这可能需要几分钟）..."
mvn clean package -Pprod -DskipTests

if [ $? -eq 0 ]; then
    echo "✓ Java后端构建成功"
else
    echo "✗ Java后端构建失败"
    exit 1
fi

# 4. 安装Python依赖
echo ""
echo "=========================================="
echo "步骤 4/6: 安装Python依赖"
echo "=========================================="
if [ ! -d "$PROJECT_DIR/python-strategies" ]; then
    echo "错误: Python策略目录不存在: $PROJECT_DIR/python-strategies"
    exit 1
fi

cd "$PROJECT_DIR/python-strategies"

# 创建虚拟环境（如果不存在）
if [ ! -d "venv" ]; then
    echo "创建Python虚拟环境..."
    python3 -m venv venv
fi

# 激活虚拟环境并安装依赖
echo "安装Python依赖..."
source venv/bin/activate
pip3 install --upgrade pip
pip3 install -r requirements.txt

# 如果ta-lib安装失败，尝试安装替代库
if ! python3 -c "import talib" 2>/dev/null; then
    echo "警告: ta-lib安装失败，尝试安装替代库..."
    pip3 install ta
fi

if [ $? -eq 0 ]; then
    echo "✓ Python依赖安装成功"
else
    echo "✗ Python依赖安装失败（某些依赖可能缺失，但不影响基本功能）"
fi

# 5. 配置环境变量
echo ""
echo "=========================================="
echo "步骤 5/6: 配置环境变量"
echo "=========================================="
ENV_FILE="/etc/quant-trading.env"

if [ ! -f "$ENV_FILE" ]; then
    echo "创建环境变量文件..."
    cat > "$ENV_FILE" << EOF
# 数据库配置
DB_PASSWORD=$MYSQL_PASSWORD
DB_USERNAME=root

# Redis配置（如果设置了密码）
REDIS_PASSWORD=

# JWT密钥（请修改为强随机字符串）
JWT_SECRET=$(openssl rand -hex 32)

# Python API地址
PYTHON_API_URL=http://localhost:8000

# 代理配置
PROXY_ENABLED=false
EOF
    chmod 600 "$ENV_FILE"
    echo "✓ 环境变量文件已创建: $ENV_FILE"
    echo "  请检查并修改JWT_SECRET等配置"
else
    echo "环境变量文件已存在，跳过创建"
fi

# 6. 配置系统服务
echo ""
echo "=========================================="
echo "步骤 6/6: 配置系统服务"
echo "=========================================="
if [ -f "$SCRIPT_DIR/setup_services.sh" ]; then
    bash "$SCRIPT_DIR/setup_services.sh"
else
    echo "警告: 服务配置脚本不存在，请手动配置"
fi

# 完成
echo ""
echo "=========================================="
echo "部署完成！"
echo "=========================================="
echo ""
echo "服务状态："
systemctl status quant-trading-backend --no-pager -l | head -n 5
echo ""
systemctl status quant-trading-python --no-pager -l | head -n 5
echo ""
echo "下一步："
echo "  1. 检查服务日志: journalctl -u quant-trading-backend -f"
echo "  2. 测试API: curl http://localhost:8080/api/health"
echo "  3. 测试Python服务: curl http://localhost:8000/health"
echo "  4. 配置Nginx反向代理（如需要）"
echo "  5. 配置防火墙规则"
echo ""
echo "详细文档请参考: docs/1panel_deployment_guide.md"
echo ""

