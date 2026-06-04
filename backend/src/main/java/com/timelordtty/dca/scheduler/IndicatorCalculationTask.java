package com.timelordtty.dca.scheduler;

import com.timelordtty.dca.service.PythonScriptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 指标日更计算任务：定时调用 Python 指标计算脚本
 */
@Component
public class IndicatorCalculationTask {

    /**
     * 日志记录器，用于输出后端运行、调度或异常诊断信息。
     */
    private static final Logger logger = LoggerFactory.getLogger(IndicatorCalculationTask.class);

    /**
     * Python 脚本执行网关，用于触发已接入的行情、指标或产品同步脚本并收集执行结果。
     */
    private final PythonScriptService pythonScriptService;

    /**
     * 装配脚本执行网关，供定时任务触发指标计算脚本。
     */
    public IndicatorCalculationTask(PythonScriptService pythonScriptService) {
        this.pythonScriptService = pythonScriptService;
    }

    /**
     * 每日 20:30 执行（在净值/日K更新后）
     */
    @Scheduled(cron = "0 30 20 * * ?")
    public void calculateIndicatorsDaily() {
        try {
            logger.info("开始执行指标日更计算...");
            String result = pythonScriptService.runIndicatorCalculator();
            logger.info("指标日更计算完成: {}", result);
        } catch (Exception e) {
            logger.error("指标日更计算异常", e);
        }
    }
}

