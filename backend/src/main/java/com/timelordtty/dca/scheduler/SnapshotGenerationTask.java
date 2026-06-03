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
/**
 * 业务注释规范化: SnapshotGenerationTask 调度任务，负责按计划触发行情、指标、快照或派生数据处理。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class SnapshotGenerationTask {

    /**
     * 业务注释规范化: logger 业务字段，承载该对象在后端流程中的核心属性。
     */
    private static final Logger logger = LoggerFactory.getLogger(SnapshotGenerationTask.class);

    /**
     * 业务注释规范化: snapshotService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final SnapshotService snapshotService;

    /**
     * 业务注释规范化: 处理 SnapshotGenerationTask 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param snapshotService snapshotService 业务字段，承载该对象在后端流程中的核心属性。
     */
    public SnapshotGenerationTask(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    /**
     * 每日 21:00 执行（在净值/指标之后）
     */
    @Scheduled(cron = "0 0 21 * * ?")
    /**
     * 业务注释规范化: 生成 generateDailySnapshots 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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

