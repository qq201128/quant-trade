"""
策略基类
所有量化策略都应继承此类
"""
from abc import ABC, abstractmethod
from typing import Dict, Any
import pandas as pd


class BaseStrategy(ABC):
    """策略基类"""
    
    def __init__(self):
        self.name = self.__class__.__name__
    
    @abstractmethod
    def execute(
        self,
        symbol: str,
        market_data: Dict[str, Any],
        strategy_params: Dict[str, Any],
        position: Dict[str, Any],
        account: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        执行策略逻辑
        
        Args:
            symbol: 交易标的
            market_data: 市场数据（K线、价格等）
            strategy_params: 策略参数
            position: 当前持仓
            account: 账户信息
            
        Returns:
            Dict包含:
                - signal: 交易信号 (BUY/SELL/HOLD)
                - position: 建议仓位 (0.0-1.0)
                - targetPrice: 目标价格
                - stopLoss: 止损价格
                - takeProfit: 止盈价格
                - confidence: 置信度 (0.0-1.0)
                - metadata: 额外信息
        """
        pass
    
    def calculate_indicators(self, data: pd.DataFrame) -> pd.DataFrame:
        """计算技术指标（子类可重写）"""
        return data
    
    def validate_params(self, params: Dict[str, Any]) -> bool:
        """验证策略参数（子类可重写）"""
        return True



