@echo off
chcp 65001 >nul
echo ====================================
echo Python策略服务启动脚本（多进程模式）
echo ====================================
echo.

REM 检查Python是否安装
python --version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未检测到Python，请先安装Python 3.8+
    pause
    exit /b 1
)

echo [1/3] 检查Python版本...
python --version

echo.
echo [2/3] 检查依赖包...
pip show fastapi >nul 2>&1
if errorlevel 1 (
    echo 正在安装依赖包...
    pip install -r requirements.txt
    if errorlevel 1 (
        echo [错误] 依赖包安装失败
        pause
        exit /b 1
    )
) else (
    echo 依赖包已安装
)

echo.
echo [3/3] 启动Python策略API服务（多进程模式）...
echo 服务地址: http://localhost:8000
echo 工作进程数: 4（支持并发处理多个用户请求）
echo 按 Ctrl+C 停止服务
echo.
echo 注意：多进程模式可以并发处理20-40个用户请求
echo.

REM 使用 uvicorn 多进程模式启动
REM --workers 4: 启动4个工作进程，支持并发处理
REM --host 0.0.0.0: 监听所有网络接口
REM --port 8000: 监听端口8000
uvicorn api_server:app --host 0.0.0.0 --port 8000 --workers 4

pause

