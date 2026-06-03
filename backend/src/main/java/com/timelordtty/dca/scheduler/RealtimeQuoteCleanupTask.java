package com.timelordtty.dca.scheduler;

import com.timelordtty.dca.mapper.MarketQuoteRealtimeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 实时行情清理任务：删除过期的实时行情数据（TTL）
 */
@Component
/**
 * 业务注释规范化: RealtimeQuoteCleanupTask 调度任务，负责按计划触发行情、指标、快照或派生数据处理。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class RealtimeQuoteCleanupTask {

    /**
     * 业务注释规范化: logger 业务字段，承载该对象在后端流程中的核心属性。
     */
    private static final Logger logger = LoggerFactory.getLogger(RealtimeQuoteCleanupTask.class);

    /**
     * 业务注释规范化: marketQuoteRealtimeMapper 时间字段，用于记录业务动作发生或审计时间。
     */
    private final MarketQuoteRealtimeMapper marketQuoteRealtimeMapper;

    /**
     * 默认保留天数（与文档保持一致：30天）
     */
    /**
     * 业务注释规范化: RETENTION_DAYS 业务字段，承载该对象在后端流程中的核心属性。
     */
    private static final int RETENTION_DAYS = 30;

    /**
     * 业务注释规范化: 处理 RealtimeQuoteCleanupTask 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param marketQuoteRealtimeMapper marketQuoteRealtimeMapper 时间字段，用于记录业务动作发生或审计时间。
     */
    public RealtimeQuoteCleanupTask(MarketQuoteRealtimeMapper marketQuoteRealtimeMapper) {
        this.marketQuoteRealtimeMapper = marketQuoteRealtimeMapper;
    }

    /**
     * 每日凌晨 02:00 执行清理
     */
    @Scheduled(cron = "0 0 2 * * ?")
    /**
     * 业务注释规范化: 处理 cleanupExpiredQuotes 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void cleanupExpiredQuotes() {
        LocalDateTime before = LocalDateTime.now().minusDays(RETENTION_DAYS);
        try {
            int deleted = marketQuoteRealtimeMapper.deleteByQuoteTimeBefore(before);
            logger.info("RealtimeQuoteCleanupTask 完成：删除 quote_time < {} 的记录 {} 条", before, deleted);
        } catch (Exception e) {
            logger.error("RealtimeQuoteCleanupTask 异常：清理 quote_time < {} 失败", before, e);
        }
    }
}

