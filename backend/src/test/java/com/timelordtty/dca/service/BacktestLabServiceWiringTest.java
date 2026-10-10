package com.timelordtty.dca.service;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class BacktestLabServiceWiringTest {
    @Test
    void springCanCreateServiceWithProductionDependencies() {
        // 只验证容器实例化，不连接数据库、不运行回测、不写财务记录。
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(BacktestRunRepository.class, () -> mock(BacktestRunRepository.class));
            context.registerBean(UserService.class, () -> mock(UserService.class));
            context.register(BacktestLabService.class);
            context.refresh();
            assertNotNull(context.getBean(BacktestLabService.class));
        }
    }
}
