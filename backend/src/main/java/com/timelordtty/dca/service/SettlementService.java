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
import com.timelordtty.dca.model.SettlementConfirm;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 结算确认服务（SettlementService）
 * 
 * 职责：处理订单的结算确认流程，将订单状态从 PENDING 更新为 CONFIRMED，并生成对应的结算记录与会计分录
 * 
 * 实现要点：
 * - 创建 SettlementConfirm 作为订单到记账的桥梁
 * - 释放订单占用的 reserved_amount（支持组合支付，从order_funding_line读取）
 * - 根据订单类型生成对应的 ledger_txn/ledger_posting（调用 LedgerService）
 * - 支持组合支付：按order_funding_line生成多条CASH CREDIT分录
 * 
 * 结算确认流程（支持组合支付）：
 * 1. 校验订单状态为PENDING
 * 2. 创建settlement_confirm记录
 * 3. 查询order_funding_line，获取所有资金来源行
 * 4. 逐条释放各账户的reserved_amount
 * 5. 生成真实分录（调用LedgerService.createTransaction）：
 *    - 买入/申购：POSITION DEBIT + 多条CASH CREDIT（按funding_line拆分）+ FEE DEBIT
 *    - 卖出/赎回：CASH DEBIT + POSITION CREDIT + FEE DEBIT
 * 6. 更新订单状态为CONFIRMED
 * 
 * 买入/申购结算分录模板：
 * - POSITION账户：DEBIT shares=confirmShares, amount=cost（成本，不含fee）
 * - CASH账户：CREDIT confirmAmount（按funding_line拆分，每个资金来源账户一条）
 * - FEE/EXPENSE账户：DEBIT fee（手续费单独分录）
 * 
 * 卖出/赎回结算分录模板：
 * - CASH账户：DEBIT confirmAmount（实际到账净额）
 * - POSITION账户：CREDIT shares=soldShares, amount=costDeduction（卖出部分成本扣减，按平均成本法计算）
 * - FEE/EXPENSE账户：DEBIT fee（手续费）
 * 
 * 关键业务规则：
 * 1. 结算确认时先释放reserved_amount，再生成真实分录
 * 2. 买入时POSITION的amount是成本（不含fee），fee单独一条分录
 * 3. 卖出时POSITION的amount是成本扣减（按平均成本法计算）
 * 4. 支持手动覆盖（overrideFlag=true），允许用户修改confirmNav、confirmShares等
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@Service
public class SettlementService {

    private final OrderMapper orderMapper;
    private final SettlementConfirmMapper settlementConfirmMapper;
    private final AccountMapper accountMapper;
    private final LedgerService ledgerService;
    private final OrderFundingLineMapper orderFundingLineMapper;
    private final UserService userService;
    private final AccountService accountService;
    private final ProductMasterMapper productMasterMapper;
    private final BrokerFeeService brokerFeeService;
    private final HoldingService holdingService;

    public SettlementService(OrderMapper orderMapper, SettlementConfirmMapper settlementConfirmMapper,
                            AccountMapper accountMapper, LedgerService ledgerService,
                            OrderFundingLineMapper orderFundingLineMapper, UserService userService,
                            @Lazy AccountService accountService, ProductMasterMapper productMasterMapper,
                            BrokerFeeService brokerFeeService, HoldingService holdingService) {
        this.orderMapper = orderMapper;
        this.settlementConfirmMapper = settlementConfirmMapper;
        this.accountMapper = accountMapper;
        this.ledgerService = ledgerService;
        this.orderFundingLineMapper = orderFundingLineMapper;
        this.userService = userService;
        this.accountService = accountService;
        this.productMasterMapper = productMasterMapper;
        this.brokerFeeService = brokerFeeService;
        this.holdingService = holdingService;
    }

