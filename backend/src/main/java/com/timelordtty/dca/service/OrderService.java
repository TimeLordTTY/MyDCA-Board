package com.timelordtty.dca.service;

import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.mapper.OrderMapper;
import com.timelordtty.dca.mapper.OrderFundingLineMapper;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.Order;
import com.timelordtty.dca.model.OrderFundingLine;
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

    public OrderService(OrderMapper orderMapper, AccountMapper accountMapper, AccountService accountService,
                       OrderFundingLineMapper orderFundingLineMapper) {
        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
        this.accountService = accountService;
        this.orderFundingLineMapper = orderFundingLineMapper;
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
     * @return 创建的 Order 实体
     */
    @Transactional
    public Order createOrder(Long userId, Long productId, String orderType, BigDecimal amount, 
                             BigDecimal shares, Long accountId, List<OrderFundingLine> fundingLines) {
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
        order.setRequestedAt(LocalDateTime.now());
        order.setTradeDate(LocalDate.now());
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
                // 卖出/赎回：使用shares字段，不需要占用资金，只需要记录卖出份额
                BigDecimal totalShares = fundingLines.stream()
                        .filter(fl -> fl.getShares() != null)
                        .map(OrderFundingLine::getShares)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // 校验总份额：Σ(fundingLines.shares) = shares
                if (shares != null && totalShares.compareTo(shares) != 0) {
                    throw new RuntimeException(
                        String.format("组合卖出总份额(%s)必须等于订单份额(%s)", totalShares, shares)
                    );
                }

                // 校验每个账户并记录卖出份额
                int lineNo = 1;
                for (OrderFundingLine fundingLine : fundingLines) {
                    Long fundingAccountId = fundingLine.getAccountId();
                    
                    // 校验账户存在且为叶子账户
                    Account fundingAccount = accountMapper.selectById(fundingAccountId);
                    if (fundingAccount == null) {
                        throw new RuntimeException("卖出账户不存在: " + fundingAccountId);
                    }
                    if (!accountService.isLeafAccount(fundingAccountId)) {
                        throw new RuntimeException("卖出账户必须是叶子账户: " + fundingAccountId);
                    }

                    // 写入order_funding_line（卖出时不需要占用资金，只记录份额）
                    fundingLine.setOrderId(orderId);
                    fundingLine.setLineNo(lineNo++);
                    fundingLine.setCurrency(fundingAccount.getCurrency() != null ? fundingAccount.getCurrency() : "CNY");
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
        return createOrder(userId, productId, orderType, amount, shares, accountId, null);
    }

    /**
     * 取消订单：仅允许取消处于 PENDING 状态的订单，并释放相应占用资金
     * 
     * 流程说明：
     * 1. 校验订单状态为PENDING
     * 2. 查询order_funding_line，获取所有资金来源行
     * 3. 逐条释放各账户的reserved_amount
     * 4. 删除order_funding_line记录（CASCADE自动删除，但显式删除更清晰）
     * 5. 更新订单状态为CANCELLED
     * 
     * 业务规则：
     * - 只能取消PENDING状态的订单
     * - 取消时释放所有资金来源账户的reserved_amount
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

        // 查询order_funding_line，获取所有资金来源行
        List<OrderFundingLine> fundingLines = orderFundingLineMapper.selectByOrderId(orderId);
        
        // 逐条释放各账户的reserved_amount
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

        // 删除order_funding_line记录（CASCADE会自动删除，但显式删除更清晰）
        orderFundingLineMapper.deleteByOrderId(orderId);

        // 更新订单状态
        order.setStatus("CANCELLED");
        orderMapper.update(order);
    }

    public List<Order> getPendingOrders() {
        return orderMapper.selectByStatus("PENDING");
    }
}

