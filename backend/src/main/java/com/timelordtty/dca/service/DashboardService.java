package com.timelordtty.dca.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.Order;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

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

    /**
     * 订单服务入口，负责订单查询、创建、取消以及与结算流程的衔接。
     */
    private final OrderService orderService;
    /**
     * 持仓服务入口，负责按账户和产品汇总持仓、成本、市值与盈亏口径。
     */
    private final HoldingService holdingService;
    /**
     * 账户服务入口，负责账户树查询、账户归属校验、账户创建更新以及余额调整编排。
     */
    private final AccountService accountService;

    /**
     * 装配订单、持仓和账户服务，用于聚合首页资产、待办和收益概览。
     */
    public DashboardService(OrderService orderService, HoldingService holdingService, AccountService accountService) {
        this.orderService = orderService;
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
     * 获取今日建议（Phase 1：仅实现“待结算订单”建议）
     *
     * 规则：
     * - 订单状态为 PENDING
     * - expectedConfirmDate <= today（今天需要结算 + 逾期未结算）
     */
    public List<TodayAction> getTodayActions(Long userId) {
        LocalDate today = LocalDate.now();
        List<Order> userOrders = orderService.getOrdersByUserId(userId);

        return userOrders.stream()
                .filter(o -> "PENDING".equals(o.getStatus()))
                .filter(o -> o.getExpectedConfirmDate() == null || !o.getExpectedConfirmDate().isAfter(today))
                .sorted((a, b) -> {
                    // 越早应确认的排越前，null的排最后
                    if (a.getExpectedConfirmDate() == null && b.getExpectedConfirmDate() == null) {
                        return String.valueOf(a.getOrderId()).compareTo(String.valueOf(b.getOrderId()));
                    }
                    if (a.getExpectedConfirmDate() == null) return 1; // null排后面
                    if (b.getExpectedConfirmDate() == null) return -1; // null排后面
                    int cmp = a.getExpectedConfirmDate().compareTo(b.getExpectedConfirmDate());
                    if (cmp != 0) return cmp;
                    return String.valueOf(a.getOrderId()).compareTo(String.valueOf(b.getOrderId()));
                })
                .map(o -> {
                    TodayAction a = new TodayAction();
                    a.setId(o.getOrderId());
                    a.setType("SETTLE_ORDER");
                    a.setTitle("结算订单 " + safeOrderShortId(o.getOrderId()));

                    String amountText = o.getAmount() != null ? ("金额 " + o.getAmount()) : null;
                    String sharesText = o.getShares() != null ? ("份额 " + o.getShares()) : null;
                    String base = amountText != null ? amountText : (sharesText != null ? sharesText : "—");

                    String dueLabel = "待结算";
                    String priority = "MEDIUM";
                    if (o.getExpectedConfirmDate() != null) {
                        String due = o.getExpectedConfirmDate().toString();
                        dueLabel = o.getExpectedConfirmDate().isBefore(today) ? "已逾期" : "今日应结算";
                        a.setDescription(dueLabel + " · 确认日期 " + due + " · " + base);
                        priority = o.getExpectedConfirmDate().isBefore(today) ? "HIGH" : "MEDIUM";
                    } else {
                        a.setDescription(dueLabel + " · " + base);
                    }

                    a.setPriority(priority);
                    a.setActionUrl("/orders?settle=" + o.getOrderId());
                    return a;
                })
                .collect(Collectors.toList());
    }

    private String safeOrderShortId(String orderId) {
        if (orderId == null) return "";
        return orderId.length() <= 8 ? orderId : orderId.substring(orderId.length() - 8);
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
        
        // 统一转换为大写进行比较
        String normalizedViewType = viewType != null ? viewType.toUpperCase() : "PERSONAL";
        
        // 查询账户余额（获取账户树，父账户余额已自动计算为子账户之和）
        List<Account> accounts = accountService.getAccountTree(
            "PERSONAL".equals(normalizedViewType) ? userId : null,
            "FAMILY".equals(normalizedViewType) ? familyId : null
        );
        
        // 调试日志：检查查询结果
        System.out.println("DashboardService.getAssetOverview - userId: " + userId + ", familyId: " + familyId + ", viewType: " + normalizedViewType);
        System.out.println("DashboardService.getAssetOverview - 查询到的账户数量: " + accounts.size());

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
        
        // 调试日志：检查叶子账户
        System.out.println("DashboardService.getAssetOverview - 叶子账户数量: " + allLeafAccounts.size());
        for (Account acc : allLeafAccounts) {
            System.out.println("  - 账户: " + acc.getAccountName() + ", 类型: " + acc.getAccountType() + ", 余额: " + acc.getBalance());
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
        List<HoldingService.HoldingInfo> holdings = holdingService.calculateHoldings(userId, familyId);
        BigDecimal positionValue = holdings.stream()
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

        // 调试日志：检查计算结果
        System.out.println("DashboardService.getAssetOverview - 计算结果:");
        System.out.println("  - cashBalance: " + cashBalance);
        System.out.println("  - positionValue: " + positionValue);
        System.out.println("  - totalLiabilities: " + totalLiabilities);
        System.out.println("  - totalAssets: " + totalAssets);
        System.out.println("  - netWorth: " + overview.getNetWorth());

        return overview;
    }

    public static class AssetOverview {
        /**
         * 资产总额，汇总现金、持仓市值和其他资产项目。
         */
        private BigDecimal totalAssets;
        @JsonProperty("liability")
        /**
         * 负债总额，用于从总资产中扣除后计算净资产。
         */
        private BigDecimal totalLiabilities;
        /**
         * 净资产，表示总资产扣除负债后的家庭或个人资产结果。
         */
        private BigDecimal netWorth;
        /**
         * 现金余额，表示统计时点账户或总览口径下可用现金资产金额。
         */
        private BigDecimal cashBalance;
        /**
         * 持仓市值，表示统计时点基金、股票或其他持仓按当前价格折算后的资产金额。
         */
        private BigDecimal positionValue; // 持仓市值

        // Getters and setters
        /**
         * 返回金额字段，按所属账户、持仓或统计口径计量。
         */
        public BigDecimal getTotalAssets() { return totalAssets; }
        /**
         * 设置金额字段，按所属账户、持仓或统计口径计量。
         */
        public void setTotalAssets(BigDecimal totalAssets) { this.totalAssets = totalAssets; }
        /**
         * 读取总负债金额，用于计算家庭或个人净资产。
         */
        public BigDecimal getTotalLiabilities() { return totalLiabilities; }
        /**
         * 设置总负债金额，用于净资产快照计算。
         */
        public void setTotalLiabilities(BigDecimal totalLiabilities) { this.totalLiabilities = totalLiabilities; }
        /**
         * 读取净资产金额，等于资产扣除负债后的统计结果。
         */
        public BigDecimal getNetWorth() { return netWorth; }
        /**
         * 设置净资产金额，保存总资产扣除负债后的结果。
         */
        public void setNetWorth(BigDecimal netWorth) { this.netWorth = netWorth; }
        /**
         * 读取现金余额，表示统计时点可用现金资产。
         */
        public BigDecimal getCashBalance() { return cashBalance; }
        /**
         * 设置现金余额，用于保存净值快照或首页资产汇总结果。
         */
        public void setCashBalance(BigDecimal cashBalance) { this.cashBalance = cashBalance; }
        /**
         * 读取持仓市值，表示统计时点证券或基金持仓折算金额。
         */
        public BigDecimal getPositionValue() { return positionValue; }
        /**
         * 设置持仓市值，用于保存按最新价格计算出的资产金额。
         */
        public void setPositionValue(BigDecimal positionValue) { this.positionValue = positionValue; }
    }

    /**
     * 今日建议（TodayAction）
     * 
     * Phase 1阶段：建议功能未实现，返回空列表
     * Phase 3阶段：会实现策略引擎和建议生成
     */
    public static class TodayAction {
        /**
         * 主键 ID，用于数据库内部唯一定位记录。
         */
        private String id;
        /**
         * 首页待办类型，用于区分结算提醒、异常提示或其他操作卡片。
         */
        private String type;
        /**
         * 展示或唯一标识字段，用于人工识别和业务查找。
         */
        private String title;
        /**
         * 首页待办说明文字，描述主人需要关注的事项。
         */
        private String description;
        /**
         * 首页待办优先级，通常为 HIGH、MEDIUM 或 LOW。
         */
        private String priority; // HIGH/MEDIUM/LOW
        /**
         * 首页待办跳转地址，指向可处理该事项的前端页面。
         */
        private String actionUrl;

        /**
         * 返回主键 ID，用于数据库内部唯一定位记录。
         */
        public String getId() { return id; }
        /**
         * 设置主键 ID，用于数据库内部唯一定位记录。
         */
        public void setId(String id) { this.id = id; }
        /**
         * 读取首页待办类型，供前端选择图标、颜色和处理入口。
         */
        public String getType() { return type; }
        /**
         * 设置首页待办类型，标记该卡片属于结算、风险或其他提醒。
         */
        public void setType(String type) { this.type = type; }
        /**
         * 返回展示或唯一标识字段，用于人工识别和业务查找。
         */
        public String getTitle() { return title; }
        /**
         * 设置展示或唯一标识字段，用于人工识别和业务查找。
         */
        public void setTitle(String title) { this.title = title; }
        /**
         * 读取首页待办卡片的描述、优先级或跳转地址，供前端渲染操作提示。
         */
        public String getDescription() { return description; }
        /**
         * 设置首页待办说明文字，描述需要主人处理或关注的事项。
         */
        public void setDescription(String description) { this.description = description; }
        /**
         * 读取首页待办卡片的描述、优先级或跳转地址，供前端渲染操作提示。
         */
        public String getPriority() { return priority; }
        /**
         * 设置首页待办优先级，影响前端排序和视觉强调。
         */
        public void setPriority(String priority) { this.priority = priority; }
        /**
         * 读取首页待办卡片的描述、优先级或跳转地址，供前端渲染操作提示。
         */
        public String getActionUrl() { return actionUrl; }
        /**
         * 设置首页待办跳转地址，指向可完成处理的页面。
         */
        public void setActionUrl(String actionUrl) { this.actionUrl = actionUrl; }
    }
}

