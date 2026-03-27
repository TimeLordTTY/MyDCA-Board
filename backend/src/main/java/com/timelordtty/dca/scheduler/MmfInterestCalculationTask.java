package com.timelordtty.dca.scheduler;

import com.timelordtty.dca.service.PythonScriptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 小荷包（货币基金）日收益计算任务。
 *
 * <p>调度时序：
 * <ul>
 *     <li>18:00 场外基金净值采集（见 {@link MarketDataScheduler#collectOTCNav()}）</li>
 *     <li>20:00 本任务按 nav.acc_nav（每万份收益）计算并回填日收益</li>
 *     <li>20:30 指标日更计算（见 {@link IndicatorCalculationTask#calculateIndicatorsDaily()}）</li>
 *     <li>21:00 资产/净值快照生成（见 {@link SnapshotGenerationTask#generateDailySnapshots()}）</li>
 * </ul>
 * 这样可以保证：先有净值，再有收益，再做指标与快照。
 */
@Component
public class MmfInterestCalculationTask {

    private static final Logger logger = LoggerFactory.getLogger(MmfInterestCalculationTask.class);

    private final PythonScriptService pythonScriptService;

    public MmfInterestCalculationTask(PythonScriptService pythonScriptService) {
        this.pythonScriptService = pythonScriptService;
    }

    /**
     * 每日 20:00 执行小荷包 MMF 日收益计算与入账。
     *
     * <p>说明：
     * <ul>
     *     <li>内部调用 Python 脚本 {@code scripts/mmf/mmf_interest_backfill.py}</li>
     *     <li>product_id 固定为 15；平台账户=17；子账户=16</li>
     *     <li>脚本从 2026-02-11 起全量复算，但会根据已存在的 INTEREST 交易做幂等处理</li>
     * </ul>
     */
    @Scheduled(cron = "0 0 20 * * ?")
    public void calculateMmfInterestDaily() {
        try {
            logger.info("开始执行小荷包 MMF 日收益计算任务...");
            String result = pythonScriptService.runMmfDailyInterestForXiaoHeBao();
            logger.info("小荷包 MMF 日收益计算完成: {}", result);
        } catch (Exception e) {
            logger.error("小荷包 MMF 日收益计算任务异常", e);
        }
    }
}

