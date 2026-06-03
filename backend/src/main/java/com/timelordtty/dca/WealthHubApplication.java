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
/**
 * 业务注释规范化: WealthHubApplication 后端组件，服务于 MyDCA-Board 财富中枢业务流程。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class WealthHubApplication {

    /**
     * 业务注释规范化: 处理 main 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param args args 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public static void main(String[] args) {
        SpringApplication.run(WealthHubApplication.class, args);
    }
}

