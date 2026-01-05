"""
双向策略
同时持有多空仓位，通过价差获利

策略逻辑（基于自用auto目录的策略）：
1. 补仓策略：盈利4次后，另一方向亏损时补仓（每4次盈利=1次补仓机会）
2. 平仓策略：盈利50%时平仓
"""
from typing import Dict, Any
from loguru import logger
from .base_strategy import BaseStrategy


class DualDirectionStrategy(BaseStrategy):
    """双向策略"""
    
    def execute(
        self,
        symbol: str,
        market_data: Dict[str, Any],
        strategy_params: Dict[str, Any],
        position: Dict[str, Any],
        account: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        执行双向策略

        策略逻辑：
        - 同时持有多头和空头仓位
        - 通过价差变化获利
        - 盈利4次后，另一方向亏损时补仓（每4次盈利=1次补仓机会）
        - 盈利50%时平仓
        """
        # 0. 网络异常检测：检查数据有效性，防止断网导致误开仓
        # 检查1：验证 _fetchSuccess 标志
        fetch_success = position.get("_fetchSuccess", True)
        if not fetch_success:
            error_msg = position.get("_error", "未知错误")
            logger.error(f"持仓数据获取失败，停止策略执行: {error_msg}")
            return self._hold_signal(f"持仓数据获取失败: {error_msg}")

        # 检查2：验证价格数据有效性（价格必须大于0）
        current_price = market_data.get("price")
        if current_price is None or current_price <= 0:
            logger.error(f"价格数据无效，停止策略执行: price={current_price}")
            return self._hold_signal(f"价格数据无效: {current_price}")

        # 检查3：验证持仓数据完整性
        # 如果有持仓（longQuantity > 0 或 shortQuantity > 0），但开仓价格为0，说明数据异常
        long_quantity_raw = position.get("longQuantity", 0.0)
        short_quantity_raw = position.get("shortQuantity", 0.0)
        long_open_rate_raw = position.get("longOpenRate", 0.0)
        short_open_rate_raw = position.get("shortOpenRate", 0.0)

        # 转换为float进行检查
        def safe_float_check(value):
            """安全转换为float用于检查"""
            if isinstance(value, (int, float)):
                return float(value)
            try:
                return float(value) if value else 0.0
            except:
                return 0.0

        long_qty_check = safe_float_check(long_quantity_raw)
        short_qty_check = safe_float_check(short_quantity_raw)
        long_open_check = safe_float_check(long_open_rate_raw)
        short_open_check = safe_float_check(short_open_rate_raw)

        # 如果有持仓但开仓价格为0，说明数据异常（可能是网络问题导致的不完整数据）
        if long_qty_check > 0 and long_open_check <= 0:
            logger.error(f"多头持仓数据异常：有持仓但开仓价格为0，可能是网络问题: longQuantity={long_qty_check}, longOpenRate={long_open_check}")
            return self._hold_signal("多头持仓数据异常，疑似网络问题")

        if short_qty_check > 0 and short_open_check <= 0:
            logger.error(f"空头持仓数据异常：有持仓但开仓价格为0，可能是网络问题: shortQuantity={short_qty_check}, shortOpenRate={short_open_check}")
            return self._hold_signal("空头持仓数据异常，疑似网络问题")

        # 检查4：如果所有数据都是0（包括盈利次数），但_fetchSuccess为true，需要额外验证
        # 这种情况可能是：1) 真的没有持仓（正常） 2) 网络问题导致返回空数据（异常）
        # 通过检查盈利次数来区分：如果之前有过盈利记录，现在突然全是0，很可能是网络问题
        all_zero = (long_qty_check == 0 and short_qty_check == 0 and
                   position.get("longProfitCount", 0) == 0 and
                   position.get("shortProfitCount", 0) == 0 and
                   position.get("longAddCount", 0) == 0 and
                   position.get("shortAddCount", 0) == 0)

        if all_zero:
            # 所有数据都是0，记录警告日志，但允许继续执行（可能是真的没有持仓）
            # 注意：这里不阻止执行，因为可能是用户第一次开仓
            logger.warning(f"所有持仓数据都是0，请确认是否真的没有持仓。如果之前有持仓，可能是网络问题导致数据丢失")

        # 1. 获取当前价格（已在上面检查过）
        if current_price is None:
            return self._hold_signal("缺少价格数据")

        # 2. 获取当前持仓信息
        # 处理不同类型的持仓数量（可能是float、int或BigDecimal）
        # 注意：long_quantity_raw 和 short_quantity_raw 已在上面的检查中定义
        
        # 转换为float类型
        if isinstance(long_quantity_raw, (int, float)):
            long_quantity = float(long_quantity_raw)
        else:
            long_quantity = float(long_quantity_raw) if long_quantity_raw else 0.0
        
        if isinstance(short_quantity_raw, (int, float)):
            short_quantity = float(short_quantity_raw)
        else:
            short_quantity = float(short_quantity_raw) if short_quantity_raw else 0.0
        
        # 确保数量非负
        long_quantity = max(0.0, long_quantity)
        short_quantity = max(0.0, short_quantity)
        
        # 调试日志：打印持仓信息
        logger.debug(f"双向策略持仓信息: symbol={symbol}, long_quantity={long_quantity}, short_quantity={short_quantity}")
        
        # 调试：打印完整的position字典，帮助诊断问题
        logger.info(f"完整position数据: {position}")
        
        # 3. 获取持仓的盈利信息（从position或strategy_params中获取）
        # 支持两种方式传递：
        # 方式1：position中包含盈利信息
        # 方式2：strategy_params中包含对方向持仓信息
        
        # 处理BigDecimal序列化问题：Java的BigDecimal可能被序列化为字符串或数字
        def safe_float(value, default=0.0):
            """安全地将值转换为float，处理BigDecimal序列化问题"""
            if value is None:
                return default
            if isinstance(value, (int, float)):
                return float(value)
            if isinstance(value, str):
                try:
                    return float(value)
                except (ValueError, TypeError):
                    return default
            # 尝试直接转换
            try:
                return float(value)
            except (ValueError, TypeError):
                return default
        
        long_profit_count = position.get("longProfitCount", 0)
        long_profit_pct_raw = position.get("longProfitPct", 0.0)
        long_profit_pct = safe_float(long_profit_pct_raw, 0.0)
        long_add_count = position.get("longAddCount", 0)
        long_open_rate_raw = position.get("longOpenRate")
        long_open_rate = safe_float(long_open_rate_raw) if long_open_rate_raw is not None else None
        long_current_rate = current_price
        
        short_profit_count = position.get("shortProfitCount", 0)
        short_profit_pct_raw = position.get("shortProfitPct", 0.0)
        short_profit_pct = safe_float(short_profit_pct_raw, 0.0)
        short_add_count = position.get("shortAddCount", 0)
        short_open_rate_raw = position.get("shortOpenRate")
        short_open_rate = safe_float(short_open_rate_raw) if short_open_rate_raw is not None else None
        short_current_rate = current_price
        
        # 注意：现在Java传递的盈利百分比已经是正确的百分比，不需要再乘以杠杆
        # 直接使用传递的盈利百分比进行判断
        
        # 调试日志：打印盈利百分比
        logger.info(f"盈利百分比: longProfitPct={long_profit_pct:.2f}%, shortProfitPct={short_profit_pct:.2f}%")
        
        # 如果position中没有盈利信息，尝试从strategy_params获取
        # 注意：只有当position中确实没有提供这些字段时，才从strategy_params获取
        # 如果position中提供了但值为0，说明是真实的0值，不应该覆盖
        if "longProfitPct" not in position and "longProfitCount" not in position:
            opposite_positions = strategy_params.get("oppositePositions", [])
            if opposite_positions:
                # 从对方向持仓中获取信息
                for opp_pos in opposite_positions:
                    if not opp_pos.get("isShort", False):  # 多头
                        long_profit_count = opp_pos.get("profitCount", 0)
                        long_profit_pct = safe_float(opp_pos.get("profitPct", 0.0), 0.0)
                        long_add_count = opp_pos.get("addCount", 0)
                        long_open_rate = safe_float(opp_pos.get("openRate")) if opp_pos.get("openRate") is not None else None
                        logger.info(f"从strategy_params获取多头盈利信息: {long_profit_pct:.2f}%")
                        break
        
        if "shortProfitPct" not in position and "shortProfitCount" not in position:
            opposite_positions = strategy_params.get("oppositePositions", [])
            if opposite_positions:
                for opp_pos in opposite_positions:
                    if opp_pos.get("isShort", False):  # 空头
                        short_profit_count = opp_pos.get("profitCount", 0)
                        short_profit_pct = safe_float(opp_pos.get("profitPct", 0.0), 0.0)
                        short_add_count = opp_pos.get("addCount", 0)
                        short_open_rate = safe_float(opp_pos.get("openRate")) if opp_pos.get("openRate") is not None else None
                        logger.info(f"从strategy_params获取空头盈利信息: {short_profit_pct:.2f}%")
                        break
        
        # 如果没有开仓价格，尝试计算盈利百分比（备用方案）
        # 注意：优先使用从Java传递过来的盈利百分比（基于保证金的盈亏百分比），更准确
        # 只有在没有提供盈利百分比时，才使用价格差计算（这个计算方式不准确，因为杠杆的影响）
        if long_quantity > 0 and long_open_rate and long_open_rate > 0:
            # 如果position中没有提供盈利百分比，才使用价格差计算（不准确，仅作备用）
            if long_profit_pct == 0.0:
                calculated_long_profit_pct = ((long_current_rate - long_open_rate) / long_open_rate) * 100
                long_profit_pct = calculated_long_profit_pct
                logger.warning(f"多头持仓未提供盈利百分比，使用价格差计算（不准确）: {long_profit_pct:.2f}%")
        if short_quantity > 0 and short_open_rate and short_open_rate > 0:
            # 如果position中没有提供盈利百分比，才使用价格差计算（不准确，仅作备用）
            if short_profit_pct == 0.0:
                calculated_short_profit_pct = ((short_open_rate - short_current_rate) / short_open_rate) * 100
                short_profit_pct = calculated_short_profit_pct
                logger.warning(f"空头持仓未提供盈利百分比，使用价格差计算（不准确）: {short_profit_pct:.2f}%")
        
        # 调试日志：打印盈利信息
        logger.info(f"双向策略盈利信息: long_quantity={long_quantity}, short_quantity={short_quantity}, "
                   f"long_profit_pct={long_profit_pct:.2f}%, short_profit_pct={short_profit_pct:.2f}%, "
                   f"long_profit_count={long_profit_count}, short_profit_count={short_profit_count}, "
                   f"long_open_rate={long_open_rate}, short_open_rate={short_open_rate}, current_price={current_price}")
        
        # 4. 策略决策
        signal = "HOLD"
        position_ratio = 0.0
        confidence = 0.6
        reason = ""
        margin = None  # 开仓/补仓金额（USDT）
        
        # 4.1 优先检查平仓条件：盈利50%时平仓（最高优先级）
        # 注意：平仓条件必须优先于补齐仓位，避免在应该平仓时却去开仓
        # 盈利百分比必须严格大于等于50.0（Java传递的已经是正确的百分比）
        
        # 调试日志：检查平仓条件
        logger.info(f"平仓条件检查: long_quantity={long_quantity}, short_quantity={short_quantity}, "
                   f"long_profit_pct={long_profit_pct:.2f}%, short_profit_pct={short_profit_pct:.2f}%, "
                   f"long_profit_pct>=50.0={long_profit_pct >= 50.0}, short_profit_pct>=50.0={short_profit_pct >= 50.0}")
        
        # 注意：双向策略只有在同时有多空持仓时，才允许平仓（避免单边持仓时误平仓）
        if long_quantity > 0 and short_quantity > 0 and long_profit_pct >= 50.0:
            signal = "SELL"
            position_ratio = 1.0  # 全部平仓
            confidence = 0.9
            reason = f"多头盈利{long_profit_pct:.2f}%达到平仓条件（双向策略，需要>=50%）"
            logger.info(f"✅ 触发平仓条件: {reason}")
            # 平仓不需要margin
        elif long_quantity > 0 and short_quantity > 0 and short_profit_pct >= 50.0:
            signal = "BUY"  # 平空头 = 买入
            position_ratio = 1.0  # 全部平仓
            confidence = 0.9
            reason = f"空头盈利{short_profit_pct:.2f}%达到平仓条件（双向策略，需要>=50%）"
            logger.info(f"✅ 触发平仓条件: {reason}")
            # 平仓不需要margin
        elif long_quantity > 0 and short_quantity > 0:
            # 如果有多空持仓但未达到平仓条件，记录原因
            logger.info(f"未触发平仓: 多头盈利{long_profit_pct:.2f}% < 50%, 空头盈利{short_profit_pct:.2f}% < 50%")
        
        # 4.2 检查是否需要补齐仓位：如果只有单边持仓，必须先补齐另一边（双向策略的核心）
        # 注意：这个检查必须在平仓之后，确保在应该平仓时不会去开仓
        # 但是，如果最近刚平仓（60秒内），需要等待冷却期，避免频繁开仓
        
        # 只有在没有触发平仓的情况下，才检查补齐仓位
        if signal == "HOLD":
            # 检查最近平仓记录（冷却期检查）
            recent_close_positions = strategy_params.get("recentClosePositions", [])
            cooldown_seconds = 60  # 冷却期：60秒
            # 获取当前时间戳（毫秒）
            import time
            current_time_ms = int(market_data.get("timestamp", 0))
            if current_time_ms == 0:
                current_time_ms = int(time.time() * 1000)
            
            # 检查是否在冷却期内
            in_cooldown = False
            cooldown_reason = ""
            if recent_close_positions:
                for close_info in recent_close_positions:
                    close_time_ms = close_info.get("closeTime", 0)
                    close_side = close_info.get("side", "")
                    if close_time_ms > 0:
                        time_diff_seconds = (current_time_ms - close_time_ms) / 1000.0
                        if time_diff_seconds < cooldown_seconds:
                            in_cooldown = True
                            cooldown_reason = f"最近{time_diff_seconds:.1f}秒内平仓了{close_side}方向（冷却期{cooldown_seconds}秒）"
                            logger.info(f"⚠️ 冷却期检查: {cooldown_reason}")
                            break
            
            # 只有在非冷却期内才补齐仓位
            if not in_cooldown:
                if long_quantity > 0 and short_quantity == 0:
                    # 只有多头，需要开空头（补齐双向仓位）
                    signal = "SELL"  # 开空头 = 卖出
                    position_ratio = 0.5
                    confidence = 0.6
                    reason = "只有多头持仓，需要开空头（补齐双向仓位）"
                    # 开仓金额：1U（与多头保持一致）
                    margin = 1.0
                elif long_quantity == 0 and short_quantity > 0:
                    # 只有空头，需要开多头（补齐双向仓位）
                    signal = "BUY"  # 开多头 = 买入
                    position_ratio = 0.5
                    confidence = 0.6
                    reason = "只有空头持仓，需要开多头（补齐双向仓位）"
                    # 开仓金额：1U（与空头保持一致）
                    margin = 1.0
            else:
                # 在冷却期内，不补齐仓位
                logger.info(f"冷却期内，跳过补齐仓位: {cooldown_reason}")
                # signal保持为HOLD，不执行任何操作
        
        # 4.3 检查补仓条件：盈利4次后，另一方向亏损时补仓
        elif long_quantity > 0 and short_quantity > 0 and long_profit_count >= 4:
            # 多头盈利，检查空头是否亏损
            max_add_allowed = long_profit_count // 4  # 每4次盈利=1次补仓机会
            
            if short_quantity > 0 and short_profit_pct < 0 and short_add_count < max_add_allowed:
                # 空头亏损，可以补仓
                signal = "SELL"  # 增加空头 = 卖出
                position_ratio = 0.5  # 补仓金额：固定使用0.5U（盈利开仓1U的一半）
                confidence = 0.7
                reason = f"多头盈利{long_profit_count}次，空头亏损{short_profit_pct:.2f}%，需要补空头（已补{short_add_count}/{max_add_allowed}）"
                # 补仓金额：0.5U
                margin = 0.5
            elif short_quantity == 0:
                # 空头没有持仓，需要开空头
                signal = "SELL"  # 开空头 = 卖出
                position_ratio = 0.5
                confidence = 0.7
                reason = f"多头盈利{long_profit_count}次，空头无持仓，需要开空头"
                # 开仓金额：0.5U
                margin = 0.5
        
        elif long_quantity > 0 and short_quantity > 0 and short_profit_count >= 4:
            # 空头盈利，检查多头是否亏损
            max_add_allowed = short_profit_count // 4  # 每4次盈利=1次补仓机会
            
            if long_quantity > 0 and long_profit_pct < 0 and long_add_count < max_add_allowed:
                # 多头亏损，可以补仓
                signal = "BUY"  # 增加多头 = 买入
                position_ratio = 0.5  # 补仓金额：固定使用0.5U（盈利开仓1U的一半）
                confidence = 0.7
                reason = f"空头盈利{short_profit_count}次，多头亏损{long_profit_pct:.2f}%，需要补多头（已补{long_add_count}/{max_add_allowed}）"
                # 补仓金额：0.5U
                margin = 0.5
        
        # 4.4 初始开仓：如果没有任何持仓，需要同时开多空两个仓位
        elif long_quantity == 0 and short_quantity == 0:
            signal = "DUAL_OPEN"  # 特殊信号：双向开仓
            position_ratio = 0.5
            confidence = 0.6
            reason = "初始开仓：同时开多空（各1U）"
            # 初始开仓金额：1U（每个方向）
            margin = 1.0
        
        # 最终决策日志
        logger.info(f"策略最终决策: signal={signal}, position_ratio={position_ratio}, reason={reason}, "
                   f"long_profit_pct={long_profit_pct:.2f}%, short_profit_pct={short_profit_pct:.2f}%")
        
        # 5. 返回结果
        result = {
            "signal": signal,
            "position": position_ratio,
            "confidence": confidence,
            "metadata": {
                "longQuantity": float(long_quantity),
                "shortQuantity": float(short_quantity),
                "longProfitCount": int(long_profit_count),
                "longProfitPct": float(long_profit_pct),  # 返回盈利百分比
                "longAddCount": int(long_add_count),
                "shortProfitCount": int(short_profit_count),
                "shortProfitPct": float(short_profit_pct),  # 返回盈利百分比
                "shortAddCount": int(short_add_count),
                "reason": reason,
                "strategy": "DualDirectionStrategy"
            }
        }
        
        # 如果设置了margin（开仓或补仓金额），添加到metadata中
        if margin is not None:
            result["metadata"]["margin"] = float(margin)
            logger.info(f"策略返回margin: {margin} USDT, signal={signal}, position_ratio={position_ratio}, reason={reason}")
        else:
            # 如果不是平仓操作（position_ratio < 1.0），但没有margin，记录警告并设置默认值
            if signal != "HOLD" and position_ratio < 1.0:
                logger.warning(f"策略返回开仓/补仓信号但未设置margin: signal={signal}, position_ratio={position_ratio}, reason={reason}")
                # 对于双向策略，如果返回BUY/SELL但position_ratio=0.5，默认使用0.5U
                if signal in ["BUY", "SELL"] and position_ratio == 0.5:
                    margin = 0.5
                    result["metadata"]["margin"] = float(margin)
                    logger.info(f"自动设置默认margin: {margin} USDT, signal={signal}, position_ratio={position_ratio}")
                elif signal == "DUAL_OPEN":
                    # DUAL_OPEN信号应该总是有margin，如果没有则设置默认值1.0
                    margin = 1.0
                    result["metadata"]["margin"] = float(margin)
                    logger.info(f"DUAL_OPEN信号自动设置margin: {margin} USDT")
        
        return result
    
    def _hold_signal(self, reason: str) -> Dict[str, Any]:
        """返回持有信号"""
        return {
            "signal": "HOLD",
            "position": 0.0,
            "confidence": 0.0,
            "metadata": {"reason": reason}
        }

