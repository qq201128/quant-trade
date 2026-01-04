#!/bin/bash

echo "===================================="
echo "Python策略服务启动脚本（多进程模式）"
echo "===================================="
echo ""

# 检查Python是否安装
if ! command -v python3 &> /dev/null; then
    echo "[错误] 未检测到Python，请先安装Python 3.8+"
    exit 1
fi

echo "[1/3] 检查Python版本..."
python3 --version

echo ""
echo "[2/3] 检查依赖包..."
if ! python3 -c "import fastapi" &> /dev/null; then
    echo "正在安装依赖包..."
    pip3 install -r requirements.txt
    if [ $? -ne 0 ]; then
        echo "[错误] 依赖包安装失败"
        exit 1
    fi
else
    echo "依赖包已安装"
fi

echo ""
echo "[3/3] 启动Python策略API服务（多进程模式）..."
echo "服务地址: http://localhost:8000"
echo "工作进程数: 4（支持并发处理多个用户请求）"
echo "按 Ctrl+C 停止服务"
echo ""
echo "注意：多进程模式可以并发处理20-40个用户请求"
echo ""

# 使用 uvicorn 多进程模式启动
# --workers 4: 启动4个工作进程，支持并发处理
# --host 0.0.0.0: 监听所有网络接口
# --port 8000: 监听端口8000
uvicorn api_server:app --host 0.0.0.0 --port 8000 --workers 4

