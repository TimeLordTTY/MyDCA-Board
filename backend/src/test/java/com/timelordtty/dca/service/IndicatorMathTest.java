package com.timelordtty.dca.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 验证技术指标公式的纯单元测试，不启动 Spring，也不连接数据库。
 */
class IndicatorMathTest {

    @Test
    void calculateBollUsesPopulationStddevAndTwoTimesBand() {
        List<BigDecimal> closes = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            closes.add(BigDecimal.valueOf(i));
        }

        IndicatorMath.BollValue value = IndicatorMath.calculateBoll(closes, 19, 20);

        assertEquals("10.500000", value.middle().toPlainString());
        assertEquals("5.766281", value.std().toPlainString());
        assertEquals("22.032562", value.upper().toPlainString());
        assertEquals("-1.032562", value.lower().toPlainString());
    }

    @Test
    void calculateKdjUsesNineDayHighLowRangeAndDefaultPreviousValues() {
        List<IndicatorMath.KdjBar> bars = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            BigDecimal close = BigDecimal.valueOf(i);
            bars.add(new IndicatorMath.KdjBar(close.add(BigDecimal.ONE), close.subtract(BigDecimal.ONE), close));
        }

        IndicatorMath.KdjValue value = IndicatorMath.calculateKdj(bars, 8, 9, null, null);

        assertEquals("90.000000", value.rsv().toPlainString());
        assertEquals("63.333333", value.k().toPlainString());
        assertEquals("54.444444", value.d().toPlainString());
        assertEquals("81.111111", value.j().toPlainString());
    }
}
