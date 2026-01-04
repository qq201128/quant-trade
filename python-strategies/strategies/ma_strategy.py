"""
均线策略示例
简单的移动平均线交叉策略
"""
import pandas as pd
import numpy as np
from typing import Dict, Any
from .base_strategy import BaseStrategy


class MAStrategy(BaseStrategy):
    """移动平均线策略"""
    
    def execute(
        self,
        symbol: str,
        market_data: Dict[str, Any],
        strategy_params: Dict[str, Any],
        position: Dict[str, Any],
        account: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        执行均线策略
        
        策略逻辑：
        - 短期均线上穿长期均线 -> 买入信号
        - 短期均线下穿长期均线 -> 卖出信号
        """
        # 1. 获取策略参数
        short_period = strategy_params.get("short_period", 5)
        long_period = strategy_params.get("long_period", 20)
        
        # 2. 处理市场数据
        # 假设market_data包含价格序列
        prices = market_data.get("prices", [])
        if len(prices) < long_period:
            return self._hold_signal("数据不足")
        
        # 转换为DataFrame
        df = pd.DataFrame({"close": prices})
        
        # 3. 计算移动平均线
        df["ma_short"] = df["close"].rolling(window=short_period).mean()
        df["ma_long"] = df["close"].rolling(window=long_period).mean()
        
        # 4. 生成交易信号
        current_price = prices[-1]
        ma_short_current = df["ma_short"].iloc[-1]
        ma_long_current = df["ma_long"].iloc[-1]
        ma_short_prev = df["ma_short"].iloc[-2] if len(df) > 1 else ma_short_current
        ma_long_prev = df["ma_long"].iloc[-2] if len(df) > 1 else ma_long_current
        
        # 5. 判断交叉
        signal = "HOLD"
        confidence = 0.5
        
        # 金叉：短期均线上穿长期均线
        if ma_short_prev <= ma_long_prev and ma_short_current > ma_long_current:
            signal = "BUY"
            confidence = 0.7
        # 死叉：短期均线下穿长期均线
        elif ma_short_prev >= ma_long_prev and ma_short_current < ma_long_current:
            signal = "SELL"
            confidence = 0.7
        
        # 6. 计算目标价格和止损止盈
        target_price = None
        stop_loss = None
        take_profit = None
        
        if signal == "BUY":
            # 买入：目标价为长期均线 + 2%
            target_price = ma_long_current * 1.02
            stop_loss = current_price * 0.98  # 止损2%
            take_profit = current_price * 1.05  # 止盈5%
        elif signal == "SELL":
            # 卖出：目标价为长期均线 - 2%
            target_price = ma_long_current * 0.98
            stop_loss = current_price * 1.02  # 止损2%
            take_profit = current_price * 0.95  # 止盈5%
        
        # 7. 计算建议仓位
        position_ratio = 0.0
        if signal == "BUY":
            position_ratio = min(confidence, 0.8)  # 最大80%仓位
        elif signal == "SELL":
            position_ratio = 0.0  # 卖出信号，清仓
        
        return {
            "signal": signal,
            "position": position_ratio,
            "targetPrice": float(target_price) if target_price else None,
            "stopLoss": float(stop_loss) if stop_loss else None,
            "takeProfit": float(take_profit) if take_profit else None,
            "confidence": confidence,
            "metadata": {
                "ma_short": float(ma_short_current),
                "ma_long": float(ma_long_current),
                "current_price": float(current_price),
                "strategy": "MAStrategy"
            }
        }
    
    def _hold_signal(self, reason: str) -> Dict[str, Any]:
        """返回持有信号"""
        return {
            "signal": "HOLD",
            "position": 0.0,
            "confidence": 0.0,
            "metadata": {"reason": reason}
        }



