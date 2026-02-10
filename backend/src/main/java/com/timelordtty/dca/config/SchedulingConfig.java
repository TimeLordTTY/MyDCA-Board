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
public class SchedulingConfig implements SchedulingConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(SchedulingConfig.class);

    @Override
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
