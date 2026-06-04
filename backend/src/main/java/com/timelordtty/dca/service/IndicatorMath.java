package com.timelordtty.dca.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 技术指标公式工具类，只做内存计算，不连接数据库，也不读取生产数据。
 */
public final class IndicatorMath {
    private static final int SCALE = 6;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal THREE = BigDecimal.valueOf(3);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal DEFAULT_KD = BigDecimal.valueOf(50).setScale(SCALE, RoundingMode.HALF_UP);

    private IndicatorMath() {
    }

    /**
     * BOLL 指标结果：中轨、上轨、下轨和窗口标准差。
     */
    public record BollValue(BigDecimal middle, BigDecimal upper, BigDecimal lower, BigDecimal std) {
    }

    /**
     * KDJ 计算所需的单日高、低、收盘价。
     */
    public record KdjBar(BigDecimal high, BigDecimal low, BigDecimal close) {
    }

    /**
     * KDJ 指标结果：K、D、J 与当日 RSV。
     */
    public record KdjValue(BigDecimal k, BigDecimal d, BigDecimal j, BigDecimal rsv) {
    }

    /**
     * 计算指定下标对应的 BOLL 值，窗口不足或收盘价缺失时返回 null。
     */
    public static BollValue calculateBoll(List<BigDecimal> closes, int index, int window) {
        if (closes == null || window <= 0 || index < window - 1 || index >= closes.size()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = index - window + 1; i <= index; i++) {
            BigDecimal close = closes.get(i);
            if (close == null) {
                return null;
            }
            sum = sum.add(close);
        }
        BigDecimal middle = sum.divide(BigDecimal.valueOf(window), SCALE, RoundingMode.HALF_UP);
        BigDecimal varianceSum = BigDecimal.ZERO;
        for (int i = index - window + 1; i <= index; i++) {
            BigDecimal diff = closes.get(i).subtract(middle);
            varianceSum = varianceSum.add(diff.multiply(diff));
        }
        BigDecimal variance = varianceSum.divide(BigDecimal.valueOf(window), SCALE + 6, RoundingMode.HALF_UP);
        BigDecimal std = scale(BigDecimal.valueOf(Math.sqrt(variance.doubleValue())));
        BigDecimal upper = scale(middle.add(std.multiply(TWO)));
        BigDecimal lower = scale(middle.subtract(std.multiply(TWO)));
        return new BollValue(middle, upper, lower, std);
    }

    /**
     * 计算指定下标对应的 KDJ 值，窗口不足或行情数据缺失时返回 null。
     */
    public static KdjValue calculateKdj(List<KdjBar> bars, int index, int window, BigDecimal previousK, BigDecimal previousD) {
        if (bars == null || window <= 0 || index < window - 1 || index >= bars.size()) {
            return null;
        }
        BigDecimal highestHigh = null;
        BigDecimal lowestLow = null;
        for (int i = index - window + 1; i <= index; i++) {
            KdjBar bar = bars.get(i);
            if (bar == null || bar.high() == null || bar.low() == null || bar.close() == null) {
                return null;
            }
            highestHigh = highestHigh == null || bar.high().compareTo(highestHigh) > 0 ? bar.high() : highestHigh;
            lowestLow = lowestLow == null || bar.low().compareTo(lowestLow) < 0 ? bar.low() : lowestLow;
        }
        BigDecimal close = bars.get(index).close();
        BigDecimal range = highestHigh.subtract(lowestLow);
        BigDecimal rsv = range.compareTo(BigDecimal.ZERO) == 0
                ? DEFAULT_KD
                : scale(close.subtract(lowestLow).multiply(HUNDRED).divide(range, SCALE, RoundingMode.HALF_UP));
        BigDecimal prevK = previousK != null ? previousK : DEFAULT_KD;
        BigDecimal prevD = previousD != null ? previousD : DEFAULT_KD;
        BigDecimal k = scale(prevK.multiply(TWO).add(rsv).divide(THREE, SCALE, RoundingMode.HALF_UP));
        BigDecimal d = scale(prevD.multiply(TWO).add(k).divide(THREE, SCALE, RoundingMode.HALF_UP));
        BigDecimal j = scale(k.multiply(THREE).subtract(d.multiply(TWO)));
        return new KdjValue(k, d, j, rsv);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
