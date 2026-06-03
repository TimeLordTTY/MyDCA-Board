package com.timelordtty.dca.scheduler;

import com.timelordtty.dca.service.SnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 快照生成任务：每日生成持仓快照与净资产快照
 */
@Component
public class SnapshotGenerationTask {

    /**
     * 日志记录器，用于输出后端运行、调度或异常诊断信息。
     */
    private static final Logger logger = LoggerFactory.getLogger(SnapshotGenerationTask.class);

    /**
     * 依赖的 SnapshotService 服务，用于复用该领域的业务校验和事务逻辑。
     */
    private final SnapshotService snapshotService;

    /**
     * 执行 SnapshotGenerationTask 相关后端逻辑，保持既有业务契约不变。
     */
    public SnapshotGenerationTask(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    /**
     * 每日 21:00 执行（在净值/指标之后）
     */
    @Scheduled(cron = "0 0 21 * * ?")
    public void generateDailySnapshots() {
        LocalDate today = LocalDate.now();
        try {
            logger.info("开始生成每日快照：{}", today);
            snapshotService.generateAllSnapshotsForDate(today);
            logger.info("每日快照生成完成：{}", today);
        } catch (Exception e) {
            logger.error("每日快照生成异常：{}", today, e);
        }
    }
}

