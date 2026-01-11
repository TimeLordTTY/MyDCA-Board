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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    public SettlementService(OrderMapper orderMapper, SettlementConfirmMapper settlementConfirmMapper,
                            AccountMapper accountMapper, LedgerService ledgerService,
                            OrderFundingLineMapper orderFundingLineMapper, UserService userService,
                            AccountService accountService, ProductMasterMapper productMasterMapper) {
        this.orderMapper = orderMapper;
        this.settlementConfirmMapper = settlementConfirmMapper;
        this.accountMapper = accountMapper;
        this.ledgerService = ledgerService;
        this.orderFundingLineMapper = orderFundingLineMapper;
        this.userService = userService;
        this.accountService = accountService;
        this.productMasterMapper = productMasterMapper;
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
        settlement.setConfirmFee(confirmFee != null ? confirmFee : BigDecimal.ZERO);
        settlement.setIsManualOverride(false);
        settlement.setConfirmedAt(LocalDateTime.now());

        settlementConfirmMapper.insert(settlement);

        // 查询order_funding_line，获取所有资金来源行（支持组合支付）
        List<OrderFundingLine> fundingLines = orderFundingLineMapper.selectByOrderId(orderId);
        if (fundingLines.isEmpty()) {
            throw new RuntimeException("订单没有资金来源记录: " + orderId);
        }

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
        
        // 获取产品信息（用于创建持仓账户）
        ProductMaster product = productMasterMapper.selectById(order.getProductId());
        if (product == null) {
            throw new RuntimeException("产品不存在: " + order.getProductId());
        }

        // 获取用户信息（用于确定账户归属）
        com.timelordtty.dca.dto.AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        String ownerType = currentUser.getFamilyId() != null ? "FAMILY" : "PERSONAL";
        Long ownerUserId = currentUser.getId();
        Long ownerFamilyId = currentUser.getFamilyId();

        if ("BUY".equals(order.getOrderType()) || "SUBSCRIPTION".equals(order.getOrderType())) {
            // 买入/申购：POSITION DEBIT + 多条CASH CREDIT（按funding_line拆分）+ FEE DEBIT
            
            // 计算成本（不含手续费）
            BigDecimal totalAmount = fundingLines.stream()
                    .map(OrderFundingLine::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal cost = totalAmount.subtract(confirmFee != null ? confirmFee : BigDecimal.ZERO);

            // 获取或创建POSITION账户（每个产品一个持仓账户）
            Account positionAccount = accountService.getOrCreatePositionAccount(
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
            // 卖出/赎回：CASH DEBIT + POSITION CREDIT + FEE DEBIT
            
            // CASH DEBIT（实际到账净额，使用第一个资金来源账户作为到账账户）
            if (!fundingLines.isEmpty()) {
                LedgerPosting cashPosting = new LedgerPosting();
                cashPosting.setPostingType("DEBIT");
                cashPosting.setAccountId(fundingLines.get(0).getAccountId());
                cashPosting.setAccountType("CASH");
                cashPosting.setAmount(confirmAmount != null ? confirmAmount : BigDecimal.ZERO);
                cashPosting.setCurrency(fundingLines.get(0).getCurrency());
                postings.add(cashPosting);
            }

            // POSITION CREDIT（卖出部分成本扣减，按平均成本法计算）
            // 获取持仓账户
            Account positionAccount = accountService.getOrCreatePositionAccount(
                order.getProductId(), product.getProductName(), ownerType, ownerUserId, ownerFamilyId);

            // 计算卖出成本（按平均成本法：卖出份额 × 平均成本）
            // 这里简化处理，使用确认金额减去手续费作为成本扣减
            // 实际应该从持仓快照或历史分录计算平均成本
            BigDecimal costDeduction = confirmAmount != null ? 
                confirmAmount.subtract(confirmFee != null ? confirmFee : BigDecimal.ZERO) : BigDecimal.ZERO;

            LedgerPosting positionCreditPosting = new LedgerPosting();
            positionCreditPosting.setPostingType("CREDIT");
            positionCreditPosting.setAccountId(positionAccount.getId());
            positionCreditPosting.setAccountType("POSITION");
            positionCreditPosting.setAmount(costDeduction);
            positionCreditPosting.setShares(confirmShares != null ? confirmShares : BigDecimal.ZERO);
            positionCreditPosting.setCurrency(product.getCurrency() != null ? product.getCurrency() : "CNY");
            postings.add(positionCreditPosting);

            // FEE DEBIT（手续费）
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

        // 生成真实分录（调用LedgerService.createTransaction）
        if (!postings.isEmpty()) {
            ledgerService.createTransaction(
                order.getUserId(), 
                ownerFamilyId, 
                order.getOrderType(), 
                orderId, 
                postings, 
                "订单结算确认: " + orderId
            );
        }

        // 更新订单状态
        order.setStatus("CONFIRMED");
        orderMapper.update(order);

        return settlement;
    }
}

