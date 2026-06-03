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
/**
 * 业务注释规范化: IndicatorCalculationTask 调度任务，负责按计划触发行情、指标、快照或派生数据处理。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class IndicatorCalculationTask {

    /**
     * 业务注释规范化: logger 业务字段，承载该对象在后端流程中的核心属性。
     */
    private static final Logger logger = LoggerFactory.getLogger(IndicatorCalculationTask.class);

    /**
     * 业务注释规范化: pythonScriptService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final PythonScriptService pythonScriptService;

    /**
     * 业务注释规范化: 处理 IndicatorCalculationTask 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param pythonScriptService pythonScriptService 业务字段，承载该对象在后端流程中的核心属性。
     */
    public IndicatorCalculationTask(PythonScriptService pythonScriptService) {
        this.pythonScriptService = pythonScriptService;
    }

    /**
     * 每日 20:30 执行（在净值/日K更新后）
     */
    @Scheduled(cron = "0 30 20 * * ?")
    /**
     * 业务注释规范化: 计算 calculateIndicatorsDaily 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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

