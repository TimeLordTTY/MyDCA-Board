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
public class RealtimeQuoteCleanupTask {

    private static final Logger logger = LoggerFactory.getLogger(RealtimeQuoteCleanupTask.class);

    private final MarketQuoteRealtimeMapper marketQuoteRealtimeMapper;

    /**
     * 默认保留天数（与文档保持一致：30天）
     */
    private static final int RETENTION_DAYS = 30;

    public RealtimeQuoteCleanupTask(MarketQuoteRealtimeMapper marketQuoteRealtimeMapper) {
        this.marketQuoteRealtimeMapper = marketQuoteRealtimeMapper;
    }

    /**
     * 每日凌晨 02:00 执行清理
     */
    @Scheduled(cron = "0 0 2 * * ?")
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

