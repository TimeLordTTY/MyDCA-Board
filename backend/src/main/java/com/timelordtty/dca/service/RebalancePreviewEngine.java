package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.AllocationPolicyDTO.Policy;
import com.timelordtty.dca.dto.FinanceRadarDTO;
import com.timelordtty.dca.dto.RebalancePreviewDTO;
import com.timelordtty.dca.dto.RebalancePreviewDTO.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Pure calculator: does not allocate the remainder among other assets or assume executable trades. */
final class RebalancePreviewEngine {
    private RebalancePreviewEngine() {}

    static RebalancePreviewDTO calculate(Policy policy, FinanceRadarDTO facts,
                                         List<HoldingService.HoldingInfo> holdings) {
        var c = policy.config();
        var warnings = new ArrayList<FinanceRadarDTO.Warning>();
        for (var code : List.of("TAXES", "FEES", "SLIPPAGE", "TRADING_RESTRICTIONS", "FX"))
            warnings.add(new FinanceRadarDTO.Warning(code, "NOT_MODELED", "未建模：" + code));
        warnings.add(new FinanceRadarDTO.Warning("PRICE_SOURCE", "UNKNOWN", "快照未提供原始价格来源"));
        warnings.add(new FinanceRadarDTO.Warning("ROUNDING", "NOT_MODELED", "假设金额四舍五入至分，亚分偏离可能仍存在；不分配其余资产、不校验资金可用性"));
        var prices = new ArrayList<PriceEvidence>();
        if (facts != null) {
            if (facts.warnings() != null) warnings.addAll(facts.warnings());
            if (facts.markets() != null) for (var m : facts.markets()) {
                if (m != null) prices.add(new PriceEvidence(m.productId(), m.status(), m.priceDate(), m.valuationDate(), "UNKNOWN"));
            }
        }
        String issue = null;
        BigDecimal total = null, selected = BigDecimal.ZERO, positions = BigDecimal.ZERO;
        if (!Boolean.TRUE.equals(c.enabled())) issue = "目标配置已停用";
        else if (facts == null || facts.date() == null || !Objects.equals(c.scope(), facts.scope())
                || facts.assets() == null || holdings == null || facts.markets() == null) issue = "组合快照缺失";
        else {
            var a = facts.assets();
            total = a.totalAssets();
            if (!"OK".equals(a.status()) || total == null || total.signum() <= 0
                    || a.cashBalance() == null || a.cashBalance().signum() < 0
                    || a.positionValue() == null || a.positionValue().signum() < 0
                    || a.cashBalance().add(a.positionValue()).compareTo(total) != 0) issue = "现金与持仓汇总未知或不一致";
            else {
                for (var h : holdings) {
                    if (h == null || h.getTotalShares() == null || h.getTotalShares().signum() < 0) {
                        issue = "持仓份额未知"; break;
                    }
                    if (h.getTotalShares().signum() == 0) continue;
                    var matches = facts.markets().stream().filter(Objects::nonNull)
                            .filter(m -> Objects.equals(m.productId(), h.getProductId())).toList();
                    var m = matches.size() == 1 ? matches.get(0) : null;
                    if (h.getProductId() == null || h.getAssetType() == null || h.getAssetType().isBlank()
                            || h.getMarketValue() == null || h.getMarketValue().signum() < 0
                            || m == null || !"OK".equals(m.status()) || m.priceDate() == null || m.valuationDate() == null
                            || m.priceDate().isAfter(facts.date()) || m.valuationDate().isAfter(facts.date())) {
                        issue = "价格或持仓分类未知/陈旧"; break;
                    }
                    positions = positions.add(h.getMarketValue());
                    if (c.productId() != null ? c.productId().equals(h.getProductId()) : c.assetType().equals(h.getAssetType()))
                        selected = selected.add(h.getMarketValue());
                }
                if (issue == null && positions.compareTo(a.positionValue()) != 0) issue = "持仓快照与汇总不一致";
                if ("CASH".equals(c.assetType())) selected = a.cashBalance();
            }
        }
        if (issue != null) {
            warnings.add(new FinanceRadarDTO.Warning("SNAPSHOT", "UNKNOWN", issue));
            return new RebalancePreviewDTO(policy.id(), "UNKNOWN", issue, null, null, c.target(), c.lowerBound(),
                    c.upperBound(), null, null, null, facts == null ? null : facts.date(), List.copyOf(prices), List.copyOf(warnings));
        }
        BigDecimal lower = total.multiply(c.lowerBound()), upper = total.multiply(c.upperBound());
        String status = selected.compareTo(lower) < 0 ? "BELOW_BAND" : selected.compareTo(upper) > 0 ? "ABOVE_BAND" : "IN_RANGE";
        BigDecimal bandAmount = selected.compareTo(lower) < 0 ? lower : selected.compareTo(upper) > 0 ? upper : selected;
        return new RebalancePreviewDTO(policy.id(), status, "情景调整/假设金额：固定总资产，在选中资产与其余组合之间等额调整；仅为数学计算器",
                total, selected.divide(total, 12, RoundingMode.HALF_UP), c.target(), c.lowerBound(), c.upperBound(),
                selected.subtract(total.multiply(c.target())).multiply(new BigDecimal("100")).divide(total, 12, RoundingMode.HALF_UP),
                scenario(total.multiply(c.target()).subtract(selected)), scenario(bandAmount.subtract(selected)),
                facts.date(), List.copyOf(prices), List.copyOf(warnings));
    }

    // Round once, then negate: the two hypothetical amounts always sum to exactly zero.
    private static Scenario scenario(BigDecimal adjustment) {
        var rounded = adjustment.setScale(2, RoundingMode.HALF_UP);
        return new Scenario(rounded, rounded.negate());
    }
}
