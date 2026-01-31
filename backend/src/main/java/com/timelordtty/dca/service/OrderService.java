package com.timelordtty.dca.service;

import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.mapper.OrderMapper;
import com.timelordtty.dca.mapper.OrderFundingLineMapper;
import com.timelordtty.dca.mapper.SettlementConfirmMapper;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.Order;
import com.timelordtty.dca.model.OrderFundingLine;
import com.timelordtty.dca.model.SettlementConfirm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 订单服务（OrderService）
 * 
 * 职责：订单创建、取消与查询。订单代表用户的交易意图，实际资金/份额变动以结算确认（SettlementConfirm）和记账为准。
 * 
 * 关键点：
 * - 下单时需校验账户可用余额并增加账户的 reserved_amount（占用）
 * - 取消或确认时需相应释放/消耗 reserved_amount
 * - 支持组合支付（多个账户共同出资）
 * 
 * 组合支付说明：
 * - 创建订单时：写入order_funding_line记录，分别增加各account.reserved_amount
 * - 取消订单时：逐条释放各account.reserved_amount，删除order_funding_line记录
 * - 确认结算时：按order_funding_line生成多条CASH CREDIT分录，并清零对应account.reserved_amount
 * 
 * 业务规则：
 * 1. 组合支付总额必须等于订单金额：Σ(order_funding_line.amount) = orders.amount（应用层校验）
 * 2. 每个资金来源账户的可用余额必须足够：account.balance - account.reserved_amount >= funding_line.amount（应用层校验）
 * 3. account_id必须是叶子账户（应用层校验）
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@Service
public class OrderService {

    private final OrderMapper orderMapper;
    private final AccountMapper accountMapper;
    private final AccountService accountService;
    private final OrderFundingLineMapper orderFundingLineMapper;
    private final SettlementConfirmMapper settlementConfirmMapper;
    private final com.timelordtty.dca.mapper.LedgerTxnMapper ledgerTxnMapper;
    private final com.timelordtty.dca.mapper.LedgerPostingMapper ledgerPostingMapper;
    private final LedgerService ledgerService;

    public OrderService(OrderMapper orderMapper, AccountMapper accountMapper, AccountService accountService,
                       OrderFundingLineMapper orderFundingLineMapper, SettlementConfirmMapper settlementConfirmMapper,
                       com.timelordtty.dca.mapper.LedgerTxnMapper ledgerTxnMapper,
                       com.timelordtty.dca.mapper.LedgerPostingMapper ledgerPostingMapper,
                       LedgerService ledgerService) {
        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
        this.accountService = accountService;
        this.orderFundingLineMapper = orderFundingLineMapper;
        this.settlementConfirmMapper = settlementConfirmMapper;
        this.ledgerTxnMapper = ledgerTxnMapper;
        this.ledgerPostingMapper = ledgerPostingMapper;
        this.ledgerService = ledgerService;
    }

