package com.timelordtty.dca.service;

import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.Order;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 看板服务（DashboardService）
 * 
 * 职责：为前端看板提供聚合数据（如待结算订单、资产总览等），将多个数据源（账户、订单、持仓）进行业务级聚合
 * 
 * 资产概览计算公式：
 * - 现金余额（cashBalance）= Σ(所有资产类账户的balance，包括BANK、PAYMENT、BROKER、MMF、CASH等，只计算REAL账户的叶子账户)
 * - 持仓市值（positionValue）= Σ(所有持仓的marketValue)，需要外部行情数据
 * - 总资产（totalAssets）= 现金余额 + 持仓市值 + 其他资产
 * - 总负债（totalLiabilities）= Σ(所有负债账户的balance，如信用卡、花呗、白条、贷款，只计算REAL账户的叶子账户)
 * - 净资产（netWorth）= 总资产 - 总负债
 * 
 * 注意：
 * - 看板数据以可展示性为主，部分计算为近似/聚合值（例如持仓市值需要额外行情数据）
 * - 应以详细报表/账务数据为准
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@Service
public class DashboardService {

    private final OrderService orderService;
    private final AccountMapper accountMapper;
    private final HoldingService holdingService;
    private final AccountService accountService;

    public DashboardService(OrderService orderService, AccountMapper accountMapper, HoldingService holdingService, AccountService accountService) {
        this.orderService = orderService;
        this.accountMapper = accountMapper;
        this.holdingService = holdingService;
        this.accountService = accountService;
    }

    /**
     * 获取待结算的订单列表（用于看板聚合）
     * @return 待结算订单列表
     */
    public List<Order> getPendingSettlements() {
        return orderService.getPendingOrders();
    }

