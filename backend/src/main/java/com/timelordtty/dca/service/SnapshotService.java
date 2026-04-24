package com.timelordtty.dca.service;

import com.timelordtty.dca.mapper.HoldingsSnapshotMapper;
import com.timelordtty.dca.mapper.NetWorthSnapshotMapper;
import com.timelordtty.dca.mapper.UserMapper;
import com.timelordtty.dca.model.HoldingsSnapshot;
import com.timelordtty.dca.model.NetWorthSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 快照服务：生成持仓快照与净资产快照
 *
 * 当前实现以个人视图（userId）为主；家庭视图可在后续扩展。
 */
@Service
public class SnapshotService {

    private static final Logger logger = LoggerFactory.getLogger(SnapshotService.class);

    private final UserMapper userMapper;
    private final HoldingService holdingService;
    private final DashboardService dashboardService;
    private final HoldingsSnapshotMapper holdingsSnapshotMapper;
    private final NetWorthSnapshotMapper netWorthSnapshotMapper;

    public SnapshotService(UserMapper userMapper,
                           HoldingService holdingService,
                           DashboardService dashboardService,
                           HoldingsSnapshotMapper holdingsSnapshotMapper,
                           NetWorthSnapshotMapper netWorthSnapshotMapper) {
        this.userMapper = userMapper;
        this.holdingService = holdingService;
        this.dashboardService = dashboardService;
        this.holdingsSnapshotMapper = holdingsSnapshotMapper;
        this.netWorthSnapshotMapper = netWorthSnapshotMapper;
    }

    public void generateAllSnapshotsForDate(LocalDate snapshotDate) {
        List<Long> userIds = userMapper.selectActiveUserIds();
        logger.info("开始生成快照：snapshotDate={}，activeUsers={}", snapshotDate, userIds.size());
        for (Long userId : userIds) {
            try {
                generateHoldingsSnapshot(userId, snapshotDate);
                generateNetWorthSnapshot(userId, snapshotDate);
            } catch (Exception e) {
                logger.error("生成快照失败：userId={}, snapshotDate={}", userId, snapshotDate, e);
            }
        }
        logger.info("快照生成完成：snapshotDate={}", snapshotDate);
    }

    /**
     * 生成持仓快照（个人视图）
     */
    public void generateHoldingsSnapshot(Long userId, LocalDate snapshotDate) {
        List<HoldingService.HoldingInfo> holdings = holdingService.calculateHoldings(userId, null);
        int count = 0;
        for (HoldingService.HoldingInfo h : holdings) {
            if (h == null || h.getProductId() == null) {
                continue;
            }
            BigDecimal shares = nz(h.getTotalShares());
            if (shares.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            HoldingsSnapshot row = new HoldingsSnapshot();
            row.setUserId(userId);
            row.setProductId(h.getProductId());
            row.setSnapshotDate(snapshotDate);
            row.setShares(shares);
            row.setCost(nz(h.getTotalCost()));
            row.setCostMethod("AVERAGE");
            row.setMarketValue(nz(h.getMarketValue()));
            row.setUnrealizedPnl(nz(h.getUnrealizedPnl()));
            row.setFetchDate(snapshotDate);
            row.setIsDirty(false);
            row.setDirtyFromDate(null);

            // 推导 nav：marketValue / shares（仅用于展示与复核；实际净值以 nav 表为准）
            if (shares.compareTo(BigDecimal.ZERO) > 0 && row.getMarketValue().compareTo(BigDecimal.ZERO) > 0) {
                row.setNav(row.getMarketValue().divide(shares, 6, RoundingMode.HALF_UP));
                row.setNavDate(snapshotDate);
            }

            // return_rate：unrealized / cost
            if (row.getCost().compareTo(BigDecimal.ZERO) > 0) {
                row.setReturnRate(row.getUnrealizedPnl().divide(row.getCost(), 10, RoundingMode.HALF_UP));
            }

            holdingsSnapshotMapper.upsert(row);
            count++;
        }
        logger.info("持仓快照生成完成：userId={}, snapshotDate={}, rows={}", userId, snapshotDate, count);
    }

    /**
     * 生成净资产快照（个人视图）
     */
    public void generateNetWorthSnapshot(Long userId, LocalDate snapshotDate) {
        DashboardService.AssetOverview overview = dashboardService.getAssetOverview(userId, null, "PERSONAL");

        List<HoldingService.HoldingInfo> holdings = holdingService.calculateHoldings(userId, null);
        BigDecimal unrealizedPnl = holdings.stream()
                .map(h -> h != null ? nz(h.getUnrealizedPnl()) : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        NetWorthSnapshot row = new NetWorthSnapshot();
        row.setUserId(userId);
        row.setFamilyId(null);
        row.setSnapshotDate(snapshotDate);
        row.setTotalAssets(nz(overview.getTotalAssets()));
        row.setTotalLiabilities(nz(overview.getTotalLiabilities()));
        row.setNetWorth(nz(overview.getNetWorth()));
        row.setCashBalance(nz(overview.getCashBalance()));
        row.setPositionValue(nz(overview.getPositionValue()));

        row.setRealizedPnl(BigDecimal.ZERO);
        row.setUnrealizedPnl(unrealizedPnl);
        row.setIncomePnl(BigDecimal.ZERO);

        row.setFetchDate(snapshotDate);
        row.setIsDirty(false);
        row.setDirtyFromDate(null);

        netWorthSnapshotMapper.upsert(row);
        logger.info("净资产快照生成完成：userId={}, snapshotDate={}", userId, snapshotDate);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