    /**
     * 对订单执行结算确认：插入 SettlementConfirm、释放占用、并触发记账创建真实分录
     * 
     * 流程说明：
     * 1. 校验订单状态为PENDING
     * 2. 创建settlement_confirm记录
     * 3. 查询order_funding_line，获取所有资金来源行
     * 4. 逐条释放各账户的reserved_amount（先释放，避免可用资金瞬间不一致）
     * 5. 生成真实分录（调用LedgerService.createTransaction）：
     *    - 买入/申购：POSITION DEBIT + 多条CASH CREDIT（按funding_line拆分）+ FEE DEBIT
     *    - 卖出/赎回：CASH DEBIT + POSITION CREDIT + FEE DEBIT
     * 6. 更新订单状态为CONFIRMED
     * 
     * 分录生成规则：
     * - 买入/申购：POSITION的amount是成本（不含fee），fee单独一条分录
     * - 卖出/赎回：POSITION的amount是成本扣减（按平均成本法计算）
     * - 组合支付：按order_funding_line生成多条CASH CREDIT分录
     * 
     * 参数说明：
     * - orderId: 待确认的订单ID
     * - confirmDate: 实际确认日期（实际到账的日期，可人工覆盖）
     * - navDate: 实际使用的净值日期（实际计算份额时用的净值日期，可人工覆盖）
     * - confirmNav: 实际确认净值（实际使用的净值，可人工覆盖）
     * - confirmShares: 实际确认份额（买入/申购时使用）
     * - confirmAmount: 实际确认金额（卖出/赎回时使用）
     * - confirmFee: 实际手续费（实际支付的手续费）
     * 
     * 返回：创建的 SettlementConfirm 对象
     */
    @Transactional
    public SettlementConfirm confirmSettlement(String orderId, LocalDate confirmDate, LocalDate navDate,
                                               BigDecimal confirmNav, BigDecimal confirmShares, 
                                               BigDecimal confirmAmount, BigDecimal confirmFee) {
        Order order = orderMapper.selectByOrderId(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在: " + orderId);
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new RuntimeException("订单状态不是PENDING，当前状态: " + order.getStatus());
        }

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
        if (fundingLines.isEmpty()) {
            throw new RuntimeException("订单没有资金来源记录: " + orderId);
        }

        // 获取产品信息（用于费率计算）
        ProductMaster product = productMasterMapper.selectById(order.getProductId());
        if (product == null) {
            throw new RuntimeException("产品不存在: " + order.getProductId());
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

        // 对于买入/申购订单，验证并重新计算份额（确保份额 = (总金额 - 手续费) / 净值）
        if (("BUY".equals(order.getOrderType()) || "SUBSCRIPTION".equals(order.getOrderType())) 
            && confirmNav != null && confirmNav.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalAmount = fundingLines.stream()
                    .map(OrderFundingLine::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal netAmount = totalAmount.subtract(settlement.getConfirmFee());
            BigDecimal calculatedShares = netAmount.divide(confirmNav, 6, RoundingMode.HALF_UP);
            
            // 如果前端传入的份额与计算值差异较大（超过0.01），使用计算值
            if (confirmShares == null || 
                confirmShares.subtract(calculatedShares).abs().compareTo(new BigDecimal("0.01")) > 0) {
                confirmShares = calculatedShares;
                settlement.setConfirmShares(confirmShares);
            }
        }

        settlement.setIsManualOverride(false);
        settlement.setConfirmedAt(LocalDateTime.now());

        settlementConfirmMapper.insert(settlement);

        // 逐条释放各账户的reserved_amount（先释放，避免可用资金瞬间不一致）
        for (OrderFundingLine fundingLine : fundingLines) {
            Account account = accountMapper.selectById(fundingLine.getAccountId());
            if (account != null) {
                BigDecimal newReservedAmount = account.getReservedAmount().subtract(fundingLine.getAmount());
                if (newReservedAmount.compareTo(BigDecimal.ZERO) < 0) {
                    newReservedAmount = BigDecimal.ZERO; // 防止负数
                }
                accountMapper.updateReservedAmount(fundingLine.getAccountId(), newReservedAmount);
            }
        }

        // 生成真实分录（调用LedgerService.createTransaction）
        // 根据订单类型生成不同的分录
        List<LedgerPosting> postings = new ArrayList<>();

        // 获取用户信息（用于确定账户归属）
        com.timelordtty.dca.dto.AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        String ownerType = currentUser.getFamilyId() != null ? "FAMILY" : "PERSONAL";
        Long ownerUserId = currentUser.getId();
        Long ownerFamilyId = currentUser.getFamilyId();

        if ("BUY".equals(order.getOrderType()) || "SUBSCRIPTION".equals(order.getOrderType())) {
            // 买入/申购：POSITION DEBIT + 多条CASH CREDIT（按funding_line拆分）+ FEE DEBIT
            
            // 计算成本：使用份额 × 净值（而不是金额 - 手续费）
            // 因为份额已经考虑了手续费，所以成本 = 份额 × 净值
            BigDecimal cost;
            if (confirmShares != null && confirmNav != null && confirmNav.compareTo(BigDecimal.ZERO) > 0) {
                // 使用份额 × 净值计算成本（更准确）
                cost = confirmShares.multiply(confirmNav).setScale(2, RoundingMode.HALF_UP);
            } else {
                // 如果没有份额和净值，使用金额 - 手续费（兜底逻辑）
            BigDecimal totalAmount = fundingLines.stream()
                    .map(OrderFundingLine::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                cost = totalAmount.subtract(confirmFee != null ? confirmFee : BigDecimal.ZERO);
            }

            // 检查产品是否有关联账户
            Account linkedAccount = accountMapper.selectByLinkedProductId(order.getProductId());
            boolean hasLinkedAccount = linkedAccount != null;

            // 获取或创建POSITION账户（非关联账户产品才需要）
            Account positionAccount = null;
            if (!hasLinkedAccount) {
                positionAccount = accountService.getOrCreatePositionAccount(
                    order.getProductId(), product.getProductName(), ownerType, ownerUserId, ownerFamilyId);

                // POSITION账户：DEBIT（持仓增加）
                LedgerPosting positionPosting = new LedgerPosting();
                positionPosting.setPostingType("DEBIT");
                positionPosting.setAccountId(positionAccount.getId());
                positionPosting.setAccountType("POSITION");
                positionPosting.setAmount(cost); // 成本，不含fee
                positionPosting.setShares(confirmShares != null ? confirmShares : BigDecimal.ZERO);
                positionPosting.setCurrency(product.getCurrency() != null ? product.getCurrency() : "CNY");
                postings.add(positionPosting);
            } else {
                // 有关联账户的产品，更新 initial_shares（增加购买的份额）
                BigDecimal currentShares = linkedAccount.getInitialShares();
                if (currentShares == null) {
                    currentShares = BigDecimal.ZERO;
                }
                BigDecimal newShares = currentShares.add(confirmShares != null ? confirmShares : BigDecimal.ZERO);
                accountMapper.updateInitialShares(linkedAccount.getId(), newShares);
            }

            // 按funding_line生成多条CASH CREDIT分录（现金减少）
            for (OrderFundingLine fundingLine : fundingLines) {
                LedgerPosting cashPosting = new LedgerPosting();
                cashPosting.setPostingType("CREDIT");
                cashPosting.setAccountId(fundingLine.getAccountId());
                cashPosting.setAccountType("CASH");
                cashPosting.setAmount(fundingLine.getAmount());
                cashPosting.setCurrency(fundingLine.getCurrency());
                postings.add(cashPosting);
            }

            // 如果购买关联账户产品，还需要为关联账户的子账户（fundingLines中的TARGET类型）增加余额
            // 分离 SOURCE 和 TARGET 类型的 fundingLines
            List<OrderFundingLine> sourceLines = fundingLines.stream()
                .filter(fl -> "SOURCE".equals(fl.getLineType()) || fl.getLineType() == null)
                .collect(java.util.stream.Collectors.toList());
            List<OrderFundingLine> targetLines = fundingLines.stream()
                .filter(fl -> "TARGET".equals(fl.getLineType()))
                .collect(java.util.stream.Collectors.toList());

            // 如果有 TARGET 类型的 fundingLines，说明买入后需要入金到这些账户
            if (hasLinkedAccount && !targetLines.isEmpty()) {
                // 为 TARGET 账户生成 CASH DEBIT 分录（余额增加）
                for (OrderFundingLine targetLine : targetLines) {
                    LedgerPosting cashDebitPosting = new LedgerPosting();
                    cashDebitPosting.setPostingType("DEBIT");
                    cashDebitPosting.setAccountId(targetLine.getAccountId());
                    cashDebitPosting.setAccountType("CASH");
                    cashDebitPosting.setAmount(targetLine.getAmount() != null ? targetLine.getAmount() : BigDecimal.ZERO);
                    cashDebitPosting.setCurrency(targetLine.getCurrency() != null ? targetLine.getCurrency() : "CNY");
                    postings.add(cashDebitPosting);
                }
            }

            // 手续费分录（如果有）
            if (confirmFee != null && confirmFee.compareTo(BigDecimal.ZERO) > 0) {
                // 获取或创建FEE账户
                Account feeAccount = accountService.getOrCreateVirtualAccount(
                    "FEE", "FEE", ownerType, ownerUserId, ownerFamilyId, null, null);
                
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
            boolean hasLinkedAccount = linkedAccount != null;
            
            // 获取持仓账户（非关联账户产品才需要）
            Account positionAccount = null;
            if (!hasLinkedAccount) {
                positionAccount = accountService.getOrCreatePositionAccount(
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
            if (hasLinkedAccount && !sourceLines.isEmpty()) {
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
                BigDecimal currentShares = linkedAccount.getInitialShares();
                if (currentShares != null) {
                    BigDecimal newShares = currentShares.subtract(totalShares);
                    if (newShares.compareTo(BigDecimal.ZERO) < 0) {
                        newShares = BigDecimal.ZERO;
                    }
                    accountMapper.updateInitialShares(linkedAccount.getId(), newShares);
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
                Account feeAccount = accountService.getOrCreateVirtualAccount(
                    "FEE", "FEE", ownerType, ownerUserId, ownerFamilyId, null, null);
                
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

        // 生成真实分录（调用LedgerService.createTransaction）
        if (!postings.isEmpty()) {
            // 自动生成备注：交易类型+产品名称+金额/份额
            String orderTypeLabel;
            String amountInfo;
            switch (order.getOrderType()) {
                case "BUY": 
                    orderTypeLabel = "买入"; 
                    // 买入：显示金额和份额
                    BigDecimal buyAmount = fundingLines.stream()
                            .map(OrderFundingLine::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    amountInfo = String.format("金额%.2f元，份额%.2f份", buyAmount, confirmShares != null ? confirmShares : BigDecimal.ZERO); 
                    break;
                case "SUBSCRIPTION": 
                    orderTypeLabel = "申购"; 
                    // 申购：显示金额和份额
                    BigDecimal subAmount = fundingLines.stream()
                            .map(OrderFundingLine::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    amountInfo = String.format("金额%.2f元，份额%.2f份", subAmount, confirmShares != null ? confirmShares : BigDecimal.ZERO); 
                    break;
                case "SELL": 
                    orderTypeLabel = "卖出"; 
                    amountInfo = String.format("份额%.2f份，金额%.2f元", totalSharesForNote, confirmAmount != null ? confirmAmount : BigDecimal.ZERO); 
                    break;
                case "REDEMPTION": 
                    orderTypeLabel = "赎回"; 
                    amountInfo = String.format("份额%.2f份，金额%.2f元", totalSharesForNote, confirmAmount != null ? confirmAmount : BigDecimal.ZERO); 
                    break;
                default: 
                    orderTypeLabel = order.getOrderType(); 
                    amountInfo = "";
            }
            String autoNote = String.format("订单结算: %s %s %s", orderTypeLabel, product.getProductName(), amountInfo);
            
            // 使用确认日期作为交易时间（默认11:00），而不是当前时间
            // 格式必须是 yyyy-MM-dd HH:mm:ss
            java.time.LocalDateTime settlementTime = confirmDate.atTime(11, 0, 0);
            String requestedAtStr = settlementTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            ledgerService.createTransaction(
                order.getUserId(), 
                ownerFamilyId, 
                order.getOrderType(), 
                orderId, 
                postings, 
                autoNote,
                requestedAtStr,
                null,  // categoryId
                false, // isReimbursable
                product.getId() // productId - 关联到产品，用于产品交易记录查询
            );
        }
        
        // 更新订单状态
        order.setStatus("CONFIRMED");
        orderMapper.update(order);

        return settlement;
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

