#!/bin/bash

# 环境检查脚本
# 用于检查服务器环境是否满足部署要求

echo "=========================================="
echo "量化交易系统 - 环境检查脚本"
echo "=========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查结果统计
PASSED=0
FAILED=0
WARNINGS=0

# 检查函数
check_item() {
    local name=$1
    local command=$2
    local required=$3
    
    echo -n "检查 $name... "
    
    if eval "$command" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ 通过${NC}"
        ((PASSED++))
        return 0
    else
        if [ "$required" = "required" ]; then
            echo -e "${RED}✗ 失败（必需）${NC}"
            ((FAILED++))
            return 1
        else
            echo -e "${YELLOW}⚠ 未安装（可选）${NC}"
            ((WARNINGS++))
            return 0
        fi
    fi
}

# 1. 检查操作系统
echo "【系统信息】"
echo "操作系统: $(cat /etc/os-release | grep PRETTY_NAME | cut -d '"' -f 2)"
echo "内核版本: $(uname -r)"
echo ""

# 2. 检查Java
echo "【Java环境】"
if check_item "Java 17" "java -version 2>&1 | grep 'openjdk version \"17'" "required"; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1)
    echo "  版本信息: $JAVA_VERSION"
    
    # 检查JAVA_HOME
    if [ -z "$JAVA_HOME" ]; then
        echo -e "  ${YELLOW}警告: JAVA_HOME 未设置${NC}"
        ((WARNINGS++))
    else
        echo -e "  ${GREEN}JAVA_HOME: $JAVA_HOME${NC}"
    fi
fi
echo ""

# 3. 检查Maven
echo "【构建工具】"
if check_item "Maven" "mvn -version" "required"; then
    MVN_VERSION=$(mvn -version | head -n 1)
    echo "  版本信息: $MVN_VERSION"
fi
echo ""

# 4. 检查Python
echo "【Python环境】"
if check_item "Python 3.8+" "python3 --version" "required"; then
    PYTHON_VERSION=$(python3 --version)
    echo "  版本信息: $PYTHON_VERSION"
    
    # 检查pip
    check_item "pip3" "pip3 --version" "required"
fi
echo ""

# 5. 检查数据库
echo "【数据库服务】"
check_item "MySQL" "systemctl is-active --quiet mysql || systemctl is-active --quiet mysqld" "required"
check_item "Redis" "systemctl is-active --quiet redis || redis-cli ping" "required"
echo ""

# 6. 检查网络端口
echo "【端口检查】"
check_port() {
    local port=$1
    local name=$2
    if netstat -tlnp 2>/dev/null | grep -q ":$port " || ss -tlnp 2>/dev/null | grep -q ":$port "; then
        echo -e "  端口 $port ($name): ${YELLOW}⚠ 已被占用${NC}"
        ((WARNINGS++))
    else
        echo -e "  端口 $port ($name): ${GREEN}✓ 可用${NC}"
        ((PASSED++))
    fi
}

check_port 8080 "Java后端"
check_port 8000 "Python策略服务"
check_port 3306 "MySQL"
check_port 6379 "Redis"
echo ""

# 7. 检查磁盘空间
echo "【磁盘空间】"
DISK_USAGE=$(df -h / | tail -1 | awk '{print $5}' | sed 's/%//')
if [ "$DISK_USAGE" -lt 80 ]; then
    echo -e "  根目录使用率: ${GREEN}${DISK_USAGE}%${NC} ✓"
    ((PASSED++))
elif [ "$DISK_USAGE" -lt 90 ]; then
    echo -e "  根目录使用率: ${YELLOW}${DISK_USAGE}%${NC} ⚠"
    ((WARNINGS++))
else
    echo -e "  根目录使用率: ${RED}${DISK_USAGE}%${NC} ✗"
    ((FAILED++))
fi
echo ""

# 8. 检查内存
echo "【内存信息】"
TOTAL_MEM=$(free -g | awk '/^Mem:/{print $2}')
AVAIL_MEM=$(free -g | awk '/^Mem:/{print $7}')
echo "  总内存: ${TOTAL_MEM}GB"
echo "  可用内存: ${AVAIL_MEM}GB"

if [ "$TOTAL_MEM" -ge 4 ]; then
    echo -e "  内存大小: ${GREEN}✓ 满足要求（≥4GB）${NC}"
    ((PASSED++))
else
    echo -e "  内存大小: ${YELLOW}⚠ 建议≥4GB${NC}"
    ((WARNINGS++))
fi
echo ""

# 9. 检查项目文件
echo "【项目文件】"
if [ -d "/opt/quant-trading-system" ]; then
    echo -e "  项目目录: ${GREEN}✓ 存在${NC}"
    ((PASSED++))
    
    if [ -f "/opt/quant-trading-system/java-backend/pom.xml" ]; then
        echo -e "  Java后端: ${GREEN}✓ 存在${NC}"
        ((PASSED++))
    else
        echo -e "  Java后端: ${RED}✗ pom.xml不存在${NC}"
        ((FAILED++))
    fi
    
    if [ -f "/opt/quant-trading-system/python-strategies/api_server.py" ]; then
        echo -e "  Python服务: ${GREEN}✓ 存在${NC}"
        ((PASSED++))
    else
        echo -e "  Python服务: ${RED}✗ api_server.py不存在${NC}"
        ((FAILED++))
    fi
else
    echo -e "  项目目录: ${RED}✗ 不存在${NC}"
    echo "  提示: 请将项目上传到 /opt/quant-trading-system"
    ((FAILED++))
fi
echo ""

# 10. 检查系统服务
echo "【系统服务】"
if [ -f "/etc/systemd/system/quant-trading-backend.service" ]; then
    if systemctl is-active --quiet quant-trading-backend; then
        echo -e "  Java后端服务: ${GREEN}✓ 运行中${NC}"
        ((PASSED++))
    else
        echo -e "  Java后端服务: ${YELLOW}⚠ 已配置但未运行${NC}"
        ((WARNINGS++))
    fi
else
    echo -e "  Java后端服务: ${YELLOW}⚠ 未配置${NC}"
    ((WARNINGS++))
fi

if [ -f "/etc/systemd/system/quant-trading-python.service" ]; then
    if systemctl is-active --quiet quant-trading-python; then
        echo -e "  Python服务: ${GREEN}✓ 运行中${NC}"
        ((PASSED++))
    else
        echo -e "  Python服务: ${YELLOW}⚠ 已配置但未运行${NC}"
        ((WARNINGS++))
    fi
else
    echo -e "  Python服务: ${YELLOW}⚠ 未配置${NC}"
    ((WARNINGS++))
fi
echo ""

# 总结
echo "=========================================="
echo "检查总结"
echo "=========================================="
echo -e "${GREEN}通过: $PASSED${NC}"
echo -e "${YELLOW}警告: $WARNINGS${NC}"
echo -e "${RED}失败: $FAILED${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    if [ $WARNINGS -eq 0 ]; then
        echo -e "${GREEN}✓ 环境检查完全通过，可以开始部署！${NC}"
        exit 0
    else
        echo -e "${YELLOW}⚠ 环境基本满足要求，但有一些警告，建议处理后再部署${NC}"
        exit 0
    fi
else
    echo -e "${RED}✗ 环境检查失败，请先解决必需项的问题${NC}"
    exit 1
fi

