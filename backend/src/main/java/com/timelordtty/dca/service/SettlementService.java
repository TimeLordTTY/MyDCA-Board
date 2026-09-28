package com.timelordtty.dca.service;

import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.mapper.OrderMapper;
import com.timelordtty.dca.mapper.OrderFundingLineMapper;
import com.timelordtty.dca.mapper.ProductMasterMapper;
import com.timelordtty.dca.mapper.SettlementConfirmMapper;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.LedgerPosting;
import com.timelordtty.dca.model.Order;
import com.timelordtty.dca.model.OrderFundingLine;
import com.timelordtty.dca.model.ProductMaster;
import com.timelordtty.dca.dto.SettlementPostingPreviewDTO;
import com.timelordtty.dca.dto.SettlementPreviewDTO;
import com.timelordtty.dca.model.SettlementConfirm;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结算确认服务（SettlementService）
 *
 * 职责：处理订单的人工结算确认流程，将订单状态从 PENDING 更新为 CONFIRMED，并生成对应的结算记录与会计分录。
 *
 * v0.13.0 校正（以当前 OrderService 真实语义为准）：
 * - BUY / SUBSCRIPTION：下单阶段已经生成付款账本（CASH CREDIT + RECEIVABLE DEBIT）；
 *   结算阶段的真实效果是「清理待结算应收 RECEIVABLE + 形成 POSITION / 关联账户影响 + 手续费」，
 *   不会在结算阶段重复扣同一笔下单现金。
 * - SELL / REDEMPTION：下单阶段只登记内部 SOURCE / TARGET 份额占用，不生成任何账本流水；
 *   结算阶段才真正产生 CASH / POSITION / FEE 影响。
 * - confirm 与 preview 共用同一套纯计算规则：preview 只读，不写 settlement_confirm / ledger_txn /
 *   ledger_posting，也不改 reserved_amount / initial_shares / order.status；confirm 才落账，
 *   并且必须携带 preview 返回的 fresh preview 令牌。
 *
 * 实现要点：
 * - 创建 SettlementConfirm 作为订单到记账的桥梁
 * - 释放订单占用的 reserved_amount（支持组合支付，从 order_funding_line 读取；只释放有金额的出资行）
 * - 根据订单类型生成对应的 ledger_txn/ledger_posting（调用 LedgerService）
 * - 只允许结算当前 user / family 可见的订单与出资账户
 *
 * @author timelordtty
 * @since 1.0.0
 */
@Service
public class SettlementService {

    /**
     * 订单 Mapper，负责订单主记录的查询、创建、状态更新和结算筛选。
     */
    private final OrderMapper orderMapper;
    /**
     * 结算确认 Mapper，负责保存订单成交确认和结算金额明细。
     */
    private final SettlementConfirmMapper settlementConfirmMapper;
    /**
     * 账户持久化 Mapper，负责账户主表的查询、插入、更新和账户树读取。
     */
    private final AccountMapper accountMapper;
    /**
     * 账本服务入口，负责流水事务、分录、余额影响和快速记账编排。
     */
    private final LedgerService ledgerService;
    /**
     * 订单资金来源 Mapper，负责订单创建和结算时读取各账户出资明细。
     */
    private final OrderFundingLineMapper orderFundingLineMapper;
    /**
     * 用户身份服务，用于根据当前登录名定位用户、家庭和角色权限上下文。
     */
    private final UserService userService;
    /**
     * 账户服务入口，负责账户树查询、账户归属校验、账户创建更新以及余额调整编排。
     */
    private final AccountService accountService;
    /**
     * 产品主数据 Mapper，负责产品代码、名称、市场类型和展示顺序的持久化访问。
     */
    private final ProductMasterMapper productMasterMapper;
    /**
     * 券商费率服务，用于根据账户、产品和交易方向计算佣金、平台费、印花税等费用。
     */
    private final BrokerFeeService brokerFeeService;

    /**
     * 装配订单、结算、账本、账户和产品组件，用于成交确认后生成费用、持仓和现金分录。
     */
    public SettlementService(OrderMapper orderMapper, SettlementConfirmMapper settlementConfirmMapper,
                            AccountMapper accountMapper, LedgerService ledgerService,
                            OrderFundingLineMapper orderFundingLineMapper, UserService userService,
                            @Lazy AccountService accountService, ProductMasterMapper productMasterMapper,
                            BrokerFeeService brokerFeeService) {
        this.orderMapper = orderMapper;
        this.settlementConfirmMapper = settlementConfirmMapper;
        this.accountMapper = accountMapper;
        this.ledgerService = ledgerService;
        this.orderFundingLineMapper = orderFundingLineMapper;
        this.userService = userService;
        this.accountService = accountService;
        this.productMasterMapper = productMasterMapper;
        this.brokerFeeService = brokerFeeService;
    }

    /**
     * 人工结算只读预览。
     *
     * <p>重新读取订单、产品、资金来源与当前账户 / 持仓，按与 confirm 完全相同的规则计算“如果现在确认会发生什么”，
     * 但绝不写 settlement_confirm、ledger_txn、ledger_posting，也不改 reserved_amount、initial_shares、
     * order.status。任何阻断原因都会以中文 blockingReasons 返回，而不是抛异常。</p>
     */
    public SettlementPreviewDTO previewSettlement(String orderId, LocalDate confirmDate, LocalDate navDate,
                                                  BigDecimal confirmNav, BigDecimal confirmShares,
                                                  BigDecimal confirmAmount, BigDecimal confirmFee) {
        SettlementComputation computation = runSettlement(orderId, confirmDate, navDate, confirmNav, confirmShares,
                confirmAmount, confirmFee, false, null);
        return toPreviewDTO(computation);
    }

    /**
     * 人工结算确认（真实落账）。
     *
     * <p>必须携带 preview 返回的 freshPreviewToken：服务端会用同一套规则重新计算并比对指纹，
     * 订单状态 / 更新时间、资金来源行、输入参数或关键账户快照任一变化都会使旧令牌失效并阻断确认。
     * 同一订单已有结算确认时直接返回既有结果，绝不重复生成第二套结算流水。</p>
     */
    @Transactional
    public SettlementConfirm confirmSettlement(String orderId, LocalDate confirmDate, LocalDate navDate,
                                               BigDecimal confirmNav, BigDecimal confirmShares,
                                               BigDecimal confirmAmount, BigDecimal confirmFee,
                                               String freshPreviewToken) {
        if (orderId == null || orderId.isBlank()) {
            throw new RuntimeException("订单ID不能为空");
        }
        // 幂等：同一订单已存在 settlement_confirm 时直接返回既有结果，绝不重复生成第二套结算流水。
        SettlementConfirm existing = settlementConfirmMapper.selectByOrderId(orderId);
        if (existing != null) {
            return existing;
        }
        SettlementComputation computation = runSettlement(orderId, confirmDate, navDate, confirmNav, confirmShares,
                confirmAmount, confirmFee, true, freshPreviewToken);
        if (computation.order == null) {
            throw new RuntimeException("订单不存在：" + orderId);
        }
        if (!computation.blockingReasons.isEmpty()) {
            throw new RuntimeException(String.join("；", computation.blockingReasons));
        }
        return computation.settlement;
    }

