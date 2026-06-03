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
/**
 * 业务注释规范化: DashboardService 服务类，负责业务规则、账户、账本流水、订单或持仓数据的组合处理。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class DashboardService {

    /**
     * 业务注释规范化: orderService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final OrderService orderService;
    /**
     * 业务注释规范化: holdingService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final HoldingService holdingService;
    /**
     * 业务注释规范化: accountService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final AccountService accountService;

    /**
     * 业务注释规范化: 处理 DashboardService 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param orderService orderService 业务字段，承载该对象在后端流程中的核心属性。
     * @param holdingService holdingService 业务字段，承载该对象在后端流程中的核心属性。
     * @param accountService accountService 业务字段，承载该对象在后端流程中的核心属性。
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
    /**
     * 业务注释规范化: 查询 getPendingSettlements 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
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
    /**
     * 业务注释规范化: 查询 getTodayActions 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param userId 所属用户 ID，用于限定个人数据权限和查询范围。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
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

    /**
     * 业务注释规范化: 处理 safeOrderShortId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该私有方法封装局部复杂逻辑，用于保持统计、展示或校验口径一致。</p>
     * @param orderId 关联订单 ID，用于串联订单创建、资金冻结、结算确认和流水入账。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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
    /**
     * 业务注释规范化: 查询 getAssetOverview 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param userId 所属用户 ID，用于限定个人数据权限和查询范围。
     * @param familyId 所属家庭 ID，用于家庭视角下的数据隔离。
     * @param viewType viewType 类型字段，用于区分不同业务分类并驱动处理分支。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
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
         * 业务注释规范化: totalAssets 业务字段，承载该对象在后端流程中的核心属性。
         */
        private BigDecimal totalAssets;
        @JsonProperty("liability")
        /**
         * 业务注释规范化: totalLiabilities 业务字段，承载该对象在后端流程中的核心属性。
         */
        private BigDecimal totalLiabilities;
        /**
         * 业务注释规范化: netWorth 业务字段，承载该对象在后端流程中的核心属性。
         */
        private BigDecimal netWorth;
        /**
         * 业务注释规范化: cashBalance 金额字段，用于表达该场景下的资金规模或费用口径。
         */
        private BigDecimal cashBalance;
        private BigDecimal positionValue; // 持仓市值

        // Getters and setters
        /**
         * 业务注释规范化: 查询 getTotalAssets 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public BigDecimal getTotalAssets() { return totalAssets; }
        /**
         * 业务注释规范化: 处理 setTotalAssets 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param totalAssets totalAssets 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setTotalAssets(BigDecimal totalAssets) { this.totalAssets = totalAssets; }
        /**
         * 业务注释规范化: 查询 getTotalLiabilities 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public BigDecimal getTotalLiabilities() { return totalLiabilities; }
        /**
         * 业务注释规范化: 处理 setTotalLiabilities 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param totalLiabilities totalLiabilities 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setTotalLiabilities(BigDecimal totalLiabilities) { this.totalLiabilities = totalLiabilities; }
        /**
         * 业务注释规范化: 查询 getNetWorth 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public BigDecimal getNetWorth() { return netWorth; }
        /**
         * 业务注释规范化: 处理 setNetWorth 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param netWorth netWorth 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setNetWorth(BigDecimal netWorth) { this.netWorth = netWorth; }
        /**
         * 业务注释规范化: 查询 getCashBalance 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public BigDecimal getCashBalance() { return cashBalance; }
        /**
         * 业务注释规范化: 处理 setCashBalance 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param cashBalance cashBalance 金额字段，用于表达该场景下的资金规模或费用口径。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setCashBalance(BigDecimal cashBalance) { this.cashBalance = cashBalance; }
        /**
         * 业务注释规范化: 查询 getPositionValue 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public BigDecimal getPositionValue() { return positionValue; }
        /**
         * 业务注释规范化: 处理 setPositionValue 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param positionValue positionValue 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
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
         * 业务注释规范化: 主键 ID，用于在后端内部唯一定位该业务记录。
         */
        private String id;
        /**
         * 业务注释规范化: type 类型字段，用于区分不同业务分类并驱动处理分支。
         */
        private String type;
        /**
         * 业务注释规范化: title 业务字段，承载该对象在后端流程中的核心属性。
         */
        private String title;
        /**
         * 业务注释规范化: description 业务字段，承载该对象在后端流程中的核心属性。
         */
        private String description;
        private String priority; // HIGH/MEDIUM/LOW
        /**
         * 业务注释规范化: actionUrl 业务字段，承载该对象在后端流程中的核心属性。
         */
        private String actionUrl;

        /**
         * 业务注释规范化: 查询 getId 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public String getId() { return id; }
        /**
         * 业务注释规范化: 处理 setId 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setId(String id) { this.id = id; }
        /**
         * 业务注释规范化: 查询 getType 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public String getType() { return type; }
        /**
         * 业务注释规范化: 处理 setType 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param type type 类型字段，用于区分不同业务分类并驱动处理分支。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setType(String type) { this.type = type; }
        /**
         * 业务注释规范化: 查询 getTitle 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public String getTitle() { return title; }
        /**
         * 业务注释规范化: 处理 setTitle 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param title title 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setTitle(String title) { this.title = title; }
        /**
         * 业务注释规范化: 查询 getDescription 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public String getDescription() { return description; }
        /**
         * 业务注释规范化: 处理 setDescription 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param description description 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setDescription(String description) { this.description = description; }
        /**
         * 业务注释规范化: 查询 getPriority 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public String getPriority() { return priority; }
        /**
         * 业务注释规范化: 处理 setPriority 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param priority priority 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setPriority(String priority) { this.priority = priority; }
        /**
         * 业务注释规范化: 查询 getActionUrl 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public String getActionUrl() { return actionUrl; }
        /**
         * 业务注释规范化: 处理 setActionUrl 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param actionUrl actionUrl 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setActionUrl(String actionUrl) { this.actionUrl = actionUrl; }
    }
}

