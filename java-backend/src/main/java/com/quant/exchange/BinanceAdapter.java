package com.quant.exchange;

import com.quant.config.ProxyConfig;
import com.quant.model.AccountInfo;
import com.quant.model.ExchangeType;
import com.quant.model.Order;
import com.quant.model.Position;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Binance交易所适配器实现
 * 对接Binance API和WebSocket
 *
 * 注意：使用原型作用域，每个用户创建独立实例，避免API密钥互相覆盖
 *
 * 参考文档: https://developers.binance.com/docs/binance-spot-api-docs
 */
@Slf4j
@Component
@Scope("prototype")  // 原型作用域：每次注入创建新实例，支持多用户隔离
public class BinanceAdapter implements ExchangeAdapter {
    
    private String apiKey;
    private String secretKey;
    private BinanceApiClient apiClient;
    private BinanceFuturesApiClient futuresApiClient;
    private BinanceWebSocketClient wsClient;
    private BinanceFuturesWebSocketClient futuresWsClient;
    private final WebClient webClient;
    private final ProxyConfig proxyConfig;
    
    // 缓存交易对的精度信息 (symbol -> stepSize)
    private final Map<String, Integer> symbolPrecisionCache = new HashMap<>();
    
    public BinanceAdapter(WebClient webClient, ProxyConfig proxyConfig) {
        this.webClient = webClient;
        this.proxyConfig = proxyConfig;
    }
    
    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BINANCE;
    }
    
    @Override
    public void initialize(String apiKey, String secretKey, String passphrase) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        // Binance不需要passphrase
        this.apiClient = new BinanceApiClient(apiKey, secretKey, webClient);
        this.futuresApiClient = new BinanceFuturesApiClient(apiKey, secretKey, webClient);
        
        // 初始化期货WebSocket客户端（用于订阅标记价格流和用户数据流）
        Proxy proxy = createProxy();
        this.futuresWsClient = new BinanceFuturesWebSocketClient(proxy, futuresApiClient);
        // 订阅所有标记价格流
        this.futuresWsClient.subscribeAllMarkPrices()
                .subscribe(
                    prices -> log.debug("收到标记价格更新: {} 个交易对", prices.size()),
                    error -> log.error("订阅标记价格流失败: {}", error.getMessage())
                );
        // 等待第一条价格数据到达（最多等待5秒）
        if (this.futuresWsClient.waitForPriceData(5)) {
            log.info("WebSocket价格数据已就绪");
        } else {
            log.warn("等待WebSocket价格数据超时，可能导致首次交易获取价格失败");
        }
        
