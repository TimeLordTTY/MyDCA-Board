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

    public static void main(String[] args) {
        SpringApplication.run(WealthHubApplication.class, args);
    }
}

