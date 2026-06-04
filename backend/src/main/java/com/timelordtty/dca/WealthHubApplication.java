package com.timelordtty.dca;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 财富中枢系统主启动类
 * 
 * @author timelordtty
 */
@SpringBootApplication
@MapperScan("com.timelordtty.dca.mapper")
@EnableScheduling
public class WealthHubApplication {

    /**
     * 启动 WealthHub Spring Boot 应用，加载后端配置、Controller、Service 与定时任务 Bean。
     */
    public static void main(String[] args) {
        SpringApplication.run(WealthHubApplication.class, args);
    }
}

