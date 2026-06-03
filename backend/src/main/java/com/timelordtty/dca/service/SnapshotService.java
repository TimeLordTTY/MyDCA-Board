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

    /**
     * 日志记录器，用于输出后端运行、调度或异常诊断信息。
     */
    private static final Logger logger = LoggerFactory.getLogger(SnapshotService.class);

    /**
     * 依赖的 UserMapper Mapper，用于读写对应持久化数据。
     */
    private final UserMapper userMapper;
    /**
     * 依赖的 HoldingService 服务，用于复用该领域的业务校验和事务逻辑。
     */
    private final HoldingService holdingService;
    /**
     * 依赖的 DashboardService 服务，用于复用该领域的业务校验和事务逻辑。
     */
    private final DashboardService dashboardService;
    /**
     * 依赖的 HoldingsSnapshotMapper Mapper，用于读写对应持久化数据。
     */
    private final HoldingsSnapshotMapper holdingsSnapshotMapper;
    /**
     * 依赖的 NetWorthSnapshotMapper Mapper，用于读写对应持久化数据。
     */
    private final NetWorthSnapshotMapper netWorthSnapshotMapper;

    /**
     * 注入 SnapshotService 所需的 Mapper 和 Service 依赖，建立对应业务协作关系。
     */
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

    /**
     * 执行业务写入或状态推进，必须在服务层校验和事务边界内运行。
     * 涉及账本、账户、持仓或订单时，以既有业务规则和事务一致性为准。
     */
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