    /**
     * 生成资产概览（总资产/负债/净值/现金）
     * 
     * 计算公式：
     * - 现金余额（cashBalance）= Σ(所有资产类账户的balance，包括BANK、PAYMENT、BROKER、MMF、CASH等，只计算REAL账户的叶子账户)
     * - 持仓市值（positionValue）= Σ(所有持仓的marketValue)，需要外部行情数据，当前为0
     * - 总资产（totalAssets）= 现金余额 + 持仓市值 + 其他资产
     * - 总负债（totalLiabilities）= Σ(所有负债账户的balance，如信用卡、花呗、白条、贷款，只计算REAL账户的叶子账户)
     * - 净资产（netWorth）= 总资产 - 总负债
     * 
     * 流程说明：
     * 1. 查询账户余额（现金、负债等）
     * 2. 计算持仓信息（通过HoldingService）
     * 3. 聚合计算总资产、总负债、净资产
     * 
     * 注意：
     * - 持仓市值需要外部行情数据，当前不计算（marketValue=0）
     * - 其他资产（如外部账户）暂不包含
     * 
     * @param userId 用户ID（个人视图）
     * @param familyId 家庭ID（家庭视图）
     * @param viewType 视图类型：PERSONAL/FAMILY
     * @return 资产概览对象
     */
    public AssetOverview getAssetOverview(Long userId, Long familyId, String viewType) {
        AssetOverview overview = new AssetOverview();
        
        // 查询账户余额（获取账户树，父账户余额已自动计算为子账户之和）
        List<Account> accounts = accountService.getAccountTree(
            "PERSONAL".equals(viewType) ? userId : null,
            "FAMILY".equals(viewType) ? familyId : null
        );

        // 收集所有叶子账户（包括父账户的children中的子账户）
        List<Account> allLeafAccounts = new java.util.ArrayList<>();
        for (Account account : accounts) {
            if (account.getChildren() != null && !account.getChildren().isEmpty()) {
                // 父账户，添加所有子账户
                allLeafAccounts.addAll(account.getChildren());
            } else {
                // 叶子账户（没有子账户的独立账户）
                allLeafAccounts.add(account);
            }
        }

        // 计算现金余额（包括所有资产类账户：BANK、PAYMENT、BROKER、MMF、CASH）
        // 只计算 REAL 账户的叶子账户余额
        BigDecimal cashBalance = allLeafAccounts.stream()
            .filter(a -> "REAL".equals(a.getAccountKind())) // 只计算 REAL 账户
            .filter(a -> "BANK".equals(a.getAccountType()) || 
                        "PAYMENT".equals(a.getAccountType()) || 
                        "BROKER".equals(a.getAccountType()) || 
                        "MMF".equals(a.getAccountType()) || 
                        "CASH".equals(a.getAccountType()) ||
                        "OTHER".equals(a.getAccountType())) // 资产类账户类型
            .filter(a -> !"CREDIT_CARD".equals(a.getAccountType()) && 
                        !"HUABEI".equals(a.getAccountType()) && 
                        !"BAITIAO".equals(a.getAccountType()) && 
                        !"LOAN".equals(a.getAccountType())) // 排除信贷账户
            .map(a -> a.getBalance() != null ? a.getBalance() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 计算持仓市值（通过HoldingService）
        // 注意：持仓市值需要外部行情数据，当前HoldingService不计算marketValue，所以这里为0
        // 后续集成行情数据后，可以从HoldingService获取marketValue
        Map<Long, HoldingService.HoldingInfo> holdings = holdingService.calculateHoldings(userId);
        BigDecimal positionValue = holdings.values().stream()
            .map(h -> h.getMarketValue() != null ? h.getMarketValue() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 计算总负债（包括信贷账户和虚拟负债账户）
        // 只计算 REAL 账户的叶子账户余额
        BigDecimal totalLiabilities = allLeafAccounts.stream()
            .filter(a -> "REAL".equals(a.getAccountKind())) // 只计算 REAL 账户
            .filter(a -> "CREDIT_CARD".equals(a.getAccountType()) || 
                        "HUABEI".equals(a.getAccountType()) || 
                        "BAITIAO".equals(a.getAccountType()) || 
                        "LOAN".equals(a.getAccountType()))
            .map(a -> a.getBalance() != null ? a.getBalance() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 加上虚拟负债账户（如果有）
        BigDecimal virtualLiabilities = allLeafAccounts.stream()
            .filter(a -> "VIRTUAL".equals(a.getAccountKind()) && "LIABILITY".equals(a.getVirtualSubtype()))
            .map(Account::getBalance)
            .filter(b -> b != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        totalLiabilities = totalLiabilities.add(virtualLiabilities);

        // 计算总资产 = 现金余额 + 持仓市值 + 其他资产
        BigDecimal totalAssets = cashBalance.add(positionValue);

        overview.setTotalAssets(totalAssets);
        overview.setTotalLiabilities(totalLiabilities);
        overview.setNetWorth(totalAssets.subtract(totalLiabilities));
        overview.setCashBalance(cashBalance);
        overview.setPositionValue(positionValue); // 设置持仓市值

        return overview;
    }

    public static class AssetOverview {
        private BigDecimal totalAssets;
        private BigDecimal totalLiabilities;
        private BigDecimal netWorth;
        private BigDecimal cashBalance;
        private BigDecimal positionValue; // 持仓市值

        // Getters and setters
        public BigDecimal getTotalAssets() { return totalAssets; }
        public void setTotalAssets(BigDecimal totalAssets) { this.totalAssets = totalAssets; }
        public BigDecimal getTotalLiabilities() { return totalLiabilities; }
        public void setTotalLiabilities(BigDecimal totalLiabilities) { this.totalLiabilities = totalLiabilities; }
        public BigDecimal getNetWorth() { return netWorth; }
        public void setNetWorth(BigDecimal netWorth) { this.netWorth = netWorth; }
        public BigDecimal getCashBalance() { return cashBalance; }
        public void setCashBalance(BigDecimal cashBalance) { this.cashBalance = cashBalance; }
        public BigDecimal getPositionValue() { return positionValue; }
        public void setPositionValue(BigDecimal positionValue) { this.positionValue = positionValue; }
    }

    /**
     * 今日建议（TodayAction）
     * 
     * Phase 1阶段：建议功能未实现，返回空列表
     * Phase 3阶段：会实现策略引擎和建议生成
     */
    public static class TodayAction {
        private Long id;
        private String title;
        private String description;
        private String actionType; // BUY/SELL/HOLD等
        private String priority; // HIGH/MEDIUM/LOW

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getActionType() { return actionType; }
        public void setActionType(String actionType) { this.actionType = actionType; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
    }
}