    /**
     * 结算核心计算：preview（apply=false，只读）与 confirm（apply=true，落账）复用同一套规则。
     *
     * <p>所有阻断条件都以 blockingReasons 形式返回，不会在只读阶段抛业务异常；
     * apply=true 时的写入集中在方法末尾，令牌校验通过后才会发生。</p>
     */
    private SettlementComputation runSettlement(String orderId, LocalDate confirmDate, LocalDate navDate,
                                               BigDecimal confirmNav, BigDecimal confirmShares,
                                               BigDecimal confirmAmount, BigDecimal confirmFee,
                                               boolean apply, String freshPreviewToken) {
        SettlementComputation computation = new SettlementComputation();
        computation.orderId = orderId;
        computation.confirmDate = confirmDate;
        computation.navDate = navDate;
        computation.confirmNav = confirmNav;
        computation.requestedShares = confirmShares;
        computation.requestedAmount = confirmAmount;
        computation.requestedFee = confirmFee;
        computation.confirmShares = confirmShares;
        computation.confirmAmount = confirmAmount;

        if (orderId == null || orderId.isBlank()) {
            return block(computation, "订单ID不能为空");
        }
        Order order = orderMapper.selectByOrderId(orderId);
        computation.order = order;
        if (order == null) {
            return block(computation, "订单不存在：" + orderId);
        }
        computation.orderType = order.getOrderType();
        computation.orderTypeLabel = orderTypeLabelOf(order.getOrderType());
        computation.orderStatus = order.getStatus();
        if (!"PENDING".equals(order.getStatus())) {
            return block(computation, "订单当前状态为 " + order.getStatus() + "，只有 PENDING 订单可以结算");
        }
        if (settlementConfirmMapper.selectByOrderId(orderId) != null) {
            return block(computation, "订单已完成结算（已存在结算确认记录），请勿重复结算");
        }

        // 当前 user / family 归属：preview 只读读取，不创建任何账户
        com.timelordtty.dca.dto.AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        computation.ownerType = currentUser.getFamilyId() != null ? "FAMILY" : "PERSONAL";
        computation.ownerUserId = currentUser.getId();
        computation.ownerFamilyId = currentUser.getFamilyId();

        // 创建结算确认记录
        SettlementConfirm settlement = new SettlementConfirm();
        settlement.setOrderId(orderId);
        settlement.setConfirmDate(confirmDate);
        settlement.setConfirmDatetime(LocalDateTime.now());
        settlement.setNavDate(navDate);
        settlement.setConfirmNav(confirmNav);
        settlement.setConfirmShares(confirmShares);
        settlement.setConfirmAmount(confirmAmount);
        // 查询order_funding_line，获取所有资金来源行（支持组合支付）
        List<OrderFundingLine> fundingLines = orderFundingLineMapper.selectByOrderId(orderId);
        computation.fundingLines = fundingLines;
        if (fundingLines.isEmpty()) {
            return block(computation, "订单没有资金来源记录，无法结算：" + orderId);
        }

        // 获取产品信息（用于费率计算与预览展示）
        ProductMaster product = productMasterMapper.selectById(order.getProductId());
        computation.product = product;
        if (product == null) {
            return block(computation, "产品不存在或已删除：" + order.getProductId());
        }
        computation.currency = product.getCurrency() != null ? product.getCurrency() : "CNY";

        // 权限与一致性加固：出资账户必须属于当前 user / family 可见范围
        List<Account> fundingAccounts = new ArrayList<>();
        boolean anyVisibleFundingAccount = false;
        for (OrderFundingLine fundingLine : fundingLines) {
            Account fundingAccount = accountMapper.selectById(fundingLine.getAccountId());
            fundingAccounts.add(fundingAccount);
            computation.snapshot(fundingAccount);
            if (fundingAccount != null
                    && isVisibleToUserOrFamily(fundingAccount, computation.ownerUserId, computation.ownerFamilyId)) {
                anyVisibleFundingAccount = true;
            }
        }
        if (order.getUserId() != null && !order.getUserId().equals(computation.ownerUserId) && !anyVisibleFundingAccount) {
            return block(computation, "订单不属于当前用户 / 家庭可见范围，不能结算");
        }
        for (int i = 0; i < fundingLines.size(); i++) {
            OrderFundingLine fundingLine = fundingLines.get(i);
            Account fundingAccount = fundingAccounts.get(i);
            if (fundingAccount == null
                    || !isVisibleToUserOrFamily(fundingAccount, computation.ownerUserId, computation.ownerFamilyId)) {
                return block(computation,
                        "资金来源账户不在当前用户 / 家庭可见范围，不能结算：账户#" + fundingLine.getAccountId());
            }
        }
        // 非法负数 / 0 值按业务规则阻断
        if (confirmDate == null) {
            return block(computation, "确认日期不能为空");
        }
        if (navDate == null) {
            return block(computation, "净值日期不能为空");
        }
        if (confirmNav == null || confirmNav.compareTo(BigDecimal.ZERO) <= 0) {
            return block(computation, "实际净值必须大于 0");
        }
        if (confirmShares != null && confirmShares.compareTo(BigDecimal.ZERO) < 0) {
            return block(computation, "实际份额不能为负数");
        }
        if (confirmAmount != null && confirmAmount.compareTo(BigDecimal.ZERO) < 0) {
            return block(computation, "实际确认金额不能为负数");
        }
        if (confirmFee != null && confirmFee.compareTo(BigDecimal.ZERO) < 0) {
            return block(computation, "手续费不能为负数");
        }
        if ("SELL".equals(order.getOrderType()) || "REDEMPTION".equals(order.getOrderType())) {
            if (confirmAmount == null || confirmAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return block(computation, "卖出 / 赎回必须填写大于 0 的实际确认金额");
            }
        }

        // 如果 confirmFee 为 null（用户未输入），自动计算费率
        // 注意：如果用户明确输入了0，则使用0，不自动计算
        if (confirmFee == null) {
            // 从资金来源账户中找到券商账户ID
            List<Long> fundingAccountIds = fundingLines.stream()
                    .map(OrderFundingLine::getAccountId)
                    .collect(java.util.stream.Collectors.toList());
            Long brokerAccountId = brokerFeeService.findBrokerAccountId(fundingAccountIds);
            
            // 计算交易金额（买入/申购用总金额，卖出/赎回用确认金额）
            BigDecimal tradeAmount;
            if ("BUY".equals(order.getOrderType()) || "SUBSCRIPTION".equals(order.getOrderType())) {
                tradeAmount = fundingLines.stream()
                        .map(OrderFundingLine::getAmount)
                        .filter(java.util.Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            } else {
                tradeAmount = confirmAmount != null ? confirmAmount : BigDecimal.ZERO;
            }
            
            // 计算手续费
            if (brokerAccountId != null && tradeAmount.compareTo(BigDecimal.ZERO) > 0) {
                confirmFee = brokerFeeService.calculateFee(brokerAccountId, product, order.getOrderType(), tradeAmount);
            } else {
                confirmFee = BigDecimal.ZERO;
            }
        }
        
        // 设置结算确认记录的手续费（使用计算后的值或用户输入的值）
        settlement.setConfirmFee(confirmFee != null ? confirmFee : BigDecimal.ZERO);

        // 出资金额合计（预览展示与结算校验共用）
        computation.totalFundingAmount = fundingLines.stream()
                .map(OrderFundingLine::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 对于买入/申购订单，验证并重新计算份额（确保份额 = (总金额 - 手续费) / 净值）
        if (("BUY".equals(order.getOrderType()) || "SUBSCRIPTION".equals(order.getOrderType()))
            && confirmNav != null && confirmNav.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalAmount = fundingLines.stream()
                    .map(OrderFundingLine::getAmount)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal netAmount = totalAmount.subtract(settlement.getConfirmFee());
            BigDecimal calculatedShares = netAmount.divide(confirmNav, 6, RoundingMode.HALF_UP);
            computation.computedShares = calculatedShares;
            computation.computedAmount = calculatedShares.multiply(confirmNav).setScale(2, RoundingMode.HALF_UP);
            
            // 如果前端传入的份额与计算值差异较大（超过0.01），使用计算值
            if (confirmShares == null ||
                confirmShares.subtract(calculatedShares).abs().compareTo(new BigDecimal("0.01")) > 0) {
                if (confirmShares != null) {
                    computation.warnings.add(String.format(
                        "输入份额 %s 与按（出资金额 - 手续费）/ 净值计算出的份额 %s 差异超过 0.01，最终采用计算值 %s",
                        plain(confirmShares), plain(calculatedShares), plain(calculatedShares)));
                }
                confirmShares = calculatedShares;
                settlement.setConfirmShares(confirmShares);
            }
        } else if ("SELL".equals(order.getOrderType()) || "REDEMPTION".equals(order.getOrderType())) {
            // 卖出 / 赎回：computedAmount 是按来源份额 × 净值估算的毛收入，仅供主人与输入金额对照
            BigDecimal sellShares = sellSourceShares(fundingLines, order);
            if (sellShares.compareTo(BigDecimal.ZERO) > 0) {
                computation.computedAmount = sellShares.multiply(confirmNav).setScale(2, RoundingMode.HALF_UP);
                if (confirmAmount != null
                        && confirmAmount.subtract(computation.computedAmount).abs().compareTo(new BigDecimal("0.01")) > 0) {
                    computation.warnings.add(String.format(
                        "输入确认金额 %s 与按份额 %s × 净值 %s 估算的金额 %s 不一致，最终以输入的确认金额为准",
                        plain(confirmAmount), plain(sellShares), plain(confirmNav), plain(computation.computedAmount)));
                }
            }
            if (confirmAmount != null) {
                computation.computedShares = confirmAmount.divide(confirmNav, 6, RoundingMode.HALF_UP);
            } else {
                computation.computedShares = sellShares;
            }
        }
        computation.confirmShares = confirmShares;
        computation.confirmAmount = confirmAmount;
        computation.confirmFee = settlement.getConfirmFee();

        settlement.setIsManualOverride(false);
        settlement.setConfirmedAt(LocalDateTime.now());

        // 注意：settlement_confirm 落库、reserved_amount 释放、initial_shares 调整与账本写入
        // 统一放在方法末尾的写入阶段，preview（apply=false）不会执行任何写操作。

        // 生成真实分录（调用LedgerService.createTransaction）
        // 根据订单类型生成不同的分录
        List<LedgerPosting> postings = new ArrayList<>();

        // 用户 / 家庭归属已在方法开头读取（preview 阶段同样只读）
        String ownerType = computation.ownerType;
        Long ownerUserId = computation.ownerUserId;
        Long ownerFamilyId = computation.ownerFamilyId;

        if ("BUY".equals(order.getOrderType()) || "SUBSCRIPTION".equals(order.getOrderType())) {
            // 买入/申购结算：
            // 下单时已生成付款流水（CASH CREDIT + RECEIVABLE DEBIT）
            // 结算时只需要：RECEIVABLE CREDIT + POSITION DEBIT（或关联账户入账）+ FEE DEBIT
            
            BigDecimal totalAmount = fundingLines.stream()
                    .map(OrderFundingLine::getAmount)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // 计算成本：使用份额 × 净值
            BigDecimal cost;
            if (confirmShares != null && confirmNav != null && confirmNav.compareTo(BigDecimal.ZERO) > 0) {
                cost = confirmShares.multiply(confirmNav).setScale(2, RoundingMode.HALF_UP);
            } else {
                cost = totalAmount.subtract(confirmFee != null ? confirmFee : BigDecimal.ZERO);
            }

            // 检查产品是否有关联账户
            Account linkedAccount = accountMapper.selectByLinkedProductId(order.getProductId());
            computation.snapshot(linkedAccount);
            boolean hasLinkedAccount = linkedAccount != null;

            // 分离 SOURCE 和 TARGET 类型的 fundingLines
            List<OrderFundingLine> targetLines = fundingLines.stream()
                .filter(fl -> "TARGET".equals(fl.getLineType()))
                .collect(java.util.stream.Collectors.toList());

            // 1. RECEIVABLE CREDIT：清零下单时创建的待结算应收
            Account receivableAccount = virtualAccountFor(apply, computation, "RECEIVABLE", "RECEIVABLE",
                ownerType, ownerUserId, ownerFamilyId, null, null);
            LedgerPosting receivablePosting = new LedgerPosting();
            receivablePosting.setPostingType("CREDIT");
            receivablePosting.setAccountId(receivableAccount.getId());
            receivablePosting.setAccountType("RECEIVABLE");
            receivablePosting.setAmount(totalAmount);
            receivablePosting.setCurrency("CNY");
            postings.add(receivablePosting);

            // 2. DEBIT 端：根据产品类型决定入账方式
            if (!hasLinkedAccount) {
                // 非关联产品：POSITION DEBIT（持仓增加）
                // 优先按资金来源推断券商账户，将 POSITION 记到“券商维度持仓账户”，实现同标的跨券商隔离成本
                List<Long> fundingAccountIds = fundingLines.stream()
                    .map(OrderFundingLine::getAccountId)
                    .collect(java.util.stream.Collectors.toList());
                Long brokerAccountId = brokerFeeService.findBrokerAccountId(fundingAccountIds);
                if (brokerAccountId != null && !isValidBrokerPlatformAccount(brokerAccountId)) {
                    return block(computation, "券商维度持仓账户无法建立：账户#" + brokerAccountId + " 不是券商平台账户");
                }

                Account positionAccount = brokerAccountId != null
                    ? brokerPositionAccountFor(apply, computation,
                        brokerAccountId, order.getProductId(), product.getProductName(), ownerType, ownerUserId, ownerFamilyId)
                    : positionAccountFor(apply, computation,
                        order.getProductId(), product.getProductName(), ownerType, ownerUserId, ownerFamilyId);

                LedgerPosting positionPosting = new LedgerPosting();
                positionPosting.setPostingType("DEBIT");
                positionPosting.setAccountId(positionAccount.getId());
                positionPosting.setAccountType("POSITION");
                positionPosting.setAmount(cost); // 成本，不含fee
                positionPosting.setShares(confirmShares != null ? confirmShares : BigDecimal.ZERO);
                positionPosting.setCurrency(product.getCurrency() != null ? product.getCurrency() : "CNY");
                postings.add(positionPosting);
            } else {
                // 有关联账户的产品：更新 initial_shares（增加购买的份额）
                if (linkedAccount == null) {
                    throw new RuntimeException("关联账户为空，无法更新初始份额");
                }
                BigDecimal currentShares = linkedAccount.getInitialShares();
                if (currentShares == null) {
                    currentShares = BigDecimal.ZERO;
                }
                BigDecimal newShares = currentShares.add(confirmShares != null ? confirmShares : BigDecimal.ZERO);
                computation.linkedAccountId = linkedAccount.getId();
                computation.linkedAccountNewShares = newShares;

                // 如果有 TARGET 账户，生成 CASH DEBIT 分录（关联账户余额增加）
                if (!targetLines.isEmpty()) {
                    BigDecimal netAmount = totalAmount.subtract(confirmFee != null ? confirmFee : BigDecimal.ZERO);
                    BigDecimal remainingAmount = netAmount;
                    for (int i = 0; i < targetLines.size(); i++) {
                        OrderFundingLine targetLine = targetLines.get(i);
                        BigDecimal accountAmount = targetLine.getAmount() != null ? targetLine.getAmount() : remainingAmount;
                        if (i == targetLines.size() - 1) {
                            accountAmount = remainingAmount;
                        } else {
                            remainingAmount = remainingAmount.subtract(accountAmount);
                        }
                        
                        LedgerPosting cashDebitPosting = new LedgerPosting();
                        cashDebitPosting.setPostingType("DEBIT");
                        cashDebitPosting.setAccountId(targetLine.getAccountId());
                        cashDebitPosting.setAccountType("CASH");
                        cashDebitPosting.setAmount(accountAmount);
                        cashDebitPosting.setCurrency(targetLine.getCurrency() != null ? targetLine.getCurrency() : "CNY");
                        postings.add(cashDebitPosting);
                    }
                } else {
                    // 没有明确 TARGET，则将净额入账到关联账户本身（如果是叶子账户）
                    // 或找其第一个子账户
                    BigDecimal netAmount = totalAmount.subtract(confirmFee != null ? confirmFee : BigDecimal.ZERO);
                    Long targetAccountId = linkedAccount.getId();
                    // 检查关联账户是否有子账户
                    List<Account> children = accountMapper.selectChildren(linkedAccount.getId());
                    if (children != null && !children.isEmpty()) {
                        targetAccountId = children.get(0).getId();
                    }
                    LedgerPosting cashDebitPosting = new LedgerPosting();
                    cashDebitPosting.setPostingType("DEBIT");
                    cashDebitPosting.setAccountId(targetAccountId);
                    cashDebitPosting.setAccountType("CASH");
                    cashDebitPosting.setAmount(netAmount);
                    cashDebitPosting.setCurrency("CNY");
                    postings.add(cashDebitPosting);
                }
            }

            // 3. 手续费分录（如果有）
            if (confirmFee != null && confirmFee.compareTo(BigDecimal.ZERO) > 0) {
                Account feeAccount = virtualAccountFor(apply, computation, "FEE", "FEE",
                    ownerType, ownerUserId, ownerFamilyId, null, null);
                
                LedgerPosting feePosting = new LedgerPosting();
                feePosting.setPostingType("DEBIT");
                feePosting.setAccountId(feeAccount.getId());
                feePosting.setAccountType("FEE");
                feePosting.setAmount(confirmFee);
                feePosting.setCurrency(product.getCurrency() != null ? product.getCurrency() : "CNY");
                postings.add(feePosting);
            }
        } else if ("SELL".equals(order.getOrderType()) || "REDEMPTION".equals(order.getOrderType())) {
            // 卖出/赎回：CASH DEBIT（到账账户） + POSITION/CASH CREDIT（持仓/出金账户减少） + FEE DEBIT
            
            // 检查产品是否有关联账户
            Account linkedAccount = accountMapper.selectByLinkedProductId(order.getProductId());
            computation.snapshot(linkedAccount);
            boolean hasLinkedAccount = linkedAccount != null;
            
            // 获取持仓账户（非关联账户产品才需要）
            Account positionAccount = null;
            if (!hasLinkedAccount) {
                // 优先按资金来源推断券商账户，将 POSITION 记到“券商维度持仓账户”，实现同标的跨券商隔离成本
                List<Long> fundingAccountIds = fundingLines.stream()
                    .map(OrderFundingLine::getAccountId)
                    .collect(java.util.stream.Collectors.toList());
                Long brokerAccountId = brokerFeeService.findBrokerAccountId(fundingAccountIds);
                if (brokerAccountId != null && !isValidBrokerPlatformAccount(brokerAccountId)) {
                    return block(computation, "券商维度持仓账户无法建立：账户#" + brokerAccountId + " 不是券商平台账户");
                }

                positionAccount = brokerAccountId != null
                    ? brokerPositionAccountFor(apply, computation,
                        brokerAccountId, order.getProductId(), product.getProductName(), ownerType, ownerUserId, ownerFamilyId)
                    : positionAccountFor(apply, computation,
                        order.getProductId(), product.getProductName(), ownerType, ownerUserId, ownerFamilyId);
            }

            // 注意：使用"摊薄成本法"（同花顺方式）
            // 卖出时 POSITION CREDIT 金额 = 卖出收入（而不是平均成本 × 份额）
            // 这样持仓成本 = 总买入金额 - 总卖出收入
            // 平均成本 = (总买入金额 - 总卖出收入) / 剩余份额

            // 分离出金账户（SOURCE）和到账账户（TARGET）
            List<OrderFundingLine> sourceLines = fundingLines.stream()
                .filter(fl -> "SOURCE".equals(fl.getLineType()) || fl.getLineType() == null)
                .filter(fl -> fl.getShares() != null && fl.getShares().compareTo(BigDecimal.ZERO) > 0)
                .collect(java.util.stream.Collectors.toList());
            List<OrderFundingLine> targetLines = fundingLines.stream()
                .filter(fl -> "TARGET".equals(fl.getLineType()))
                .collect(java.util.stream.Collectors.toList());

            // 计算总份额
            BigDecimal totalShares = sourceLines.isEmpty() ? 
                (confirmShares != null ? confirmShares : BigDecimal.ZERO) :
                sourceLines.stream().map(OrderFundingLine::getShares).reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalConfirmAmount = confirmAmount != null ? confirmAmount : BigDecimal.ZERO;
            
            // 摊薄成本法：POSITION CREDIT 金额 = 卖出收入（totalConfirmAmount）
            // 注意：手续费已在后面单独处理，不从卖出收入中扣除
            BigDecimal totalCostDeduction = totalConfirmAmount;

            // 判断出金账户是否是关联账户的子账户
            boolean sourceIsLinkedChild = false;
            if (linkedAccount != null && !sourceLines.isEmpty()) {
                for (OrderFundingLine sourceLine : sourceLines) {
                    Account sourceAccount = accountMapper.selectById(sourceLine.getAccountId());
                    if (sourceAccount != null && sourceAccount.getParentAccountId() != null 
                        && sourceAccount.getParentAccountId().equals(linkedAccount.getId())) {
                        sourceIsLinkedChild = true;
                        break;
                    }
                }
            }

            if (hasLinkedAccount && sourceIsLinkedChild && !targetLines.isEmpty()) {
                // 有关联账户且出金是关联账户子账户：使用转账模式
                // CASH DEBIT（入金账户增加）+ CASH CREDIT（出金账户减少）
                // 同时需要更新关联账户的 initial_shares（持仓份额）
                
                // 1. CASH DEBIT：入金到 TARGET 账户（净到账金额 = 卖出收入 - 手续费）
                BigDecimal netAmount = totalConfirmAmount.subtract(confirmFee != null ? confirmFee : BigDecimal.ZERO);
                BigDecimal remainingAmount = netAmount;
                for (int i = 0; i < targetLines.size(); i++) {
                    OrderFundingLine targetLine = targetLines.get(i);
                    BigDecimal accountAmount = targetLine.getAmount() != null ? targetLine.getAmount() : remainingAmount;
                    if (i == targetLines.size() - 1) {
                        accountAmount = remainingAmount;
                    } else {
                        remainingAmount = remainingAmount.subtract(accountAmount);
                    }
                    
                    LedgerPosting cashDebitPosting = new LedgerPosting();
                    cashDebitPosting.setPostingType("DEBIT");
                    cashDebitPosting.setAccountId(targetLine.getAccountId());
                    cashDebitPosting.setAccountType("CASH");
                    cashDebitPosting.setAmount(accountAmount);
                    cashDebitPosting.setCurrency(targetLine.getCurrency() != null ? targetLine.getCurrency() : "CNY");
                    postings.add(cashDebitPosting);
                }
                
                // 2. CASH CREDIT：从 SOURCE 账户扣减
                // 新逻辑：固定金额账户优先使用固定金额，其余账户分配剩余金额
                // 注意：为了保证借贷平衡，CREDIT 总额必须等于 DEBIT 总额（即 totalConfirmAmount）
                BigDecimal remainingCreditAmount = totalConfirmAmount;
                
                // 用于存储各账户的分配信息
                List<Object[]> accountAllocations = new ArrayList<>(); // [accountId, amount, shares, currency]
                
                // 用于标记哪些账户已经被处理
                java.util.Set<Long> processedAccountIds = new java.util.HashSet<>();
                
                // 检查是否有非固定金额账户
                boolean hasNonFixedAccount = false;
                for (OrderFundingLine sourceLine : sourceLines) {
                    Account sourceAccount = accountMapper.selectById(sourceLine.getAccountId());
                    if (sourceAccount == null || !Boolean.TRUE.equals(sourceAccount.getIsFixedAmount())) {
                        hasNonFixedAccount = true;
                        break;
                    }
                }
                
                // 第一轮：处理固定金额账户（优先使用固定金额）
                for (OrderFundingLine sourceLine : sourceLines) {
                    Account sourceAccount = accountMapper.selectById(sourceLine.getAccountId());
                    if (sourceAccount != null && Boolean.TRUE.equals(sourceAccount.getIsFixedAmount()) 
                        && sourceAccount.getFixedAmount() != null 
                        && sourceAccount.getFixedAmount().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal fixedAmount = sourceAccount.getFixedAmount();
                        BigDecimal accountAmount;
                        
                        if (hasNonFixedAccount) {
                            // 有非固定金额账户时，固定金额账户使用固定金额（但不超过剩余金额）
                            accountAmount = fixedAmount.min(remainingCreditAmount);
                        } else {
                            // 全是固定金额账户时，按比例分配 totalConfirmAmount 以保证借贷平衡
                            // 使用固定金额作为比例基准
                            accountAmount = totalConfirmAmount.multiply(fixedAmount)
                                .divide(getTotalFixedAmount(sourceLines), 2, RoundingMode.HALF_UP);
                        }
                        
                        // 根据金额和净值计算份额
                        BigDecimal accountShares = accountAmount.divide(confirmNav, 6, RoundingMode.HALF_UP);
                        
                        accountAllocations.add(new Object[]{
                            sourceLine.getAccountId(), 
                            accountAmount, 
                            accountShares, 
                            sourceLine.getCurrency()
                        });
                        
                        processedAccountIds.add(sourceLine.getAccountId());
                        remainingCreditAmount = remainingCreditAmount.subtract(accountAmount);
                    }
                }
                
                // 计算非固定金额账户的原始份额总和（用于按比例分配，只计算未处理的账户）
                BigDecimal nonFixedTotalShares = BigDecimal.ZERO;
                for (OrderFundingLine sourceLine : sourceLines) {
                    // 跳过已经处理的账户
                    if (processedAccountIds.contains(sourceLine.getAccountId())) {
                        continue;
                    }
                    Account sourceAccount = accountMapper.selectById(sourceLine.getAccountId());
                    if (sourceAccount == null || !Boolean.TRUE.equals(sourceAccount.getIsFixedAmount())) {
                        nonFixedTotalShares = nonFixedTotalShares.add(
                            sourceLine.getShares() != null ? sourceLine.getShares() : BigDecimal.ZERO
                        );
                    }
                }
                
                // 第二轮：处理非固定金额账户（分配剩余金额）
                BigDecimal nonFixedRemainingAmount = remainingCreditAmount;
                int nonFixedCount = 0;
                int currentNonFixed = 0;
                for (OrderFundingLine sourceLine : sourceLines) {
                    // 跳过已经处理的账户
                    if (processedAccountIds.contains(sourceLine.getAccountId())) {
                        continue;
                    }
                    Account sourceAccount = accountMapper.selectById(sourceLine.getAccountId());
                    if (sourceAccount == null || !Boolean.TRUE.equals(sourceAccount.getIsFixedAmount())) {
                        nonFixedCount++;
                    }
                }
                
                for (OrderFundingLine sourceLine : sourceLines) {
                    // 跳过已经处理的账户
                    if (processedAccountIds.contains(sourceLine.getAccountId())) {
                        continue;
                    }
                    
                    Account sourceAccount = accountMapper.selectById(sourceLine.getAccountId());
                    if (sourceAccount == null || !Boolean.TRUE.equals(sourceAccount.getIsFixedAmount())) {
                        currentNonFixed++;
                        BigDecimal accountAmount;
                        BigDecimal accountShares;
                        
                        if (currentNonFixed == nonFixedCount) {
                            // 最后一个非固定金额账户：分配所有剩余金额（确保借贷平衡）
                            accountAmount = nonFixedRemainingAmount;
                        } else if (nonFixedTotalShares.compareTo(BigDecimal.ZERO) > 0) {
                            // 按原份额比例分配
                            BigDecimal ratio = sourceLine.getShares().divide(nonFixedTotalShares, 10, RoundingMode.HALF_UP);
                            accountAmount = remainingCreditAmount.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                        } else {
                            // 平均分配
                            accountAmount = remainingCreditAmount.divide(new BigDecimal(nonFixedCount - currentNonFixed + 1), 2, RoundingMode.HALF_UP);
                        }
                        
                        accountShares = accountAmount.divide(confirmNav, 6, RoundingMode.HALF_UP);
                        nonFixedRemainingAmount = nonFixedRemainingAmount.subtract(accountAmount);
                        
                        accountAllocations.add(new Object[]{
                            sourceLine.getAccountId(), 
                            accountAmount, 
                            accountShares, 
                            sourceLine.getCurrency()
                        });
                        
                        processedAccountIds.add(sourceLine.getAccountId());
                    }
                }
                
                // 第三轮：处理未被处理的 SOURCE 账户（按份额比例分配剩余金额）
                // 这确保所有 SOURCE 账户都会生成分录
                if (processedAccountIds.size() < sourceLines.size()) {
                    // 计算未处理账户的份额总和
                    BigDecimal unprocessedTotalShares = BigDecimal.ZERO;
                    int unprocessedCount = 0;
                    for (OrderFundingLine sourceLine : sourceLines) {
                        if (!processedAccountIds.contains(sourceLine.getAccountId())) {
                            unprocessedCount++;
                            if (sourceLine.getShares() != null) {
                                unprocessedTotalShares = unprocessedTotalShares.add(sourceLine.getShares());
                            }
                        }
                    }
                    
                    // 按份额比例分配剩余金额
                    BigDecimal unprocessedRemainingAmount = remainingCreditAmount;
                    int currentUnprocessed = 0;
                    for (OrderFundingLine sourceLine : sourceLines) {
                        if (!processedAccountIds.contains(sourceLine.getAccountId())) {
                            currentUnprocessed++;
                            BigDecimal accountAmount;
                            BigDecimal accountShares;
                            
                            if (currentUnprocessed == unprocessedCount) {
                                // 最后一个未处理账户：分配所有剩余金额（确保借贷平衡）
                                accountAmount = unprocessedRemainingAmount;
                            } else if (unprocessedTotalShares.compareTo(BigDecimal.ZERO) > 0 && sourceLine.getShares() != null) {
                                // 按份额比例分配剩余金额（使用 remainingCreditAmount 作为基准）
                                BigDecimal ratio = sourceLine.getShares().divide(unprocessedTotalShares, 10, RoundingMode.HALF_UP);
                                accountAmount = remainingCreditAmount.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                            } else {
                                // 如果没有份额信息，平均分配剩余金额（使用 remainingCreditAmount 作为基准）
                                accountAmount = remainingCreditAmount.divide(new BigDecimal(unprocessedCount - currentUnprocessed + 1), 2, RoundingMode.HALF_UP);
                            }
                            
                            // 从 unprocessedRemainingAmount 中扣减（用于最后一个账户的精确分配）
                            if (currentUnprocessed < unprocessedCount) {
                                unprocessedRemainingAmount = unprocessedRemainingAmount.subtract(accountAmount);
                            }
                            
                            accountShares = accountAmount.divide(confirmNav, 6, RoundingMode.HALF_UP);
                            
                            accountAllocations.add(new Object[]{
                                sourceLine.getAccountId(), 
                                accountAmount, 
                                accountShares, 
                                sourceLine.getCurrency()
                            });
                            
                            processedAccountIds.add(sourceLine.getAccountId());
                        }
                    }
                    
                    // 更新剩余金额
                    remainingCreditAmount = unprocessedRemainingAmount;
                }
                
                // 如果还有剩余金额（由于四舍五入），调整最后一个账户
                if (remainingCreditAmount.abs().compareTo(new BigDecimal("0.01")) > 0 && !accountAllocations.isEmpty()) {
                    Object[] lastAllocation = accountAllocations.get(accountAllocations.size() - 1);
                    BigDecimal currentAmount = (BigDecimal) lastAllocation[1];
                    lastAllocation[1] = currentAmount.add(remainingCreditAmount).setScale(2, RoundingMode.HALF_UP);
                    lastAllocation[2] = ((BigDecimal) lastAllocation[1]).divide(confirmNav, 6, RoundingMode.HALF_UP);
                }
                
                // 如果全是固定金额账户，需要调整最后一个账户以确保借贷平衡
                if (!hasNonFixedAccount && accountAllocations.size() > 0) {
                    BigDecimal totalCreditAmount = BigDecimal.ZERO;
                    for (Object[] allocation : accountAllocations) {
                        totalCreditAmount = totalCreditAmount.add((BigDecimal) allocation[1]);
                    }
                    BigDecimal diff = totalConfirmAmount.subtract(totalCreditAmount);
                    if (diff.abs().compareTo(new BigDecimal("0.01")) > 0) {
                        // 调整最后一个账户
                        Object[] lastAllocation = accountAllocations.get(accountAllocations.size() - 1);
                        BigDecimal currentAmount = (BigDecimal) lastAllocation[1];
                        lastAllocation[1] = currentAmount.add(diff).setScale(2, RoundingMode.HALF_UP);
                        lastAllocation[2] = ((BigDecimal) lastAllocation[1]).divide(confirmNav, 6, RoundingMode.HALF_UP);
                    }
                }
                
                // 验证总金额：确保所有账户的分配金额总和等于 totalConfirmAmount
                BigDecimal totalAllocatedAmount = BigDecimal.ZERO;
                for (Object[] allocation : accountAllocations) {
                    totalAllocatedAmount = totalAllocatedAmount.add((BigDecimal) allocation[1]);
                }
                
                // 如果总分配金额与 totalConfirmAmount 不一致，调整最后一个账户
                BigDecimal diff = totalConfirmAmount.subtract(totalAllocatedAmount);
                if (diff.abs().compareTo(new BigDecimal("0.01")) > 0 && !accountAllocations.isEmpty()) {
                    Object[] lastAllocation = accountAllocations.get(accountAllocations.size() - 1);
                    BigDecimal currentAmount = (BigDecimal) lastAllocation[1];
                    lastAllocation[1] = currentAmount.add(diff).setScale(2, RoundingMode.HALF_UP);
                    lastAllocation[2] = ((BigDecimal) lastAllocation[1]).divide(confirmNav, 6, RoundingMode.HALF_UP);
                }
                
                // 生成 CASH CREDIT 分录
                for (Object[] allocation : accountAllocations) {
                    LedgerPosting cashCreditPosting = new LedgerPosting();
                    cashCreditPosting.setPostingType("CREDIT");
                    cashCreditPosting.setAccountId((Long) allocation[0]);
                    cashCreditPosting.setAccountType("CASH");
                    cashCreditPosting.setAmount((BigDecimal) allocation[1]);
                    cashCreditPosting.setShares((BigDecimal) allocation[2]);
                    cashCreditPosting.setCurrency(allocation[3] != null ? (String) allocation[3] : "CNY");
                    postings.add(cashCreditPosting);
                }
                
                // 3. 更新关联账户的 initial_shares（减少赎回的份额）
                // 这确保持仓计算正确反映赎回后的份额
                if (linkedAccount == null) {
                    throw new RuntimeException("关联账户为空，无法更新初始份额");
                }
                BigDecimal currentShares = linkedAccount.getInitialShares();
                if (currentShares != null) {
                    BigDecimal newShares = currentShares.subtract(totalShares);
                    if (newShares.compareTo(BigDecimal.ZERO) < 0) {
                        newShares = BigDecimal.ZERO;
                    }
                    computation.linkedAccountId = linkedAccount.getId();
                    computation.linkedAccountNewShares = newShares;
                }
            } else {
                // 普通模式：CASH DEBIT + POSITION CREDIT
                
                // 1. CASH DEBIT：到账到 TARGET 账户（如果有），否则到 SOURCE 账户
                // 计算净到账金额（卖出收入 - 手续费）
                BigDecimal netAmountForCash = totalConfirmAmount.subtract(confirmFee != null ? confirmFee : BigDecimal.ZERO);
                
                if (!targetLines.isEmpty()) {
                    // 有明确的到账账户（使用净到账金额）
                    BigDecimal remainingAmount = netAmountForCash;
                    for (int i = 0; i < targetLines.size(); i++) {
                        OrderFundingLine targetLine = targetLines.get(i);
                        BigDecimal accountAmount = targetLine.getAmount() != null ? targetLine.getAmount() : remainingAmount;
                        if (i == targetLines.size() - 1) {
                            accountAmount = remainingAmount;
                        } else {
                            remainingAmount = remainingAmount.subtract(accountAmount);
                        }
                        
                        LedgerPosting cashPosting = new LedgerPosting();
                        cashPosting.setPostingType("DEBIT");
                        cashPosting.setAccountId(targetLine.getAccountId());
                        cashPosting.setAccountType("CASH");
                        cashPosting.setAmount(accountAmount);
                        cashPosting.setCurrency(targetLine.getCurrency() != null ? targetLine.getCurrency() : "CNY");
                        postings.add(cashPosting);
                    }
                } else if (!sourceLines.isEmpty()) {
                    // 没有明确的到账账户，按SOURCE账户份额比例分配（兼容旧逻辑，使用净到账金额）
                    BigDecimal remainingAmount = netAmountForCash;
                    for (int i = 0; i < sourceLines.size(); i++) {
                        OrderFundingLine sourceLine = sourceLines.get(i);
                        BigDecimal accountAmount = netAmountForCash.multiply(sourceLine.getShares())
                            .divide(totalShares, 2, RoundingMode.HALF_UP);
                        if (i == sourceLines.size() - 1) {
                            accountAmount = remainingAmount;
                        } else {
                            remainingAmount = remainingAmount.subtract(accountAmount);
                        }
                        
                        LedgerPosting cashPosting = new LedgerPosting();
                        cashPosting.setPostingType("DEBIT");
                        cashPosting.setAccountId(sourceLine.getAccountId());
                        cashPosting.setAccountType("CASH");
                        cashPosting.setAmount(accountAmount);
                        cashPosting.setCurrency(sourceLine.getCurrency() != null ? sourceLine.getCurrency() : "CNY");
                        postings.add(cashPosting);
                    }
                } else if (!fundingLines.isEmpty()) {
                    // 兜底：使用第一个fundingLine（使用净到账金额）
                    LedgerPosting cashPosting = new LedgerPosting();
                    cashPosting.setPostingType("DEBIT");
                    cashPosting.setAccountId(fundingLines.get(0).getAccountId());
                    cashPosting.setAccountType("CASH");
                    cashPosting.setAmount(netAmountForCash);
                    cashPosting.setCurrency(fundingLines.get(0).getCurrency() != null ? fundingLines.get(0).getCurrency() : "CNY");
                    postings.add(cashPosting);
                }

                // 2. POSITION CREDIT：从持仓账户扣除份额
                // 摊薄成本法：CREDIT金额 = 卖出收入（按份额比例分配）
                if (positionAccount != null) {
                    if (!sourceLines.isEmpty() && sourceLines.size() > 1) {
                        // 多账户卖出：按份额比例分配卖出收入
                        for (OrderFundingLine sourceLine : sourceLines) {
                            // 按份额比例计算该账户对应的卖出收入
                            BigDecimal shareRatio = totalShares.compareTo(BigDecimal.ZERO) > 0 
                                ? sourceLine.getShares().divide(totalShares, 6, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO;
                            BigDecimal accountCostDeduction = totalConfirmAmount.multiply(shareRatio)
                                .setScale(2, RoundingMode.HALF_UP);
                            
                            LedgerPosting positionCreditPosting = new LedgerPosting();
                            positionCreditPosting.setPostingType("CREDIT");
                            positionCreditPosting.setAccountId(positionAccount.getId());
                            positionCreditPosting.setAccountType("POSITION");
                            positionCreditPosting.setAmount(accountCostDeduction);
                            positionCreditPosting.setShares(sourceLine.getShares());
                            positionCreditPosting.setCurrency(product.getCurrency() != null ? product.getCurrency() : "CNY");
                            postings.add(positionCreditPosting);
                        }
                    } else {
                        // 单账户卖出：CREDIT金额 = 全部卖出收入
                        LedgerPosting positionCreditPosting = new LedgerPosting();
                        positionCreditPosting.setPostingType("CREDIT");
                        positionCreditPosting.setAccountId(positionAccount.getId());
                        positionCreditPosting.setAccountType("POSITION");
                        positionCreditPosting.setAmount(totalCostDeduction);
                        positionCreditPosting.setShares(totalShares);
                        positionCreditPosting.setCurrency(product.getCurrency() != null ? product.getCurrency() : "CNY");
                        postings.add(positionCreditPosting);
                    }
                }
            }

            // 3. FEE DEBIT（手续费）
            if (confirmFee != null && confirmFee.compareTo(BigDecimal.ZERO) > 0) {
                Account feeAccount = virtualAccountFor(apply, computation, "FEE", "FEE",
                    ownerType, ownerUserId, ownerFamilyId, null, null);
                
                LedgerPosting feePosting = new LedgerPosting();
                feePosting.setPostingType("DEBIT");
                feePosting.setAccountId(feeAccount.getId());
                feePosting.setAccountType("FEE");
                feePosting.setAmount(confirmFee);
                feePosting.setCurrency(product.getCurrency() != null ? product.getCurrency() : "CNY");
                postings.add(feePosting);
            }
        }

        // 获取总份额（用于备注）
        BigDecimal totalSharesForNote = BigDecimal.ZERO;
        if ("SELL".equals(order.getOrderType()) || "REDEMPTION".equals(order.getOrderType())) {
            List<OrderFundingLine> sourceLines = fundingLines.stream()
                .filter(fl -> "SOURCE".equals(fl.getLineType()) || fl.getLineType() == null)
                .filter(fl -> fl.getShares() != null && fl.getShares().compareTo(BigDecimal.ZERO) > 0)
                .collect(java.util.stream.Collectors.toList());
            totalSharesForNote = sourceLines.isEmpty() ? 
                (order.getShares() != null ? order.getShares() : BigDecimal.ZERO) :
                sourceLines.stream().map(OrderFundingLine::getShares).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // 账本备注口径保持与既有实现一致（交易类型 + 产品名称 + 金额/份额）
        String ledgerNote = null;
        String requestedAtStr = null;
        if (!postings.isEmpty()) {
            String noteTypeLabel;
            String amountInfo;
            switch (order.getOrderType()) {
                case "BUY":
                    noteTypeLabel = "买入";
                    BigDecimal buyAmount = fundingLines.stream()
                            .map(OrderFundingLine::getAmount)
                            .filter(java.util.Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    amountInfo = String.format("金额%.2f元，份额%.2f份", buyAmount, confirmShares != null ? confirmShares : BigDecimal.ZERO);
                    break;
                case "SUBSCRIPTION":
                    noteTypeLabel = "申购";
                    BigDecimal subAmount = fundingLines.stream()
                            .map(OrderFundingLine::getAmount)
                            .filter(java.util.Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    amountInfo = String.format("金额%.2f元，份额%.2f份", subAmount, confirmShares != null ? confirmShares : BigDecimal.ZERO);
                    break;
                case "SELL":
                    noteTypeLabel = "卖出";
                    amountInfo = String.format("份额%.2f份，金额%.2f元", totalSharesForNote, confirmAmount != null ? confirmAmount : BigDecimal.ZERO);
                    break;
                case "REDEMPTION":
                    noteTypeLabel = "赎回";
                    amountInfo = String.format("份额%.2f份，金额%.2f元", totalSharesForNote, confirmAmount != null ? confirmAmount : BigDecimal.ZERO);
                    break;
                default:
                    noteTypeLabel = order.getOrderType();
                    amountInfo = "";
            }
            ledgerNote = String.format("订单结算: %s %s %s", noteTypeLabel, product.getProductName(), amountInfo);

            // 使用确认日期作为交易时间（默认11:00），而不是当前时间
            // 格式必须是 yyyy-MM-dd HH:mm:ss
            java.time.LocalDateTime settlementTime = confirmDate.atTime(11, 0, 0);
            requestedAtStr = settlementTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        computation.note = ledgerNote;
        computation.postings = postings;

        // ===== 计算阶段结束：生成 fresh preview 指纹 =====
        // 指纹覆盖订单（含 updated_at 与 status）、资金来源行、输入参数、关键账户快照与分录序列，
        // 任何一项变化都会让旧令牌失效。
        computation.freshPreviewToken = buildFingerprint(computation);

        // ===== 写入阶段：只有 confirm（apply=true）且 fresh preview 令牌匹配时才落账 =====
        // preview（apply=false）到此结束，不产生任何业务写入。
        if (apply) {
            if (freshPreviewToken == null || freshPreviewToken.isBlank()) {
                throw new RuntimeException("缺少 fresh preview 令牌，请先生成最新结算预览并二次确认");
            }
            if (!freshPreviewToken.equals(computation.freshPreviewToken)) {
                throw new RuntimeException("结算预览已失效（订单、资金来源、账户或输入参数已变化），请重新生成结算预览后再确认");
            }
            // 0. 创建 preview 阶段登记的“待创建账户”并回填分录；账户创建严格发生在令牌校验之后，令牌失效时零副作用。
            createPendingAccounts(computation);

            // 1. settlement_confirm 落库（一个订单只允许一条）
            settlementConfirmMapper.insert(settlement);

            // 2. 释放出资行占用的 reserved_amount（只释放有金额的出资行；
            //    SELL / REDEMPTION 的份额占用不涉及金额，不在此处理）
            for (OrderFundingLine fundingLine : fundingLines) {
                if (fundingLine.getAmount() == null || fundingLine.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                Account account = accountMapper.selectById(fundingLine.getAccountId());
                if (account == null) {
                    continue;
                }
                BigDecimal currentReserved = account.getReservedAmount() != null
                        ? account.getReservedAmount() : BigDecimal.ZERO;
                BigDecimal newReservedAmount = currentReserved.subtract(fundingLine.getAmount());
                if (newReservedAmount.compareTo(BigDecimal.ZERO) < 0) {
                    newReservedAmount = BigDecimal.ZERO; // 防止负数
                }
                accountMapper.updateReservedAmount(fundingLine.getAccountId(), newReservedAmount);
            }

            // 3. 关联账户 initial_shares 影响（只在结算时真正写入）
            if (computation.linkedAccountId != null && computation.linkedAccountNewShares != null) {
                accountMapper.updateInitialShares(computation.linkedAccountId, computation.linkedAccountNewShares);
            }

            // 4. 正式账本：整个订单只生成这一套 ledger_txn / ledger_posting
            if (!postings.isEmpty()) {
                ledgerService.createTransaction(
                    order.getUserId(),
                    ownerFamilyId,
                    order.getOrderType(),
                    orderId,
                    postings,
                    ledgerNote,
                    requestedAtStr,
                    null,  // categoryId
                    false, // isReimbursable
                    product.getId(), // productId - 关联到产品
                    orderId  // orderId - 关联订单
                );
            }

            // 5. 订单状态
            order.setStatus("CONFIRMED");
            orderMapper.update(order);
        }

        computation.settlement = settlement;
        finalizeComputation(computation);
        return computation;
    }
    
    // ===================== v0.13.0 结算预览 / 确认共用支撑 =====================

    /** 记录阻断原因并原样返回计算结果（preview 不抛业务异常，confirm 统一转异常）。 */
    private static SettlementComputation block(SettlementComputation computation, String reason) {
        if (reason != null && !reason.isBlank()) {
            computation.blockingReasons.add(reason);
        }
        return computation;
    }

    /** 订单类型中文名。 */
    private static String orderTypeLabelOf(String orderType) {
        if (orderType == null) {
            return "未知类型";
        }
        switch (orderType) {
            case "BUY":
                return "买入";
            case "SUBSCRIPTION":
                return "申购";
            case "SELL":
                return "卖出";
            case "REDEMPTION":
                return "赎回";
            default:
                return orderType;
        }
    }

    /** 账户可见性判断，语义与 OrderService / AccountMapper 保持一致。 */
    private boolean isVisibleToUserOrFamily(Account account, Long userId, Long familyId) {
        if (account == null) {
            return false;
        }
        if (account.getOwnerUserId() != null && account.getOwnerUserId().equals(userId)) {
            return true;
        }
        return familyId != null && account.getOwnerFamilyId() != null && account.getOwnerFamilyId().equals(familyId);
    }

    /** BigDecimal 的稳定文本，null 归一为空串，避免指纹受标度差异影响。 */
    private static String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    /** 卖出 / 赎回的份额来源合计；资金来源行没有份额时回退到订单份额。 */
    private static BigDecimal sellSourceShares(List<OrderFundingLine> fundingLines, Order order) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderFundingLine line : fundingLines) {
            if (line.getLineType() != null && !"SOURCE".equals(line.getLineType())) {
                continue;
            }
            if (line.getShares() == null || line.getShares().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            total = total.add(line.getShares());
        }
        if (total.compareTo(BigDecimal.ZERO) > 0) {
            return total;
        }
        return order.getShares() != null ? order.getShares() : BigDecimal.ZERO;
    }

    /** 券商平台账户校验：与 AccountService.getOrCreateBrokerPositionAccount 的前置校验保持一致。 */
    private boolean isValidBrokerPlatformAccount(Long brokerAccountId) {
        Account brokerAccount = accountMapper.selectById(brokerAccountId);
        return brokerAccount != null
                && "BROKER".equals(brokerAccount.getAccountType())
                && brokerAccount.getParentAccountId() == null;
    }

    /** 虚拟账户 account_code 规则，与 AccountService.getOrCreateVirtualAccount 完全一致。 */
    private static String virtualAccountCode(String virtualSubtype, String ownerType, Long ownerUserId,
                                             Long ownerFamilyId, Long productId) {
        String ownerKey = "PERSONAL".equals(ownerType) ? String.valueOf(ownerUserId) : String.valueOf(ownerFamilyId);
        if ("POSITION".equals(virtualSubtype)) {
            return String.format("VIRTUAL-POSITION-%s-%s-%s", ownerType, ownerKey, String.valueOf(productId));
        }
        return String.format("VIRTUAL-%s-%s-%s", virtualSubtype, ownerType, ownerKey);
    }

    /** 虚拟账户名称规则，与 AccountService.getOrCreateVirtualAccount 完全一致。 */
    private static String virtualAccountName(String virtualSubtype, String productName) {
        if ("POSITION".equals(virtualSubtype) && productName != null) {
            return "持仓账户-" + productName;
        }
        switch (virtualSubtype) {
            case "EXPENSE":
                return "费用账户";
            case "INCOME":
                return "收入账户";
            case "FEE":
                return "手续费账户";
            case "RECEIVABLE":
                return "应收账户";
            case "LIABILITY":
                return "负债账户";
            default:
                return virtualSubtype + "账户";
        }
    }

    /** 占位账户的稳定身份：只用 account_code 派生，保证 preview 与 confirm 首次创建时指纹一致。 */
    private static Long placeholderAccountId(String accountCode) {
        return -((long) Math.abs(accountCode.hashCode()) + 1L);
    }

    /**
     * 获取（或只读解析）虚拟账户。
     *
     * <p>preview 与 confirm 都只读解析：账户尚未创建时返回与创建结果等价的占位账户，并登记“待创建账户”；
     * confirm 只有通过 fresh preview 令牌校验后，才会在写入阶段真正创建账户并回填分录，令牌失效时零副作用。</p>
     */
    private Account virtualAccountFor(boolean apply, SettlementComputation computation, String virtualSubtype,
                                      String accountType, String ownerType, Long ownerUserId, Long ownerFamilyId,
                                      Long productId, String productName) {
        String accountCode = virtualAccountCode(virtualSubtype, ownerType, ownerUserId, ownerFamilyId, productId);
        Account byCode = accountMapper.selectByCode(accountCode);
        if (byCode != null) {
            computation.registerAccount(byCode);
            return byCode;
        }

        String expectedName = virtualAccountName(virtualSubtype, productName);
        List<Account> existing = accountMapper.selectVirtualAccountsByOwner(ownerUserId, ownerFamilyId, virtualSubtype);
        if (existing != null) {
            for (Account candidate : existing) {
                if (candidate == null || !"VIRTUAL".equals(candidate.getAccountKind())) {
                    continue;
                }
                if (!virtualSubtype.equals(candidate.getVirtualSubtype())) {
                    continue;
                }
                if (!ownerType.equals(candidate.getOwnerType())) {
                    continue;
                }
                if ("POSITION".equals(virtualSubtype) && !expectedName.equals(candidate.getAccountName())) {
                    continue;
                }
                computation.registerAccount(candidate);
                return candidate;
            }
        }

        Account placeholder = new Account();
        placeholder.setId(placeholderAccountId(accountCode));
        placeholder.setAccountCode(accountCode);
        placeholder.setAccountName(expectedName);
        placeholder.setAccountKind("VIRTUAL");
        placeholder.setAccountType("OTHER");
        placeholder.setVirtualSubtype(virtualSubtype);
        placeholder.setOwnerType(ownerType);
        placeholder.setOwnerUserId(ownerUserId);
        placeholder.setOwnerFamilyId(ownerFamilyId);
        placeholder.setCurrency("CNY");
        placeholder.setBalance(BigDecimal.ZERO);
        placeholder.setReservedAmount(BigDecimal.ZERO);
        placeholder.setInitialBalance(BigDecimal.ZERO);
        placeholder.setIsActive(true);
        placeholder.setIsFixedAmount(false);
        computation.registerAccount(placeholder);
        computation.warnings.add("账户「" + expectedName + "」尚未创建，结算时会自动创建");
        if (apply) {
            computation.pendingCreations.add(PendingAccountCreation.virtual(placeholder.getId(), virtualSubtype,
                    accountType, ownerType, ownerUserId, ownerFamilyId, productId, productName));
        }
        return placeholder;
    }

    /** 获取（或只读解析）产品持仓账户（无券商维度）。 */
    private Account positionAccountFor(boolean apply, SettlementComputation computation, Long productId,
                                       String productName, String ownerType, Long ownerUserId, Long ownerFamilyId) {
        return virtualAccountFor(apply, computation, "POSITION", "POSITION", ownerType, ownerUserId, ownerFamilyId,
                productId, productName);
    }

    /** 券商维度产品持仓账户 account_code 规则，与 AccountService.getOrCreateBrokerPositionAccount 一致。 */
    private static String brokerPositionAccountCode(Long brokerAccountId, Long productId) {
        return "BROKER-POS-" + brokerAccountId + "-" + productId;
    }

    /**
     * 获取（或只读解析）券商维度产品持仓账户。
     *
     * <p>与虚拟账户一致：preview 阶段只读解析，账户未创建时返回等价占位账户并登记待创建；写入阶段才真正落库。</p>
     */
    private Account brokerPositionAccountFor(boolean apply, SettlementComputation computation, Long brokerAccountId,
                                             Long productId, String productName, String ownerType, Long ownerUserId,
                                             Long ownerFamilyId) {
        String accountCode = brokerPositionAccountCode(brokerAccountId, productId);
        Account byCode = accountMapper.selectByCode(accountCode);
        if (byCode != null) {
            computation.registerAccount(byCode);
            return byCode;
        }

        Account brokerAccount = accountMapper.selectById(brokerAccountId);
        String resolvedProductName = productName != null ? productName : ("产品" + productId);
        Account placeholder = new Account();
        placeholder.setId(placeholderAccountId(accountCode));
        placeholder.setAccountCode(accountCode);
        placeholder.setAccountName((brokerAccount != null ? brokerAccount.getAccountName() : ("券商" + brokerAccountId))
                + "-持仓账户-" + resolvedProductName);
        placeholder.setAccountKind("VIRTUAL");
        placeholder.setAccountType("OTHER");
        placeholder.setVirtualSubtype("POSITION");
        placeholder.setOwnerType(ownerType != null ? ownerType : (brokerAccount != null ? brokerAccount.getOwnerType() : null));
        placeholder.setOwnerUserId(ownerUserId != null ? ownerUserId : (brokerAccount != null ? brokerAccount.getOwnerUserId() : null));
        placeholder.setOwnerFamilyId(ownerFamilyId != null ? ownerFamilyId : (brokerAccount != null ? brokerAccount.getOwnerFamilyId() : null));
        placeholder.setCurrency(brokerAccount != null && brokerAccount.getCurrency() != null
                ? brokerAccount.getCurrency() : "CNY");
        placeholder.setParentAccountId(brokerAccountId);
        placeholder.setLinkedProductId(productId);
        placeholder.setInitialBalance(BigDecimal.ZERO);
        placeholder.setBalance(BigDecimal.ZERO);
        placeholder.setReservedAmount(BigDecimal.ZERO);
        placeholder.setIsActive(true);
        placeholder.setIsFixedAmount(false);
        computation.registerAccount(placeholder);
        computation.warnings.add("券商维度持仓账户「" + placeholder.getAccountName() + "」尚未创建，结算时会自动创建");
        if (apply) {
            computation.pendingCreations.add(PendingAccountCreation.broker(placeholder.getId(), brokerAccountId,
                    productId, resolvedProductName, ownerType, ownerUserId, ownerFamilyId));
        }
        return placeholder;
    }

    /**
     * 生成 fresh preview 指纹。
     *
     * <p>输入只使用稳定身份（account_code / 订单业务单号等），不使用账户自增主键，避免首次创建账户时
     * preview 与 confirm 出现无意义差异；同时覆盖订单 updated_at、状态、资金来源、输入参数与关键账户快照。</p>
     */
    private String buildFingerprint(SettlementComputation computation) {
        StringBuilder sb = new StringBuilder();
        sb.append("v=013\n");
        sb.append("orderId=").append(computation.orderId).append('\n');
        Order order = computation.order;
        if (order != null) {
            sb.append("orderRef=").append(order.getUserId()).append('|').append(order.getProductId()).append('|')
                    .append(order.getOrderType()).append('|').append(order.getStatus()).append('|')
                    .append(plain(order.getAmount())).append('|').append(plain(order.getShares())).append('|')
                    .append(order.getUpdatedAt()).append('\n');
        }
        sb.append("owner=").append(computation.ownerType).append('|').append(computation.ownerUserId).append('|')
                .append(computation.ownerFamilyId).append('\n');
        sb.append("input=").append(computation.confirmDate).append('|').append(computation.navDate).append('|')
                .append(plain(computation.confirmNav)).append('|').append(plain(computation.requestedShares))
                .append('|').append(plain(computation.requestedAmount)).append('|')
                .append(plain(computation.requestedFee)).append('\n');
        for (OrderFundingLine line : computation.sortedFundingLines()) {
            sb.append("funding=").append(line.getLineNo()).append('|').append(line.getAccountId()).append('|')
                    .append(line.getLineType()).append('|').append(plain(line.getAmount())).append('|')
                    .append(plain(line.getShares())).append('|').append(line.getCurrency()).append('\n');
        }
        for (AccountSnapshot snapshot : computation.sortedSnapshots()) {
            sb.append("account=").append(snapshot.key).append('|').append(snapshot.accountType).append('|')
                    .append(snapshot.virtualSubtype).append('|').append(snapshot.currency).append('|')
                    .append(snapshot.balance).append('|').append(snapshot.reservedAmount).append('|')
                    .append(snapshot.initialShares).append('|').append(snapshot.fixed).append('\n');
        }
        sb.append("fee=").append(plain(computation.confirmFee)).append('\n');
        sb.append("computed=").append(plain(computation.computedShares)).append('|')
                .append(plain(computation.computedAmount)).append('\n');
        for (LedgerPosting posting : computation.postings) {
            sb.append("posting=").append(posting.getAccountType()).append('|').append(posting.getPostingType())
                    .append('|').append(plain(posting.getAmount())).append('|').append(plain(posting.getShares()))
                    .append('|').append(posting.getCurrency()).append('\n');
        }
        return sha256Hex(sb.toString());
    }

    /** SHA-256 十六进制摘要。 */
    private static String sha256Hex(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法生成结算预览指纹", e);
        }
    }

    /** 汇总计算结果：真实影响标记与中文预览文案。 */
    private void finalizeComputation(SettlementComputation computation) {
        computation.willChangeCash = false;
        computation.willChangeHolding = false;
        computation.postingsPreview.clear();
        computation.summaryLines.clear();

        for (LedgerPosting posting : computation.postings) {
            if ("CASH".equals(posting.getAccountType())) {
                computation.willChangeCash = true;
            }
            if ("POSITION".equals(posting.getAccountType())) {
                computation.willChangeHolding = true;
            }
            String accountName = resolveAccountName(computation, posting);
            SettlementPostingPreviewDTO row = new SettlementPostingPreviewDTO();
            row.setAccountId(posting.getAccountId());
            row.setAccountName(accountName);
            row.setAccountType(posting.getAccountType());
            row.setPostingType(posting.getPostingType());
            row.setAmount(posting.getAmount());
            row.setShares(posting.getShares());
            row.setCurrency(posting.getCurrency());
            row.setDescription(postingDescription(posting, accountName));
            computation.postingsPreview.add(row);
            computation.summaryLines.add(row.getDescription());
        }
        if (computation.linkedAccountId != null && computation.linkedAccountNewShares != null) {
            computation.willChangeHolding = true;
        }
        if (!computation.blockingReasons.isEmpty()) {
            computation.willChangeCash = false;
            computation.willChangeHolding = false;
            computation.willCreateLedgerTxn = false;
        } else {
            computation.willCreateLedgerTxn = !computation.postings.isEmpty();
        }

        List<String> lines = new ArrayList<>();
        if (computation.order != null && computation.blockingReasons.isEmpty()) {
            lines.add(String.format("%s %s：确认净值 %s（净值日期 %s / 确认日期 %s）",
                    computation.orderTypeLabel,
                    computation.product != null ? computation.product.getProductName() : "未知产品",
                    plain(computation.confirmNav), computation.navDate, computation.confirmDate));
            if ("BUY".equals(computation.orderType) || "SUBSCRIPTION".equals(computation.orderType)) {
                lines.add("下单付款流水已存在，本次结算不会再扣同一笔下单现金；结算效果为清理待结算应收、形成持仓 / 关联账户与手续费");
            } else {
                lines.add("本次结算才真正产生现金、持仓与手续费影响，结算完成后该订单的 PENDING 占用失效");
            }
            lines.add(String.format("手续费：%s 元", plain(computation.confirmFee)));
        }
        lines.addAll(computation.summaryLines);
        for (String warning : computation.warnings) {
            lines.add("提示：" + warning);
        }
        computation.summaryLines = lines;
    }

    /** 找到分录对应的账户名称：优先使用本次计算已解析的账户，缺失时只读回查。 */
    private String resolveAccountName(SettlementComputation computation, LedgerPosting posting) {
        Long accountId = posting.getAccountId();
        if (accountId != null) {
            String registered = computation.accountNames.get(accountId);
            if (registered != null && !registered.isBlank()) {
                return registered;
            }
            Account account = accountMapper.selectById(accountId);
            if (account != null && account.getAccountName() != null && !account.getAccountName().isBlank()) {
                return account.getAccountName();
            }
        }
        return defaultAccountName(posting.getAccountType());
    }

    /** 账户尚未创建且无法回查时的中文兜底名称。 */
    private static String defaultAccountName(String accountType) {
        if ("RECEIVABLE".equals(accountType)) {
            return "应收账户";
        }
        if ("FEE".equals(accountType)) {
            return "手续费账户";
        }
        if ("POSITION".equals(accountType)) {
            return "持仓账户";
        }
        if ("CASH".equals(accountType)) {
            return "现金账户";
        }
        return "账户";
    }

    /** 单条分录的中文说明。 */
    private static String postingDescription(LedgerPosting posting, String accountName) {
        String name = accountName != null && !accountName.isBlank() ? accountName : "账户";
        String amount = plain(posting.getAmount());
        String unit = posting.getCurrency() == null || "CNY".equals(posting.getCurrency())
                ? "元" : posting.getCurrency();
        boolean debit = "DEBIT".equals(posting.getPostingType());
        String sign = debit ? "+" : "-";
        switch (posting.getAccountType() == null ? "" : posting.getAccountType()) {
            case "POSITION":
                return debit
                        ? String.format("%s：份额 +%s（成本 %s %s）", name, plain(posting.getShares()), amount, unit)
                        : String.format("%s：份额 -%s（按摊薄成本法减少成本 %s %s）",
                                name, plain(posting.getShares()), amount, unit);
            case "CASH":
                return String.format("%s：现金 %s%s %s", name, sign, amount, unit);
            case "RECEIVABLE":
                return String.format("%s：待结算应收 %s%s %s", name, sign, amount, unit);
            case "FEE":
                return String.format("%s：手续费 %s %s", name, amount, unit);
            default:
                return String.format("%s：%s %s%s %s", name, posting.getAccountType(), sign, amount, unit);
        }
    }

    /** 计算结果 -> 客户端预览 DTO。 */
    private SettlementPreviewDTO toPreviewDTO(SettlementComputation computation) {
        SettlementPreviewDTO dto = new SettlementPreviewDTO();
        dto.setOrderId(computation.orderId);
        dto.setOrderType(computation.orderType);
        dto.setOrderTypeLabel(computation.orderTypeLabel);
        dto.setOrderStatus(computation.orderStatus);
        dto.setProductId(computation.product != null ? computation.product.getId()
                : (computation.order != null ? computation.order.getProductId() : null));
        dto.setProductName(computation.product != null ? computation.product.getProductName() : null);
        dto.setProductCode(computation.product != null ? computation.product.getProductCode() : null);
        dto.setCurrency(computation.currency);
        dto.setConfirmDate(computation.confirmDate);
        dto.setNavDate(computation.navDate);
        dto.setConfirmNav(computation.confirmNav);
        dto.setConfirmShares(computation.confirmShares);
        dto.setConfirmAmount(computation.confirmAmount);
        dto.setConfirmFee(computation.confirmFee);
        dto.setComputedShares(computation.computedShares);
        dto.setComputedAmount(computation.computedAmount);
        dto.setTotalFundingAmount(computation.totalFundingAmount);
        dto.setWarnings(new ArrayList<>(computation.warnings));
        dto.setBlockingReasons(new ArrayList<>(computation.blockingReasons));
        dto.setPostingsPreview(new ArrayList<>(computation.postingsPreview));
        dto.setSummaryLines(new ArrayList<>(computation.summaryLines));
        boolean supported = computation.blockingReasons.isEmpty();
        dto.setConfirmSupported(supported);
        dto.setWillCreateSettlementConfirm(supported);
        dto.setWillCreateLedgerTxn(supported && !computation.postings.isEmpty());
        dto.setWillChangeHolding(computation.willChangeHolding);
        dto.setWillChangeCash(computation.willChangeCash);
        dto.setFreshPreviewToken(computation.freshPreviewToken);
        dto.setPreviewFingerprint(computation.freshPreviewToken);
        return dto;
    }

    /** 账户指纹快照：只保留金额 / 份额 / 固定金额等关键口径。 */
    private static final class AccountSnapshot {
        String key;
        String accountType;
        String virtualSubtype;
        String currency;
        String balance;
        String reservedAmount;
        String initialShares;
        String fixed;
    }

    /** 结算计算结果：preview 与 confirm 共用。 */
    private static final class SettlementComputation {
        String orderId;
        Order order;
        ProductMaster product;
        List<OrderFundingLine> fundingLines = new ArrayList<>();
        List<LedgerPosting> postings = new ArrayList<>();
        String orderType;
        String orderTypeLabel;
        String orderStatus;
        String currency;
        LocalDate confirmDate;
        LocalDate navDate;
        BigDecimal confirmNav;
        BigDecimal requestedShares;
        BigDecimal requestedAmount;
        BigDecimal requestedFee;
        BigDecimal confirmShares;
        BigDecimal confirmAmount;
        BigDecimal confirmFee;
        BigDecimal computedShares;
        BigDecimal computedAmount;
        BigDecimal totalFundingAmount;
        String ownerType;
        Long ownerUserId;
        Long ownerFamilyId;
        boolean willChangeCash;
        boolean willChangeHolding;
        boolean willCreateLedgerTxn;
        Long linkedAccountId;
        BigDecimal linkedAccountNewShares;
        String note;
        String freshPreviewToken;
        SettlementConfirm settlement;
        final List<String> warnings = new ArrayList<>();
        final List<String> blockingReasons = new ArrayList<>();
        final List<SettlementPostingPreviewDTO> postingsPreview = new ArrayList<>();
        List<String> summaryLines = new ArrayList<>();
        final Map<Long, String> accountNames = new HashMap<>();
        final Map<String, AccountSnapshot> snapshots = new LinkedHashMap<>();
        final List<PendingAccountCreation> pendingCreations = new ArrayList<>();

        /** 记录账户快照（按稳定身份去重，后写覆盖先写）。 */
        void snapshot(Account account) {
            if (account == null) {
                return;
            }
            AccountSnapshot snapshot = new AccountSnapshot();
            snapshot.key = accountKey(account);
            snapshot.accountType = account.getAccountType();
            snapshot.virtualSubtype = account.getVirtualSubtype();
            snapshot.currency = account.getCurrency();
            snapshot.balance = plain(account.getBalance());
            snapshot.reservedAmount = plain(account.getReservedAmount());
            snapshot.initialShares = plain(account.getInitialShares());
            snapshot.fixed = (Boolean.TRUE.equals(account.getIsFixedAmount()) ? "T" : "F")
                    + "|" + plain(account.getFixedAmount());
            snapshots.put(snapshot.key, snapshot);
        }

        /** 同时登记快照与展示名称，供 postingsPreview 使用。 */
        void registerAccount(Account account) {
            if (account == null) {
                return;
            }
            snapshot(account);
            if (account.getId() != null) {
                accountNames.put(account.getId(), account.getAccountName());
            }
        }

        List<OrderFundingLine> sortedFundingLines() {
            List<OrderFundingLine> sorted = new ArrayList<>(fundingLines);
            sorted.sort(Comparator
                    .comparing((OrderFundingLine line) -> line.getLineNo() == null ? Integer.MAX_VALUE : line.getLineNo())
                    .thenComparing(line -> line.getAccountId() == null ? Long.MAX_VALUE : line.getAccountId()));
            return sorted;
        }

        List<AccountSnapshot> sortedSnapshots() {
            List<AccountSnapshot> sorted = new ArrayList<>(snapshots.values());
            sorted.sort(Comparator.comparing(snapshot -> snapshot.key));
            return sorted;
        }
    }

    /** preview 阶段解析出的“待创建虚拟账户”，写入阶段（令牌校验通过后）才真正落库。 */
    private static final class PendingAccountCreation {
        final Long placeholderId;
        final String virtualSubtype;
        final String accountType;
        final Long brokerAccountId;
        final Long productId;
        final String productName;
        final String ownerType;
        final Long ownerUserId;
        final Long ownerFamilyId;

        private PendingAccountCreation(Long placeholderId, String virtualSubtype, String accountType,
                                      Long brokerAccountId, Long productId, String productName, String ownerType,
                                      Long ownerUserId, Long ownerFamilyId) {
            this.placeholderId = placeholderId;
            this.virtualSubtype = virtualSubtype;
            this.accountType = accountType;
            this.brokerAccountId = brokerAccountId;
            this.productId = productId;
            this.productName = productName;
            this.ownerType = ownerType;
            this.ownerUserId = ownerUserId;
            this.ownerFamilyId = ownerFamilyId;
        }

        /** 普通虚拟账户（RECEIVABLE / FEE / POSITION）。 */
        static PendingAccountCreation virtual(Long placeholderId, String virtualSubtype, String accountType,
                                              String ownerType, Long ownerUserId, Long ownerFamilyId,
                                              Long productId, String productName) {
            return new PendingAccountCreation(placeholderId, virtualSubtype, accountType, null, productId, productName,
                    ownerType, ownerUserId, ownerFamilyId);
        }

        /** 券商维度产品持仓账户。 */
        static PendingAccountCreation broker(Long placeholderId, Long brokerAccountId, Long productId,
                                             String productName, String ownerType, Long ownerUserId,
                                             Long ownerFamilyId) {
            return new PendingAccountCreation(placeholderId, "POSITION", "POSITION", brokerAccountId, productId,
                    productName, ownerType, ownerUserId, ownerFamilyId);
        }
    }

    /** 写入阶段创建 preview 阶段登记的“待创建账户”，并把分录里的占位账户 ID 回填为真实账户 ID。 */
    private void createPendingAccounts(SettlementComputation computation) {
        for (PendingAccountCreation pending : computation.pendingCreations) {
            Account created = pending.brokerAccountId != null
                    ? accountService.getOrCreateBrokerPositionAccount(pending.brokerAccountId, pending.productId,
                        pending.productName, pending.ownerType, pending.ownerUserId, pending.ownerFamilyId)
                    : accountService.getOrCreateVirtualAccount(pending.virtualSubtype, pending.accountType,
                        pending.ownerType, pending.ownerUserId, pending.ownerFamilyId, pending.productId,
                        pending.productName);
            if (created == null || created.getId() == null) {
                continue;
            }
            for (LedgerPosting posting : computation.postings) {
                if (pending.placeholderId.equals(posting.getAccountId())) {
                    posting.setAccountId(created.getId());
                }
            }
            computation.accountNames.put(created.getId(), created.getAccountName());
        }
    }

    /** 账户稳定身份：优先 account_code，避免依赖自增主键。 */
    private static String accountKey(Account account) {
        if (account.getAccountCode() != null && !account.getAccountCode().isBlank()) {
            return account.getAccountCode();
        }
        if (account.getId() != null) {
            return "#" + account.getId();
        }
        return "?";
    }
    /**
     * 计算固定金额账户的总固定金额
     */
    private BigDecimal getTotalFixedAmount(List<OrderFundingLine> sourceLines) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderFundingLine sourceLine : sourceLines) {
            Account sourceAccount = accountMapper.selectById(sourceLine.getAccountId());
            if (sourceAccount != null && Boolean.TRUE.equals(sourceAccount.getIsFixedAmount()) 
                && sourceAccount.getFixedAmount() != null) {
                total = total.add(sourceAccount.getFixedAmount());
            }
        }
        return total.compareTo(BigDecimal.ZERO) > 0 ? total : BigDecimal.ONE; // 避免除以零
    }
}