    /**
     * 创建订单并锁定对应账户的可用资金（reserved_amount）
     * 
     * 支持组合支付：如果fundingLines不为空，使用组合支付；否则使用单个账户（兼容旧接口）
     * 
     * 流程说明：
     * 1. 生成订单ID（格式：ORD-YYYYMMDD-6位随机字符）
     * 2. 创建订单记录（status=PENDING）
     * 3. 如果使用组合支付：
     *    - 校验：Σ(fundingLines.amount) = amount（总额校验）
     *    - 校验每个账户：可用余额 >= funding_line.amount
     *    - 写入order_funding_line表（每个fundingLine一行）
     *    - 分别增加各账户的reserved_amount
     * 4. 如果使用单个账户（兼容旧接口）：
     *    - 校验账户可用余额
     *    - 增加reserved_amount
     * 
     * 业务规则：
     * 1. 组合支付总额必须等于订单金额：Σ(fundingLines.amount) = amount
     * 2. 每个资金来源账户的可用余额必须足够：account.balance - account.reserved_amount >= funding_line.amount
     * 3. account_id必须是叶子账户
     * 
     * @param userId 发起用户ID
     * @param productId 产品ID
     * @param orderType 订单类型：BUY/SELL/SUBSCRIPTION/REDEMPTION
     * @param amount 下单金额（可为空，卖出/赎回时为空）
     * @param shares 下单份额（可为空，买入/申购时为空）
     * @param accountId 使用的账户ID（单个账户，兼容旧接口，如果fundingLines不为空则忽略此参数）
     * @param fundingLines 资金来源列表（组合支付，每个元素包含accountId和amount），可为空
     * @param expectedNavDate 预期净值日期（可为空）
     * @param expectedConfirmDate 预期确认日期（可为空）
     * @return 创建的 Order 实体
     */
    @Transactional
    public Order createOrder(Long userId, Long productId, String orderType, BigDecimal amount, 
                             BigDecimal shares, Long accountId, List<OrderFundingLine> fundingLines,
                             LocalDate expectedNavDate, LocalDate expectedConfirmDate, LocalDateTime requestedAt, BigDecimal feeEstimate) {
        // 生成订单ID
        String orderId = "ORD-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + 
                        "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // 创建订单
        Order order = new Order();
        order.setOrderId(orderId);
        order.setUserId(userId);
        order.setProductId(productId);
        order.setOrderType(orderType);
        order.setAmount(amount);
        order.setShares(shares);
        // 使用用户指定的发起时间，如果没有提供则使用系统当前时间
        order.setRequestedAt(requestedAt != null ? requestedAt : LocalDateTime.now());
        order.setTradeDate(order.getRequestedAt().toLocalDate()); // 使用requestedAt的日期部分
        order.setExpectedNavDate(expectedNavDate);
        order.setExpectedConfirmDate(expectedConfirmDate);
        order.setFeeEstimate(feeEstimate); // 设置手续费
        order.setStatus("PENDING");

        orderMapper.insert(order);

        // 处理组合支付或单个账户
        if (fundingLines != null && !fundingLines.isEmpty()) {
            // 判断是买入还是卖出
            boolean isBuyOrder = "BUY".equals(orderType) || "SUBSCRIPTION".equals(orderType);
            boolean isSellOrder = "SELL".equals(orderType) || "REDEMPTION".equals(orderType);

            if (isBuyOrder) {
                // 买入/申购：使用amount字段，需要占用资金
                BigDecimal totalFunding = fundingLines.stream()
                        .filter(fl -> fl.getAmount() != null)
                        .map(OrderFundingLine::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // 校验总额：Σ(fundingLines.amount) = amount
                if (amount != null && totalFunding.compareTo(amount) != 0) {
                    throw new RuntimeException(
                        String.format("组合支付总额(%s)必须等于订单金额(%s)", totalFunding, amount)
                    );
                }

                // 校验每个账户并增加reserved_amount
                int lineNo = 1;
                for (OrderFundingLine fundingLine : fundingLines) {
                    Long fundingAccountId = fundingLine.getAccountId();
                    
                    // 校验账户存在且为叶子账户
                    Account fundingAccount = accountMapper.selectById(fundingAccountId);
                    if (fundingAccount == null) {
                        throw new RuntimeException("资金来源账户不存在: " + fundingAccountId);
                    }
                    if (!accountService.isLeafAccount(fundingAccountId)) {
                        throw new RuntimeException("资金来源账户必须是叶子账户: " + fundingAccountId);
                    }

                    // 校验可用余额
                    if (fundingLine.getAmount() != null) {
                        BigDecimal available = fundingAccount.getBalance().subtract(fundingAccount.getReservedAmount());
                        if (available.compareTo(fundingLine.getAmount()) < 0) {
                            throw new RuntimeException(
                                String.format("账户[%d]可用余额不足: 可用=%s, 需要=%s", 
                                    fundingAccountId, available, fundingLine.getAmount())
                            );
                        }
                    }

                    // 写入order_funding_line
                    fundingLine.setOrderId(orderId);
                    fundingLine.setLineNo(lineNo++);
                    fundingLine.setCurrency(fundingAccount.getCurrency() != null ? fundingAccount.getCurrency() : "CNY");
                    orderFundingLineMapper.insert(fundingLine);

                    // 增加reserved_amount（占用资金，不扣款，不生成流水）
                    if (fundingLine.getAmount() != null) {
                        BigDecimal newReservedAmount = fundingAccount.getReservedAmount().add(fundingLine.getAmount());
                        accountMapper.updateReservedAmount(fundingAccountId, newReservedAmount);
                    }
                }
            } else if (isSellOrder) {
                // 卖出/赎回：分离 SOURCE（出金账户）和 TARGET（到账账户）
                List<OrderFundingLine> sourceLines = fundingLines.stream()
                        .filter(fl -> "SOURCE".equals(fl.getLineType()) || fl.getLineType() == null)
                        .filter(fl -> fl.getShares() != null && fl.getShares().compareTo(BigDecimal.ZERO) > 0)
                        .collect(java.util.stream.Collectors.toList());
                List<OrderFundingLine> targetLines = fundingLines.stream()
                        .filter(fl -> "TARGET".equals(fl.getLineType()))
                        .collect(java.util.stream.Collectors.toList());

                // 计算出金账户总份额
                BigDecimal totalShares = sourceLines.stream()
                        .map(OrderFundingLine::getShares)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // 校验总份额：Σ(sourceLines.shares) = shares
                if (shares != null && totalShares.compareTo(shares) != 0) {
                    throw new RuntimeException(
                        String.format("组合卖出总份额(%s)必须等于订单份额(%s)", totalShares, shares)
                    );
                }

                // 校验并记录出金账户（SOURCE）
                int lineNo = 1;
                for (OrderFundingLine fundingLine : sourceLines) {
                    Long fundingAccountId = fundingLine.getAccountId();
                    
                    // 校验账户存在且为叶子账户
                    Account fundingAccount = accountMapper.selectById(fundingAccountId);
                    if (fundingAccount == null) {
                        throw new RuntimeException("出金账户不存在: " + fundingAccountId);
                    }
                    if (!accountService.isLeafAccount(fundingAccountId)) {
                        throw new RuntimeException("出金账户必须是叶子账户: " + fundingAccountId);
                    }

                    // 写入order_funding_line（出金账户）
                    fundingLine.setOrderId(orderId);
                    fundingLine.setLineNo(lineNo++);
                    fundingLine.setCurrency(fundingAccount.getCurrency() != null ? fundingAccount.getCurrency() : "CNY");
                    fundingLine.setLineType("SOURCE");
                    orderFundingLineMapper.insert(fundingLine);
                }

                // 校验并记录到账账户（TARGET）
                for (OrderFundingLine fundingLine : targetLines) {
                    Long targetAccountId = fundingLine.getAccountId();
                    
                    // 校验账户存在且为叶子账户
                    Account targetAccount = accountMapper.selectById(targetAccountId);
                    if (targetAccount == null) {
                        throw new RuntimeException("到账账户不存在: " + targetAccountId);
                    }
                    if (!accountService.isLeafAccount(targetAccountId)) {
                        throw new RuntimeException("到账账户必须是叶子账户: " + targetAccountId);
                    }

                    // 写入order_funding_line（到账账户）
                    fundingLine.setOrderId(orderId);
                    fundingLine.setLineNo(lineNo++);
                    fundingLine.setCurrency(targetAccount.getCurrency() != null ? targetAccount.getCurrency() : "CNY");
                    fundingLine.setLineType("TARGET");
                    orderFundingLineMapper.insert(fundingLine);
                }
            }
        } else {
            // 单个账户模式（兼容旧接口）
            if (accountId == null) {
                throw new RuntimeException("单个账户模式必须提供accountId");
            }

            // 校验账户可用余额
            Account account = accountMapper.selectById(accountId);
            if (account == null) {
                throw new RuntimeException("账户不存在");
            }

            if (!accountService.isLeafAccount(accountId)) {
                throw new RuntimeException("账户必须是叶子账户");
            }

            BigDecimal available = account.getBalance().subtract(account.getReservedAmount());
            if (amount != null && available.compareTo(amount) < 0) {
                throw new RuntimeException("可用余额不足");
            }

            // 增加reserved_amount（占用资金，不扣款，不生成流水）
            BigDecimal newReservedAmount = account.getReservedAmount().add(amount != null ? amount : BigDecimal.ZERO);
            accountMapper.updateReservedAmount(accountId, newReservedAmount);

            // 为了兼容，也写入order_funding_line（单行）
            OrderFundingLine fundingLine = new OrderFundingLine();
            fundingLine.setOrderId(orderId);
            fundingLine.setLineNo(1);
            fundingLine.setAccountId(accountId);
            fundingLine.setAmount(amount != null ? amount : BigDecimal.ZERO);
            fundingLine.setCurrency(account.getCurrency() != null ? account.getCurrency() : "CNY");
            orderFundingLineMapper.insert(fundingLine);
        }

        return order;
    }

    /**
     * 创建订单（兼容旧接口，带 fundingLines）
     */
    @Transactional
    public Order createOrder(Long userId, Long productId, String orderType, BigDecimal amount, 
                             BigDecimal shares, Long accountId, List<OrderFundingLine> fundingLines) {
        return createOrder(userId, productId, orderType, amount, shares, accountId, fundingLines, null, null, null, null);
    }

    /**
     * 创建订单（兼容旧接口，单个账户）
     * 
     * @param userId 发起用户ID
     * @param productId 产品ID
     * @param orderType 订单类型
     * @param amount 下单金额（可为空）
     * @param shares 下单份额（可为空）
     * @param accountId 使用的账户ID
     * @return 创建的 Order 实体
     */
    @Transactional
    public Order createOrder(Long userId, Long productId, String orderType, BigDecimal amount, 
                             BigDecimal shares, Long accountId) {
        return createOrder(userId, productId, orderType, amount, shares, accountId, null, null, null, null, null);
    }

    /**
     * 取消订单：仅允许取消处于 PENDING 状态的订单，并释放相应占用资金，同时删除相关流水记录
     * 
     * 重要说明：
     * - 订单创建时：只增加 reserved_amount（占用金额），不扣减 balance，不创建流水，不创建持仓
     * - 订单结算时：释放 reserved_amount，扣减/增加 balance（通过流水），创建流水，创建/更新持仓
     * - 订单取消时（PENDING状态）：
     *   1. 释放 reserved_amount（恢复可用余额）
     *   2. 删除所有与订单相关的流水记录（如果有，直接删除，不是退款）
     *   3. 反向操作恢复账户余额（如果有流水记录）
     *   4. 持仓不会变化（因为未结算的订单不会创建持仓）
     * 
     * 流程说明：
     * 1. 校验订单状态为PENDING
     * 2. 查询并删除与订单相关的所有流水记录（ledger_txn和ledger_posting）
     *    - 如果存在流水记录，反向操作恢复账户余额
     *    - 如果不存在流水记录（正常情况，因为未结算），跳过此步骤
     * 3. 查询order_funding_line，获取所有资金来源行
     * 4. 逐条释放各账户的reserved_amount（恢复可用余额）
     * 5. 删除order_funding_line记录
     * 6. 更新订单状态为CANCELLED
     * 
     * 业务规则：
     * - 只能取消PENDING状态的订单
     * - 取消时释放所有资金来源账户的reserved_amount（恢复可用余额）
     * - 如果存在流水记录，删除流水记录并恢复账户余额
     * - 持仓不会变化（未结算的订单不会创建持仓）
     * 
     * @param orderId 系统订单ID
     */
    @Transactional
    public void cancelOrder(String orderId) {
        Order order = orderMapper.selectByOrderId(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在: " + orderId);
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new RuntimeException("只能取消PENDING状态的订单，当前状态: " + order.getStatus());
        }

        // 查询与订单相关的所有流水记录
        // 注意：正常情况下，PENDING状态的订单不应该有流水记录（流水是在结算时创建的）
        // 但为了处理异常情况（比如订单状态异常），这里仍然检查并删除
        List<com.timelordtty.dca.model.LedgerTxn> relatedTxns = ledgerTxnMapper.selectByOrderId(orderId);
        
        // 删除所有相关的流水记录（直接删除，不是退款）
        for (com.timelordtty.dca.model.LedgerTxn txn : relatedTxns) {
            // 查询所有分录
            List<com.timelordtty.dca.model.LedgerPosting> postings = ledgerPostingMapper.selectByTxnId(txn.getTxnId());
            
            // 反向操作：恢复账户余额
            // 注意：对于PENDING状态的订单，正常情况下不应该有流水，所以这个循环通常不会执行
            for (com.timelordtty.dca.model.LedgerPosting posting : postings) {
                Account account = accountMapper.selectById(posting.getAccountId());
                if (account != null) {
                    BigDecimal currentBalance = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
                    BigDecimal newBalance;
                    if ("DEBIT".equals(posting.getPostingType())) {
                        // 原DEBIT增加余额，现在需要减少
                        newBalance = currentBalance.subtract(posting.getAmount());
                    } else {
                        // 原CREDIT减少余额，现在需要增加
                        newBalance = currentBalance.add(posting.getAmount());
                    }
                    // 确保余额不为负（对于资产类账户）
                    String accType = account.getAccountType();
                    if (newBalance.compareTo(BigDecimal.ZERO) < 0 && 
                        ("CASH".equals(accType) || "POSITION".equals(accType) || "BROKER".equals(accType) ||
                         "MMF".equals(accType) || "ETF".equals(accType) || "LOF".equals(accType) || "FUND".equals(accType) ||
                         "STOCK".equals(accType) || "BOND".equals(accType) || "RECEIVABLE".equals(accType) ||
                         "BANK_WM_NAV".equals(accType) || "BANK_WM_BOX".equals(accType) || "OPTION".equals(accType) ||
                         "INVESTABLE".equals(accType) || "SPENDABLE".equals(accType) || "RESERVED".equals(accType) ||
                         "PAYMENT".equals(accType) || "BANK".equals(accType) || "OTHER".equals(accType))) {
                        newBalance = BigDecimal.ZERO;
                    }
                    accountMapper.updateBalance(posting.getAccountId(), newBalance);
                }
            }
            
            // 删除所有分录
            ledgerPostingMapper.deleteByTxnId(txn.getTxnId());
            
            // 删除交易记录
            ledgerTxnMapper.deleteByTxnId(txn.getTxnId());
        }

        // 查询order_funding_line，获取所有资金来源行
        List<OrderFundingLine> fundingLines = orderFundingLineMapper.selectByOrderId(orderId);
        
        // 逐条释放各账户的reserved_amount（恢复可用余额）
        // 注意：这是取消订单的核心操作，因为订单创建时只增加了reserved_amount，没有扣减balance
        for (OrderFundingLine fundingLine : fundingLines) {
            Account account = accountMapper.selectById(fundingLine.getAccountId());
            if (account != null && fundingLine.getAmount() != null) {
                BigDecimal currentReserved = account.getReservedAmount() != null ? account.getReservedAmount() : BigDecimal.ZERO;
                BigDecimal newReservedAmount = currentReserved.subtract(fundingLine.getAmount());
                if (newReservedAmount.compareTo(BigDecimal.ZERO) < 0) {
                    newReservedAmount = BigDecimal.ZERO; // 防止负数
                }
                accountMapper.updateReservedAmount(fundingLine.getAccountId(), newReservedAmount);
            }
        }

        // 删除order_funding_line记录（CASCADE会自动删除，但显式删除更清晰）
        orderFundingLineMapper.deleteByOrderId(orderId);

        // 更新订单状态
        order.setStatus("CANCELLED");
        orderMapper.update(order);
    }

    public List<Order> getPendingOrders() {
        return orderMapper.selectByStatus("PENDING");
    }

    public List<Order> getOrdersByStatus(String status) {
        return orderMapper.selectByStatus(status);
    }

    public List<Order> getOrdersByUserId(Long userId) {
        return orderMapper.selectByUserId(userId);
    }

    public Order getOrderByOrderId(String orderId) {
        return orderMapper.selectByOrderId(orderId);
    }

    public List<OrderFundingLine> getOrderFundingLines(String orderId) {
        return orderFundingLineMapper.selectByOrderId(orderId);
    }

    public SettlementConfirm getSettlementByOrderId(String orderId) {
        return settlementConfirmMapper.selectByOrderId(orderId);
    }
}

