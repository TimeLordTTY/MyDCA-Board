package com.timelordtty.dca.scheduler;

import com.timelordtty.dca.service.PythonScriptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 行情数据定时采集任务
 * 
 * 定时任务说明：
 * - 场内产品（ETF/股票/期货/期权）：交易时间内每分钟采集一次实时行情
 * - 场外产品（基金）：每日18:00采集净值（T+1日净值通常在18:00更新）
 * - 场内产品日K线：每日15:30采集（收盘后）
 */
@Component
public class MarketDataScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataScheduler.class);

    private final PythonScriptService pythonScriptService;

    // 避免重复/并发执行（启动时 + 定时任务可能重叠）
    private final AtomicBoolean exchangeRealtimeRunning = new AtomicBoolean(false);
    private final AtomicLong lastExchangeRealtimeRunAtMs = new AtomicLong(0);
    // 冷却窗口：防止短时间重复采集（单位：毫秒）
    private static final long EXCHANGE_REALTIME_COOLDOWN_MS = 90_000L;

    // 交易时间：上午 9:30-11:30，下午 13:00-15:00
    private static final LocalTime MARKET_OPEN_MORNING = LocalTime.of(9, 30);
    private static final LocalTime MARKET_CLOSE_MORNING = LocalTime.of(11, 30);
    private static final LocalTime MARKET_OPEN_AFTERNOON = LocalTime.of(13, 0);
    private static final LocalTime MARKET_CLOSE_AFTERNOON = LocalTime.of(15, 0);

    public MarketDataScheduler(PythonScriptService pythonScriptService) {
        this.pythonScriptService = pythonScriptService;
    }

    /**
     * 采集场内产品实时行情
     * 交易时间内每分钟执行一次（9:30-15:00）
     * Cron表达式：每分钟的第0秒执行（仅在交易时间内）
     */
    @Scheduled(cron = "0 * * * * ?")
    public void collectExchangeRealtime() {
        LocalDateTime now = LocalDateTime.now();
        LocalTime currentTime = now.toLocalTime();
        DayOfWeek dayOfWeek = now.getDayOfWeek();

        // 先检查是否在交易时间内，避免不必要的锁获取
        // 只在交易日执行（周一到周五）
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return;
        }

        // 判断是否在交易时间内（排除中午休市时间 11:30-13:00）
        // 上午 9:30-11:30，下午 13:00-15:00
        boolean inTradingHours = (currentTime.isAfter(MARKET_OPEN_MORNING) || currentTime.equals(MARKET_OPEN_MORNING))
                && currentTime.isBefore(MARKET_CLOSE_MORNING)
                || (currentTime.isAfter(MARKET_OPEN_AFTERNOON) || currentTime.equals(MARKET_OPEN_AFTERNOON))
                && (currentTime.isBefore(MARKET_CLOSE_AFTERNOON) || currentTime.equals(MARKET_CLOSE_AFTERNOON));
        
        if (!inTradingHours) {
            return;
        }

        // 交易时间内，尝试获取锁
        if (!tryEnterExchangeRealtime("scheduled")) {
            return;
        }

        try {
            logger.info("开始采集场内产品实时行情...");
            String result = pythonScriptService.collectETFRealtime();
            logger.info("场内产品实时行情采集完成: {}", result);
        } catch (Exception e) {
            logger.error("场内产品实时行情采集异常", e);
        } finally {
            exitExchangeRealtime();
        }
    }

    /**
     * 启动时/定时任务共用的门禁：并发锁 + 冷却窗口
     */
    private boolean tryEnterExchangeRealtime(String source) {
        long nowMs = System.currentTimeMillis();
        long last = lastExchangeRealtimeRunAtMs.get();
        if (nowMs - last < EXCHANGE_REALTIME_COOLDOWN_MS) {
            logger.info("跳过场内实时行情采集（冷却中，source={}，距上次={}ms）", source, (nowMs - last));
            return false;
        }
        if (!exchangeRealtimeRunning.compareAndSet(false, true)) {
            logger.info("跳过场内实时行情采集（已有任务在运行，source={}）", source);
            return false;
        }
        // 进入即记录，避免并发窗口
        lastExchangeRealtimeRunAtMs.set(nowMs);
        return true;
    }

    private void exitExchangeRealtime() {
        exchangeRealtimeRunning.set(false);
    }

    /**
     * 采集场外产品净值
     * 每日18:00执行（T+1日净值通常在18:00更新）
     */
    @Scheduled(cron = "0 0 18 * * ?")
    public void collectOTCNav() {
        try {
            logger.info("开始采集场外产品净值...");
            String result = pythonScriptService.collectFundNav();
            logger.info("场外产品净值采集完成: {}", result);
        } catch (Exception e) {
            logger.error("场外产品净值采集异常", e);
        }
    }

    /**
     * 采集场内产品日K线
     * 每日15:30执行（收盘后）
     */
    @Scheduled(cron = "0 30 15 * * ?")
    public void collectExchangeDaily() {
        try {
            logger.info("开始采集场内产品日K线...");
            String result = pythonScriptService.collectETFDaily();
            logger.info("场内产品日K线采集完成: {}", result);
        } catch (Exception e) {
            logger.error("场内产品日K线采集异常", e);
        }
    }

    /**
     * 服务启动时执行一次历史行情补齐
     * 使用@PostConstruct在服务启动后执行
     */
    @PostConstruct
    public void backfillHistoryOnStartup() {
        try {
            logger.info("服务启动，开始补齐历史行情数据...");
            // 异步执行，避免阻塞服务启动
            new Thread(() -> {
                try {
                    Thread.sleep(5000); // 等待5秒，确保数据库连接等初始化完成
                    String result = pythonScriptService.backfillMarketHistory();
                    logger.info("历史行情补齐完成: {}", result);

                    // 启动后主动采集一次场内实时行情，确保IOPV/溢价等字段尽快有数据可展示
                    // 需求：即使在闭市后新加的产品，启动时也要采一次（不再依赖交易时间判断）
                    try {
                        if (tryEnterExchangeRealtime("startup")) {
                            logger.info("服务启动后主动采集一次场内实时行情（用于IOPV/估值展示）...");
                            String rt = pythonScriptService.collectETFRealtime();
                            logger.info("启动实时行情采集完成: {}", rt);
                        } else {
                            logger.info("启动后不触发场内实时行情采集（冷却窗口内或已有任务在运行）");
                        }
                    } catch (Exception e) {
                        logger.error("启动实时行情采集异常", e);
                    } finally {
                        // 若启动触发走了 tryEnter，这里兜底释放
                        exitExchangeRealtime();
                    }
                } catch (Exception e) {
                    logger.error("历史行情补齐异常", e);
                }
            }).start();
        } catch (Exception e) {
            logger.error("启动历史行情补齐任务异常", e);
        }
    }
}
