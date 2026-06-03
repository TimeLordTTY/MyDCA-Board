package com.timelordtty.dca.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * 定时任务配置类
 * 确保定时任务使用正确的时区（Asia/Shanghai）
 */
@Configuration
/**
 * 业务注释规范化: SchedulingConfig 配置类，负责声明后端运行期的基础设施或框架行为。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class SchedulingConfig implements SchedulingConfigurer {

    /**
     * 业务注释规范化: logger 业务字段，承载该对象在后端流程中的核心属性。
     */
    private static final Logger logger = LoggerFactory.getLogger(SchedulingConfig.class);

    @Override
    /**
     * 业务注释规范化: 处理 configureTasks 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param taskRegistrar taskRegistrar 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        // 设置定时任务使用的时区为 Asia/Shanghai
        ZoneId zoneId = ZoneId.of("Asia/Shanghai");
        taskRegistrar.setScheduler(
            java.util.concurrent.Executors.newScheduledThreadPool(5)
        );
        
        // 设置默认时区
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
        
        logger.info("定时任务配置完成，使用时区: {}", zoneId);
        logger.info("当前系统时区: {}", TimeZone.getDefault().getID());
    }
}
