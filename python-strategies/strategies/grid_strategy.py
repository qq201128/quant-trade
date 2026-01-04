"""
网格策略
在价格区间内设置买卖网格，自动低买高卖
"""
import pandas as pd
from typing import Dict, Any
from .base_strategy import BaseStrategy


class GridStrategy(BaseStrategy):
    """网格策略"""
    
    def execute(
        self,
        symbol: str,
        market_data: Dict[str, Any],
        strategy_params: Dict[str, Any],
        position: Dict[str, Any],
        account: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        执行网格策略
        
        策略逻辑：
        - 在价格区间[gridLower, gridUpper]内设置N个网格
        - 价格触及网格下沿时买入
        - 价格触及网格上沿时卖出
        """
        # 1. 获取策略参数
        grid_count = strategy_params.get("gridCount", 10)
        grid_lower = strategy_params.get("gridLower")
        grid_upper = strategy_params.get("gridUpper")
        
        if grid_lower is None or grid_upper is None:
            return self._hold_signal("缺少网格参数")
        
        # 2. 获取当前价格
        current_price = market_data.get("price")
        if current_price is None:
            return self._hold_signal("缺少价格数据")
        
        # 3. 计算网格步长
        grid_step = (grid_upper - grid_lower) / grid_count
        
        # 4. 判断当前价格在哪个网格
        current_grid = int((current_price - grid_lower) / grid_step)
        
        # 5. 网格策略逻辑
        signal = "HOLD"
        confidence = 0.7
        
        # 价格接近网格下沿，买入
        grid_lower_price = grid_lower + grid_step * current_grid
        grid_upper_price = grid_lower + grid_step * (current_grid + 1)
        
        if current_price <= grid_lower_price * 1.01:  # 允许1%误差
            signal = "BUY"
            position_ratio = 0.3  # 买入30%仓位
        elif current_price >= grid_upper_price * 0.99:
            signal = "SELL"
            position_ratio = 0.0  # 卖出
        else:
            position_ratio = 0.0
        
        return {
            "signal": signal,
            "position": position_ratio,
            "targetPrice": float(grid_upper_price) if signal == "BUY" else float(grid_lower_price),
            "confidence": confidence,
            "metadata": {
                "currentGrid": current_grid,
                "gridLower": float(grid_lower_price),
                "gridUpper": float(grid_upper_price),
                "strategy": "GridStrategy"
            }
        }
    
    def _hold_signal(self, reason: str) -> Dict[str, Any]:
        return {
            "signal": "HOLD",
            "position": 0.0,
            "confidence": 0.0,
            "metadata": {"reason": reason}
        }



