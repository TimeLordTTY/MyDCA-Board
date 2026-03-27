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

    private static final Logger logger = LoggerFactory.getLogger(SnapshotGenerationTask.class);

    private final SnapshotService snapshotService;

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

