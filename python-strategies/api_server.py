"""
Python策略API服务
提供RESTful API接口供Java后台调用
"""
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Dict, Any, Optional
from loguru import logger
import sys

# 导入策略模块
from strategies.base_strategy import BaseStrategy
from strategies.ma_strategy import MAStrategy
from strategies.ml_strategy import MLStrategy

app = FastAPI(title="Quantitative Strategy API", version="1.0.0")

# 导入更多策略
from strategies.grid_strategy import GridStrategy
from strategies.dual_direction_strategy import DualDirectionStrategy

# 策略注册表
STRATEGY_REGISTRY = {
    # "ma_strategy": MAStrategy,
    # "ml_strategy": MLStrategy,
    # "grid_strategy": GridStrategy,
    "dual_direction_strategy": DualDirectionStrategy,
}


class StrategyRequest(BaseModel):
    """策略请求模型（与Java端对应）"""
    strategyName: str
    symbol: str
    marketData: Dict[str, Any]
    strategyParams: Dict[str, Any]
    position: Dict[str, Any]
    account: Dict[str, Any]


class StrategyResponse(BaseModel):
    """策略响应模型（与Java端对应）"""
    signal: str  # BUY, SELL, HOLD
    position: float  # 0.0 - 1.0
    targetPrice: Optional[float] = None
    stopLoss: Optional[float] = None
    takeProfit: Optional[float] = None
    confidence: float  # 0.0 - 1.0
    metadata: Dict[str, Any] = {}
    error: Optional[str] = None


@app.get("/health")
async def health_check():
    """健康检查接口"""
    return {"status": "ok"}


@app.post("/api/strategy/execute", response_model=StrategyResponse)
async def execute_strategy(request: StrategyRequest):
    """
    执行策略并返回交易信号
    
    Args:
        request: 策略请求，包含市场数据和策略参数
        
    Returns:
        StrategyResponse: 策略响应，包含交易信号和建议
    """
    try:
        logger.info(f"收到策略请求: {request.strategyName} - {request.symbol}")
        
        # 1. 获取策略实例
        strategy_class = STRATEGY_REGISTRY.get(request.strategyName)
        if not strategy_class:
            raise HTTPException(
                status_code=404, 
                detail=f"策略不存在: {request.strategyName}"
            )
        
        strategy = strategy_class()
        
        # 2. 执行策略
        result = strategy.execute(
            symbol=request.symbol,
            market_data=request.marketData,
            strategy_params=request.strategyParams,
            position=request.position,
            account=request.account
        )
        
        # 3. 构建响应
        response = StrategyResponse(
            signal=result.get("signal", "HOLD"),
            position=result.get("position", 0.0),
            targetPrice=result.get("targetPrice"),
            stopLoss=result.get("stopLoss"),
            takeProfit=result.get("takeProfit"),
            confidence=result.get("confidence", 0.0),
            metadata=result.get("metadata", {}),
        )
        
        logger.info(f"策略执行完成: {response.signal} - 置信度: {response.confidence}")
        return response
        
    except Exception as e:
        logger.error(f"策略执行失败: {str(e)}", exc_info=True)
        return StrategyResponse(
            signal="HOLD",
            position=0.0,
            confidence=0.0,
            error=str(e)
        )


@app.get("/api/strategies")
async def list_strategies():
    """列出所有可用策略"""
    return {
        "strategies": list(STRATEGY_REGISTRY.keys()),
        "count": len(STRATEGY_REGISTRY)
    }


if __name__ == "__main__":
    import uvicorn
    logger.info("启动Python策略API服务...")
    uvicorn.run(app, host="0.0.0.0", port=8000)