//        log.info("Binance适配器初始化完成（支持现货和期货API，已订阅标记价格流）");
    }
    
    @Override
    public Mono<AccountInfo> getAccountInfo(String userId) {
        // 使用币安U本位合约API获取账户信息
        // 参考: https://developers.binance.com/docs/derivatives/usds-margined-futures/account/rest-api/Account-Information-V2
//        log.info("获取Binance期货账户信息: userId={}", userId);
        
        if (futuresApiClient == null) {
            log.error("Binance Futures API客户端未初始化");
            return Mono.error(new RuntimeException("Futures API客户端未初始化"));
        }
        
        // 使用期货API获取账户信息
        return futuresApiClient.getAccountInfo()
                .map(accountData -> {
                    log.debug("Binance期货账户数据: {}", accountData);
                    
                    // 解析币安U本位合约账户数据
                    // 参考API文档: /fapi/v2/account 返回字段
                    String totalWalletBalanceStr = (String) accountData.getOrDefault("totalWalletBalance", "0");
                    String totalUnrealizedProfitStr = (String) accountData.getOrDefault("totalUnrealizedProfit", "0");
                    String totalMarginBalanceStr = (String) accountData.getOrDefault("totalMarginBalance", "0");
                    String availableBalanceStr = (String) accountData.getOrDefault("availableBalance", "0");
                    
                    BigDecimal totalWalletBalance = new BigDecimal(totalWalletBalanceStr);
                    BigDecimal totalUnrealizedProfit = new BigDecimal(totalUnrealizedProfitStr);
                    BigDecimal totalMarginBalance = new BigDecimal(totalMarginBalanceStr);
                    BigDecimal availableBalance = new BigDecimal(availableBalanceStr);
                    
                    // 计算冻结余额 = 总钱包余额 - 可用余额
                    BigDecimal frozenBalance = totalWalletBalance.subtract(availableBalance);
                    if (frozenBalance.compareTo(BigDecimal.ZERO) < 0) {
                        frozenBalance = BigDecimal.ZERO;
                    }
                    
                    // 账户权益 = 总保证金余额 = 总钱包余额 + 未实现盈亏
                    BigDecimal equity = totalMarginBalance;
                    
                    // 从assets中提取USDT余额信息（如果有）
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> assets = (List<Map<String, Object>>) accountData.get("assets");
                    BigDecimal usdtAvailableBalance = availableBalance; // 默认使用总可用余额
                    if (assets != null) {
                        for (Map<String, Object> asset : assets) {
                            String assetName = (String) asset.get("asset");
                            if ("USDT".equals(assetName)) {
                                String assetAvailableStr = (String) asset.getOrDefault("availableBalance", "0");
                                usdtAvailableBalance = new BigDecimal(assetAvailableStr);
                                log.debug("USDT可用余额: {}", usdtAvailableBalance);
                                break;
                            }
                        }
                    }
                    
                    AccountInfo accountInfo = AccountInfo.builder()
                            .userId(userId)
                            .totalBalance(totalWalletBalance)  // 总钱包余额
                            .availableBalance(usdtAvailableBalance)  // USDT可用余额
                            .frozenBalance(frozenBalance)  // 冻结余额
                            .equity(equity)  // 账户权益 = 总保证金余额
                            .unrealizedPnl(totalUnrealizedProfit)  // 总未实现盈亏
                            .timestamp(System.currentTimeMillis())
                            .build();
                    
                    log.debug("成功获取Binance期货账户信息: userId={}, 总钱包余额={}, 可用余额={}, 未实现盈亏={}, 账户权益={}",
                            userId, totalWalletBalance, usdtAvailableBalance, totalUnrealizedProfit, equity);
                    return accountInfo;
                })
                .doOnError(error -> {
                    log.error("获取Binance期货账户信息失败: userId={}, error={}", userId, error.getMessage(), error);
                });
    }
    
    @Override
    public Mono<List<Position>> getPositions(String userId) {
//        log.info("获取Binance持仓: userId={}", userId);
        
        if (futuresApiClient == null) {
            log.error("Binance Futures API客户端未初始化");
            return Mono.just(new ArrayList<>());
        }
        
        return futuresApiClient.getPositions()
                .map(positionList -> {
                    // Binance Futures API返回的是数组
                    List<Position> positions = new ArrayList<>();
                    
                    for (Map<String, Object> posData : positionList) {
                        // 只处理有持仓的（positionAmt != 0）
                        String positionAmtStr = (String) posData.getOrDefault("positionAmt", "0");
                        BigDecimal positionAmt = new BigDecimal(positionAmtStr);
                        
                        if (positionAmt.compareTo(BigDecimal.ZERO) != 0) {
                            String symbol = (String) posData.get("symbol");
                            String positionSide = (String) posData.getOrDefault("positionSide", "BOTH");
                            String entryPriceStr = (String) posData.getOrDefault("entryPrice", "0");
                            String markPriceStr = (String) posData.getOrDefault("markPrice", "0");
                            String unrealizedPnlStr = (String) posData.getOrDefault("unRealizedProfit", "0");
                            String leverageStr = (String) posData.getOrDefault("leverage", "1");
                            String isolatedMarginStr = (String) posData.getOrDefault("isolatedMargin", "0");
                            
                            BigDecimal entryPrice = new BigDecimal(entryPriceStr);
                            BigDecimal markPrice = new BigDecimal(markPriceStr);
                            BigDecimal unrealizedPnl = new BigDecimal(unrealizedPnlStr);
                            BigDecimal leverage = new BigDecimal(leverageStr);
                            BigDecimal isolatedMargin = new BigDecimal(isolatedMarginStr);
                            
                            // 计算保证金：如果 isolatedMargin 为 0，则根据持仓价值和杠杆计算
                            BigDecimal calculatedMargin = isolatedMargin;
                            if (isolatedMargin.compareTo(BigDecimal.ZERO) <= 0 && 
                                entryPrice.compareTo(BigDecimal.ZERO) > 0 && 
                                positionAmt.abs().compareTo(BigDecimal.ZERO) > 0 &&
                                leverage.compareTo(BigDecimal.ZERO) > 0) {
                                // 保证金 = 持仓价值 / 杠杆 = (开仓均价 × 持仓数量) / 杠杆
                                BigDecimal positionValue = entryPrice.multiply(positionAmt.abs());
                                calculatedMargin = positionValue.divide(leverage, 8, BigDecimal.ROUND_HALF_UP);
                                log.debug("计算保证金: symbol={}, 持仓价值={}, 杠杆={}, 保证金={}", 
                                        symbol, positionValue, leverage, calculatedMargin);
                            }
                            
                            // 计算盈亏百分比（相对于保证金）
                            // 盈亏百分比 = 未实现盈亏 / 保证金 × 100
                            BigDecimal pnlPercentage = BigDecimal.ZERO;
                            if (calculatedMargin.compareTo(BigDecimal.ZERO) > 0) {
                                // 使用保证金计算盈亏百分比（更准确）
                                pnlPercentage = unrealizedPnl.divide(calculatedMargin, 8, BigDecimal.ROUND_HALF_UP)
                                        .multiply(new BigDecimal("100"));
                            } else if (entryPrice.compareTo(BigDecimal.ZERO) > 0 && positionAmt.abs().compareTo(BigDecimal.ZERO) > 0) {
                                // 如果仍然没有保证金，使用持仓价值计算（备用方案）
                                BigDecimal positionValue = entryPrice.multiply(positionAmt.abs());
                                if (positionValue.compareTo(BigDecimal.ZERO) > 0) {
                                    pnlPercentage = unrealizedPnl.divide(positionValue, 8, BigDecimal.ROUND_HALF_UP)
                                            .multiply(new BigDecimal("100"));
                                }
                            }
                            
                            // 确定持仓方向
                            String side = "LONG";
                            if (positionAmt.compareTo(BigDecimal.ZERO) < 0) {
                                side = "SHORT";
                            } else if ("SHORT".equals(positionSide)) {
                                side = "SHORT";
                            } else if ("LONG".equals(positionSide)) {
                                side = "LONG";
                            }
                            
                            // 尝试从WebSocket获取实时标记价格（如果可用）
                            BigDecimal realTimeMarkPrice = markPrice;
                            if (futuresWsClient != null) {
                                BigDecimal wsMarkPrice = futuresWsClient.getMarkPrice(symbol);
                                if (wsMarkPrice != null && wsMarkPrice.compareTo(BigDecimal.ZERO) > 0) {
                                    realTimeMarkPrice = wsMarkPrice;
                                    // 使用实时标记价格重新计算盈亏
                                    if (side.equals("LONG")) {
                                        // 多仓：盈亏 = (当前价格 - 开仓价格) × 数量
                                        unrealizedPnl = realTimeMarkPrice.subtract(entryPrice)
                                                .multiply(positionAmt.abs());
                                    } else {
                                        // 空仓：盈亏 = (开仓价格 - 当前价格) × 数量
                                        unrealizedPnl = entryPrice.subtract(realTimeMarkPrice)
                                                .multiply(positionAmt.abs());
                                    }
                                    // 重新计算盈亏百分比（相对于保证金）
                                    // 盈亏百分比 = 未实现盈亏 / 保证金 × 100
                                    if (calculatedMargin.compareTo(BigDecimal.ZERO) > 0) {
                                        // 使用保证金计算盈亏百分比（更准确）
                                        pnlPercentage = unrealizedPnl.divide(calculatedMargin, 8, BigDecimal.ROUND_HALF_UP)
                                                .multiply(new BigDecimal("100"));
                                    } else if (entryPrice.compareTo(BigDecimal.ZERO) > 0 && positionAmt.abs().compareTo(BigDecimal.ZERO) > 0) {
                                        // 如果仍然没有保证金，使用持仓价值计算（备用方案）
                                        BigDecimal positionValue = entryPrice.multiply(positionAmt.abs());
                                        if (positionValue.compareTo(BigDecimal.ZERO) > 0) {
                                            pnlPercentage = unrealizedPnl.divide(positionValue, 8, BigDecimal.ROUND_HALF_UP)
                                                    .multiply(new BigDecimal("100"));
                                        }
                                    }
                                    log.debug("使用实时标记价格更新盈亏: symbol={}, 实时价格={}, 盈亏={}", 
                                            symbol, realTimeMarkPrice, unrealizedPnl);
                                }
                            }
                            
                            Position position = Position.builder()
                                    .symbol(symbol)
                                    .side(side)
                                    .quantity(positionAmt.abs())
                                    .available(positionAmt.abs()) // 简化处理，实际应该从API获取
                                    .avgPrice(entryPrice)
                                    .currentPrice(realTimeMarkPrice) // 使用实时标记价格
                                    .unrealizedPnl(unrealizedPnl)
                                    .pnlPercentage(pnlPercentage)
                                    .leverage(leverage.intValue())
                                    .margin(calculatedMargin) // 使用计算出的保证金
                                    .build();
                            
                            positions.add(position);
                            log.debug("解析Binance期货持仓: symbol={}, side={}, quantity={}, pnl={}", 
                                    symbol, side, positionAmt, unrealizedPnl);
                        }
                    }
                    
//                    log.info("成功获取Binance期货持仓: userId={}, 持仓数量={}", userId, positions.size());
                    return positions;
                })
                .doOnError(error -> {
                    log.error("获取Binance期货持仓失败: userId={}, error={}", userId, error.getMessage(), error);
                })
                .onErrorReturn(new ArrayList<>()); // 出错时返回空列表
    }
    
    /**
     * 获取交易对的精度（小数位数）
     * 从 exchangeInfo 中获取 LOT_SIZE 过滤器的 stepSize，计算精度
     */
    private Mono<Integer> getSymbolPrecision(String symbol) {
        // 如果缓存中有，直接返回
        if (symbolPrecisionCache.containsKey(symbol)) {
            return Mono.just(symbolPrecisionCache.get(symbol));
        }
        
        // 从 API 获取交易所信息
        return futuresApiClient.getExchangeInfo()
                .map(exchangeInfo -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> symbols = (List<Map<String, Object>>) exchangeInfo.get("symbols");
                    if (symbols == null) {
                        log.warn("交易所信息中没有symbols字段，使用默认精度8");
                        return 8; // 默认精度
                    }
                    
                    // 查找对应的交易对
                    for (Map<String, Object> symbolInfo : symbols) {
                        if (symbol.equals(symbolInfo.get("symbol"))) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> filters = (List<Map<String, Object>>) symbolInfo.get("filters");
                            if (filters != null) {
                                // 查找 LOT_SIZE 过滤器
                                for (Map<String, Object> filter : filters) {
                                    if ("LOT_SIZE".equals(filter.get("filterType"))) {
                                        String stepSize = (String) filter.get("stepSize");
                                        if (stepSize != null) {
                                            // stepSize 是字符串，如 "0.001" 表示精度为3位小数
                                            // 计算小数位数
                                            int precision = 0;
                                            if (stepSize.contains(".")) {
                                                String decimalPart = stepSize.split("\\.")[1];
                                                // 去除尾随零
                                                decimalPart = decimalPart.replaceAll("0+$", "");
                                                precision = decimalPart.length();
                                            }
                                            // 缓存精度
                                            symbolPrecisionCache.put(symbol, precision);
                                            log.info("获取交易对精度: symbol={}, stepSize={}, precision={}", symbol, stepSize, precision);
                                            return precision;
                                        }
                                    }
                                }
                            }
                            break;
                        }
                    }
                    
                    // 如果找不到，使用默认精度8
                    log.warn("未找到交易对 {} 的精度信息，使用默认精度8", symbol);
                    symbolPrecisionCache.put(symbol, 8);
                    return 8;
                })
                .onErrorReturn(8); // 出错时返回默认精度8
    }
    
    /**
     * 根据精度格式化数量
     */
    private String formatQuantity(BigDecimal quantity, int precision) {
        return quantity.setScale(precision, BigDecimal.ROUND_DOWN).stripTrailingZeros().toPlainString();
    }
    
    @Override
    public Mono<Order> placeOrder(Order order) {
        log.info("Binance期货下单: symbol={}, side={}, type={}, quantity={}", 
                order.getSymbol(), order.getSide(), order.getType(), order.getQuantity());
        
        if (futuresApiClient == null) {
            log.error("Binance Futures API客户端未初始化");
            return Mono.error(new RuntimeException("Futures API客户端未初始化"));
        }
        
        // 转换订单方向
        // 注意：如果 order.getSide() 是 LONG 或 SHORT，需要判断是开仓还是平仓
        // 对于开仓（加仓）：
        //   LONG 持仓加仓 = BUY 订单（增加多仓）
        //   SHORT 持仓加仓 = SELL 订单（增加空仓）
        // 对于平仓：
        //   LONG 持仓平仓 = SELL 订单（减少多仓）
        //   SHORT 持仓平仓 = BUY 订单（减少空仓）
        // 
        // 判断逻辑：如果 order.getSide() 是 LONG 或 SHORT，且没有指定 positionSide，
        // 则根据订单类型判断。但更简单的方法是：如果 order.getSide() 是 LONG 或 SHORT，
        // 且这是开仓操作（通过检查是否有 reduceOnly 标志，或者通过其他方式判断）
        // 
        // 由于当前实现中，开仓和平仓都使用 LONG/SHORT 作为 side，
        // 我们需要通过其他方式区分。最简单的方法是：
        // - 如果 order.getSide() 是 BUY 或 SELL，直接使用
        // - 如果 order.getSide() 是 LONG 或 SHORT，需要判断是开仓还是平仓
        // 
        // 但是，从代码来看，openPosition 和 closePosition 都传入 LONG/SHORT，
        // 所以我们需要一个标志来区分。暂时使用以下逻辑：
        // - 如果 order.getSide() 是 LONG 或 SHORT，且没有其他信息，默认当作平仓处理
        // - 但这样会导致加仓变成减仓
        // 
        // 更好的方案：在 Order 模型中添加一个字段来标识是开仓还是平仓
        // 或者：修改 openPosition 传入的 side 为 BUY/SELL
        // 
        // 临时解决方案：检查 order 的其他属性来判断
        // 如果 order.getType() 是 "OPEN" 或类似标志，则认为是开仓
        // 否则认为是平仓
        
        // 转换订单方向
        // 逻辑说明：
        // - 如果 order.getSide() 是 LONG 或 SHORT，说明这是平仓订单（从 closePosition 来的）
        //   LONG -> SELL（平多），SHORT -> BUY（平空）
        // - 如果 order.getSide() 是 BUY 或 SELL，说明这是开仓订单（从 openPosition 来的）
        //   直接使用，不需要转换
        final String side; // 声明为final，以便在lambda中使用
        if ("LONG".equals(order.getSide())) {
            side = "SELL"; // 平多
        } else if ("SHORT".equals(order.getSide())) {
            side = "BUY"; // 平空
        } else {
            // 如果order.getSide()已经是BUY或SELL，直接使用（这是开仓订单）
            side = order.getSide();
        }
        
        // 转换订单类型
        final String type; // 声明为final，以便在lambda中使用
        if (order.getType() != null) {
            type = order.getType();
        } else {
            type = "MARKET"; // 默认市价单
        }
        
        // 保存订单信息到final变量，以便在lambda中使用
        final String symbol = order.getSymbol();
        final BigDecimal quantityValue = order.getQuantity();
        
        // 确定持仓方向（positionSide）
        // 逻辑说明：
        // - 如果 order.getSide() 是 LONG 或 SHORT，说明这是平仓订单
        //   使用原值作为 positionSide（LONG 或 SHORT）
        // - 如果 order.getSide() 是 BUY 或 SELL，说明这是开仓订单
        //   BUY -> LONG（开多仓），SELL -> SHORT（开空仓）
        final String positionSide;
        if ("LONG".equals(order.getSide()) || "SHORT".equals(order.getSide())) {
            // 平仓订单：使用原值作为 positionSide
            positionSide = order.getSide();
        } else {
            // 开仓订单：根据订单方向推断 positionSide
            // BUY 订单 -> LONG（开多仓）
            // SELL 订单 -> SHORT（开空仓）
            positionSide = "BUY".equals(side) ? "LONG" : "SHORT";
        }
        
        // 获取交易对精度并格式化数量
        return getSymbolPrecision(symbol)
                .flatMap(precision -> {
                    // 根据精度格式化数量（向下取整，符合Binance要求）
                    String formattedQuantity = formatQuantity(quantityValue, precision);
                    log.info("格式化数量: symbol={}, 原始数量={}, 精度={}, 格式化后={}", 
                            symbol, quantityValue, precision, formattedQuantity);
                    
                    // 注意：在双向持仓模式下，Binance不接受reduceOnly参数
                    // 只使用positionSide即可，系统会自动识别这是平仓订单
                    // 调用期货API下单（不传reduceOnly参数）
                    return futuresApiClient.placeOrder(
                            symbol,
                            side,
                            type,
                            formattedQuantity,
                            positionSide,
                            null  // 不传reduceOnly，因为双向持仓模式不支持
                    );
                })
        .map(response -> {
            // 解析响应，更新订单信息
            String orderId = String.valueOf(response.getOrDefault("orderId", ""));
            String status = (String) response.getOrDefault("status", "UNKNOWN");
            
            log.info("Binance期货下单响应: orderId={}, status={}", orderId, status);
            
            // 更新订单对象
            order.setOrderId(orderId);
            order.setStatus(status);
            
            return order;
        })
        .doOnError(error -> {
            log.error("Binance期货下单失败: symbol={}, side={}, error={}", 
                    symbol, side, error.getMessage());
        });
    }
    
    @Override
    public Mono<Boolean> cancelOrder(String orderId) {
        log.info("Binance取消订单: orderId={}", orderId);
        return Mono.just(true);
    }
    
    @Override
    public Mono<Order> getOrder(String orderId) {
        log.info("Binance查询订单: orderId={}", orderId);
        return Mono.just(new Order());
    }
    
    @Override
    public Flux<AccountInfo> subscribeAccountUpdates(String userId) {
        log.info("订阅Binance期货账户更新: userId={}", userId);
        
        if (futuresApiClient == null) {
            log.error("Binance Futures API客户端未初始化");
            return Flux.error(new RuntimeException("Futures API客户端未初始化"));
        }
        
        if (futuresWsClient == null) {
            Proxy proxy = createProxy();
            futuresWsClient = new BinanceFuturesWebSocketClient(proxy, futuresApiClient);
        }
        
        // 创建listenKey并订阅用户数据流
        return futuresApiClient.createListenKey()
                .flatMapMany(listenKey -> {
                    log.info("创建Binance期货listenKey成功，开始订阅用户数据流");
                    return futuresWsClient.subscribeUserDataStream(userId, listenKey);
                })
                .doOnError(error -> log.error("Binance期货WebSocket错误: {}", error.getMessage()));
    }
    
    @Override
    public Flux<Map<String, Object>> subscribeMarketData(String symbol) {
        log.info("订阅Binance市场数据: symbol={}", symbol);
        
        if (wsClient == null) {
            Proxy proxy = createProxy();
            wsClient = new BinanceWebSocketClient(apiKey, secretKey, apiClient, proxy);
        }
        
        return wsClient.subscribeMarketData(symbol);
    }
    
    @Override
    public Mono<Boolean> testConnection() {
        return Mono.just(true);
    }
    
    /**
     * 获取实时标记价格（用于更新持仓盈亏）
     */
    public java.math.BigDecimal getRealTimeMarkPrice(String symbol) {
        if (futuresWsClient != null) {
            return futuresWsClient.getMarkPrice(symbol);
        }
        return null;
    }
    
    /**
     * 创建代理对象
     */
    private Proxy createProxy() {
        if (proxyConfig == null) {
            log.debug("ProxyConfig未注入，不使用代理");
            return null;
        }
        
        if (!proxyConfig.isEnabled()) {
            log.debug("代理未启用（proxy.enabled=false）");
            return null;
        }
        
        String proxyUrl = proxyConfig.getProxyUrl();
        if (proxyUrl == null || proxyUrl.isEmpty()) {
            log.debug("代理URL为空，不使用代理");
            return null;
        }
        
        try {
            URI uri = URI.create(proxyUrl);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 10809;
            
            log.info("创建Binance WebSocket代理: {}:{}", host, port);
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
            return proxy;
        } catch (Exception e) {
            log.error("创建代理失败: {}", e.getMessage(), e);
            return null;
        }
    }
}

