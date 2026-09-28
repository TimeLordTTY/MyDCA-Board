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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 订单服务（OrderService）
 * 
 * 职责：订单创建、取消与查询。订单代表用户的交易意图，实际资金/份额变动以结算确认（SettlementConfirm）和记账为准。
 * 
 * 关键点（与当前代码事实一致）：
 * - BUY / SUBSCRIPTION 下单时立即生成付款账本：CASH CREDIT（付款账户余额减少）+ RECEIVABLE DEBIT（待结算应收增加），
 *   不再增加 account.reserved_amount；余额可用性校验仍为 account.balance - account.reserved_amount >= 出资金额
 * - SELL / REDEMPTION 下单时不生成流水（锁定的是份额而非金额）
 * - 取消 PENDING 订单时会反向恢复下单付款流水余额，并兼容清理历史遗留的 reserved_amount
 * - 支持组合支付（多个账户共同出资）
 * 
 * 组合支付说明：
 * - 创建订单时：写入order_funding_line记录；BUY / SUBSCRIPTION 立即按资金来源生成付款账本（CASH CREDIT + RECEIVABLE DEBIT）
 * - 取消订单时：删除与订单关联的下单付款流水并恢复余额，删除order_funding_line记录
 * - 确认结算时：由 SettlementService 按order_funding_line把 RECEIVABLE 转为 POSITION / 关联现金账户，并处理手续费
 * 
 * 业务规则：
 * 1. 组合支付总额必须等于订单金额：Σ(order_funding_line.amount) = orders.amount（应用层校验）
 * 2. 每个资金来源账户的可用余额必须足够：account.balance - account.reserved_amount >= funding_line.amount（应用层校验）
 * 3. account_id必须是叶子账户（应用层校验）
 * 4. 草稿投资确认必须走 createInvestmentDraftOrder，重新校验产品、币种、资金用途与账户可见性
 * 5. 草稿卖出 / 赎回确认必须走 createSellRedeemDraftOrder：只登记 SOURCE / TARGET 份额占用，不生成账本、不改现金与持仓
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@Service
public class OrderService {

    /**
     * 订单 Mapper，负责订单主记录的查询、创建、状态更新和结算筛选。
     */
    private final OrderMapper orderMapper;
    /**
     * 账户持久化 Mapper，负责账户主表的查询、插入、更新和账户树读取。
     */
    private final AccountMapper accountMapper;
    /**
     * 账户服务入口，负责账户树查询、账户归属校验、账户创建更新以及余额调整编排。
     */
    private final AccountService accountService;
    /**
     * 订单资金来源 Mapper，负责订单创建和结算时读取各账户出资明细。
     */
    private final OrderFundingLineMapper orderFundingLineMapper;
    /**
     * 结算确认 Mapper，负责保存订单成交确认和结算金额明细。
     */
    private final SettlementConfirmMapper settlementConfirmMapper;
    /**
     * 账本事务 Mapper，用于订单详情中读取由订单结算生成的账本主事务。
     */
    private final com.timelordtty.dca.mapper.LedgerTxnMapper ledgerTxnMapper;
    /**
     * 账本分录 Mapper，用于订单详情中读取成交确认后生成的借贷分录。
     */
    private final com.timelordtty.dca.mapper.LedgerPostingMapper ledgerPostingMapper;
    /**
     * 账本服务入口，负责流水事务、分录、余额影响和快速记账编排。
     */
    private final LedgerService ledgerService;
    /**
     * 用户身份服务，用于根据当前登录名定位用户、家庭和角色权限上下文。
     */
    private final UserService userService;
    /**
     * 产品主数据 Mapper，负责产品代码、名称、市场类型和展示顺序的持久化访问。
     */
    private final ProductMasterMapper productMasterMapper;

    /**
     * 装配订单、资金来源、结算、账户、产品和账本组件，串联订单创建、取消与成交确认流程。
     */
    public OrderService(OrderMapper orderMapper, AccountMapper accountMapper, AccountService accountService,
                       OrderFundingLineMapper orderFundingLineMapper, SettlementConfirmMapper settlementConfirmMapper,
                       com.timelordtty.dca.mapper.LedgerTxnMapper ledgerTxnMapper,
                       com.timelordtty.dca.mapper.LedgerPostingMapper ledgerPostingMapper,
                       LedgerService ledgerService, UserService userService,
                       ProductMasterMapper productMasterMapper) {
        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
        this.accountService = accountService;
        this.orderFundingLineMapper = orderFundingLineMapper;
        this.settlementConfirmMapper = settlementConfirmMapper;
        this.ledgerTxnMapper = ledgerTxnMapper;
        this.ledgerPostingMapper = ledgerPostingMapper;
        this.ledgerService = ledgerService;
        this.userService = userService;
        this.productMasterMapper = productMasterMapper;
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
                // 买入/申购：使用amount字段，立即生成付款流水（CASH CREDIT + RECEIVABLE DEBIT）
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

                // 校验每个账户并写入 order_funding_line
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

                    // 不再增加reserved_amount，而是直接通过流水扣款（见下方流水生成）
                }

                // 生成付款流水：CASH CREDIT（付款账户减少）+ RECEIVABLE DEBIT（待结算增加）
                generateBuyOrderLedger(order, fundingLines, totalFunding);
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

            // 写入order_funding_line（单行）
            OrderFundingLine fundingLine = new OrderFundingLine();
            fundingLine.setOrderId(orderId);
            fundingLine.setLineNo(1);
            fundingLine.setAccountId(accountId);
            fundingLine.setAmount(amount != null ? amount : BigDecimal.ZERO);
            fundingLine.setCurrency(account.getCurrency() != null ? account.getCurrency() : "CNY");
            orderFundingLineMapper.insert(fundingLine);

            // 买入/申购：直接生成付款流水而非增加reserved_amount
            boolean isBuyOrderSingle = "BUY".equals(orderType) || "SUBSCRIPTION".equals(orderType);
            if (isBuyOrderSingle && amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                List<OrderFundingLine> singleLine = new ArrayList<>();
                singleLine.add(fundingLine);
                generateBuyOrderLedger(order, singleLine, amount);
            } else {
                // 卖出/赎回：保持原逻辑，不在下单时生成流水
                // 不需要reserved_amount（卖出时锁定的是份额，不是金额）
            }
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
     * 草稿投资确认的安全入口：只允许 BUY / SUBSCRIPTION，并在创建订单前再次完成全部安全校验。
     *
     * <p>该入口不依赖“知道 accountId 就能下单”的弱边界：会重新校验 user/family 账户可见性、叶子账户、
     * 产品启用、产品与账户币种、资金用途（一般投资只允许 INVESTABLE，BOND_REPO 额外允许 RESERVED）以及可用余额。
     * 校验通过后复用 createOrder 的既有订单与付款账本语义，创建 PENDING 订单并生成下单付款流水。</p>
     *
     * <p>本方法不自动结算、不生成最终持仓，也不调用任何真实交易渠道。</p>
     *
     * @param userId 发起用户 ID
     * @param familyId 家庭 ID，可为空
     * @param productId 真实产品 ID，必须由用户明确选择
     * @param orderType 订单类型，只允许 BUY / SUBSCRIPTION
     * @param amount 下单金额，必须大于 0
     * @param accountId 单一资金来源叶子账户 ID
     * @param expectedNavDate 预期净值日期（ISO 字符串，可空）
     * @param expectedConfirmDate 预期确认日期（ISO 字符串，可空）
     * @param note 备注，仅用于调用方追踪；订单流水备注仍由订单付款模板生成
     * @return 创建的 PENDING 订单
     */
    @Transactional
    public Order createInvestmentDraftOrder(Long userId, Long familyId, Long productId, String orderType,
                                            BigDecimal amount, Long accountId, String expectedNavDate,
                                            String expectedConfirmDate, String note) {
        if (!"BUY".equals(orderType) && !"SUBSCRIPTION".equals(orderType)) {
            throw new RuntimeException("草稿投资确认只支持 BUY / SUBSCRIPTION 订单");
        }
        if (productId == null) {
            throw new RuntimeException("投资草稿必须由主人明确选择真实产品，不能按产品名称自动匹配");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("投资订单金额必须大于 0");
        }
        if (accountId == null) {
            throw new RuntimeException("投资草稿必须指定单一资金来源账户");
        }

        ProductMaster product = productMasterMapper.selectById(productId);
        if (product == null || !Boolean.TRUE.equals(product.getIsActive())) {
            throw new RuntimeException("产品不存在或已停用，不能用于买入 / 申购订单");
        }

        Account account = accountMapper.selectVisibleRealById(accountId, userId, familyId);
        if (account == null) {
            throw new RuntimeException("资金来源账户不存在、已停用或当前用户/家庭不可见");
        }
        if (!accountService.isLeafAccount(accountId)) {
            throw new RuntimeException("资金来源账户必须是叶子账户");
        }

        boolean bondRepo = "BOND_REPO".equalsIgnoreCase(product.getAssetType());
        String fundUsage = account.getFundUsage();
        if (!"INVESTABLE".equals(fundUsage) && !("RESERVED".equals(fundUsage) && bondRepo)) {
            throw new RuntimeException(
                    "资金来源账户资金用途不允许普通投资，请使用 INVESTABLE 叶子账户（BOND_REPO 可用 RESERVED）");
        }
        if (!currencyEquals(account.getCurrency(), product.getCurrency())) {
            throw new RuntimeException("资金账户币种与产品币种必须一致");
        }

        BigDecimal balance = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
        BigDecimal reserved = account.getReservedAmount() != null ? account.getReservedAmount() : BigDecimal.ZERO;
        BigDecimal available = balance.subtract(reserved);
        if (available.compareTo(amount) < 0) {
            throw new RuntimeException(String.format("账户[%d]可用余额不足: 可用=%s, 需要=%s", accountId, available, amount));
        }

        return createOrder(userId, productId, orderType, amount, null, accountId, null,
                parseOptionalDate(expectedNavDate), parseOptionalDate(expectedConfirmDate), null, null);
    }

    /**
     * 草稿卖出 / 赎回确认的安全入口：只允许 SELL / REDEMPTION，并在创建订单前再次完成全部安全校验。
     *
     * <p>SELL / REDEMPTION 与 BUY / SUBSCRIPTION 的真实语义不同：下单阶段只登记份额占用，
     * 不生成 CASH / POSITION 流水、不改现金余额、不改持仓，也不调用 SettlementService；
     * 真正的资金与持仓变化只在后续人工结算（SettlementService）时产生。</p>
     *
     * <p>写入的 order_funding_line 固定两行：SOURCE 行保存持仓来源账户 + 份额，TARGET 行保存到账账户。
     * 到账账户必须是当前 user/family 可见、启用的 REAL 叶子账户，币种与产品一致，
     * 且不能是 POSITION 持仓账户、VIRTUAL 虚拟账户或父账户。</p>
     *
     * @param userId 发起用户 ID
     * @param familyId 家庭 ID，可为空
     * @param productId 真实产品 ID，必须由用户明确选择
     * @param orderType 订单类型，只允许 SELL / REDEMPTION
     * @param shares 卖出 / 赎回份额，必须大于 0
     * @param sourceAccountId 持仓来源账户 ID
     * @param targetAccountId 到账账户 ID
     * @param expectedNavDate 预期净值日期（ISO 字符串，可空）
     * @param expectedConfirmDate 预期确认日期（ISO 字符串，可空）
     * @param note 备注，仅用于调用方追踪；订单流水备注仍由订单模板生成
     * @return 创建的 PENDING 订单
     */
    @Transactional
    public Order createSellRedeemDraftOrder(Long userId, Long familyId, Long productId, String orderType,
                                            BigDecimal shares, Long sourceAccountId, Long targetAccountId,
                                            String expectedNavDate, String expectedConfirmDate, String note) {
        if (!"SELL".equals(orderType) && !"REDEMPTION".equals(orderType)) {
            throw new RuntimeException("草稿卖出 / 赎回确认只支持 SELL / REDEMPTION 订单");
        }
        if (productId == null) {
            throw new RuntimeException("卖出 / 赎回草稿必须由主人明确选择真实产品，不能按产品名称自动匹配");
        }
        if (shares == null || shares.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("卖出 / 赎回份额必须大于 0");
        }
        if (sourceAccountId == null) {
            throw new RuntimeException("卖出 / 赎回草稿必须指定持仓来源账户");
        }
        if (targetAccountId == null) {
            throw new RuntimeException("卖出 / 赎回草稿必须指定到账账户");
        }

        ProductMaster product = productMasterMapper.selectById(productId);
        if (product == null || !Boolean.TRUE.equals(product.getIsActive())) {
            throw new RuntimeException("产品不存在或已停用，不能用于卖出 / 赎回订单");
        }

        Account sourceAccount = accountMapper.selectById(sourceAccountId);
        if (sourceAccount == null || !Boolean.TRUE.equals(sourceAccount.getIsActive())) {
            throw new RuntimeException("持仓来源账户不存在或已停用");
        }
        if (!accountService.isLeafAccount(sourceAccountId)) {
            throw new RuntimeException("持仓来源账户必须是叶子账户");
        }
        if (!isVisibleToUserOrFamily(sourceAccount, userId, familyId)) {
            throw new RuntimeException("持仓来源账户不存在、已停用或当前用户/家庭不可见");
        }

        Account targetAccount = accountMapper.selectVisibleRealById(targetAccountId, userId, familyId);
        if (targetAccount == null) {
            throw new RuntimeException("到账账户不存在、已停用或当前用户/家庭不可见");
        }
        if (!accountService.isLeafAccount(targetAccountId)) {
            throw new RuntimeException("到账账户必须是叶子账户");
        }
        if ("POSITION".equalsIgnoreCase(targetAccount.getAccountType())) {
            throw new RuntimeException("到账账户不能是 POSITION 持仓账户，请选择现金类 REAL 叶子账户");
        }
        if (!currencyEquals(targetAccount.getCurrency(), product.getCurrency())) {
            throw new RuntimeException("到账账户币种与产品币种必须一致");
        }

        List<OrderFundingLine> fundingLines = new ArrayList<>();
        OrderFundingLine sourceLine = new OrderFundingLine();
        sourceLine.setAccountId(sourceAccountId);
        sourceLine.setShares(shares);
        sourceLine.setLineType("SOURCE");
        fundingLines.add(sourceLine);
        OrderFundingLine targetLine = new OrderFundingLine();
        targetLine.setAccountId(targetAccountId);
        targetLine.setLineType("TARGET");
        fundingLines.add(targetLine);

        return createOrder(userId, productId, orderType, null, shares, null, fundingLines,
                parseOptionalDate(expectedNavDate), parseOptionalDate(expectedConfirmDate), null, null);
    }

    /** user/family 可见性判断，与 accounts 表可见性语义保持一致。 */
    private boolean isVisibleToUserOrFamily(Account account, Long userId, Long familyId) {
        if (account.getOwnerUserId() != null && account.getOwnerUserId().equals(userId)) {
            return true;
        }
        return familyId != null && account.getOwnerFamilyId() != null
                && account.getOwnerFamilyId().equals(familyId);
    }

    /** 解析可选 ISO 日期；空值或无法解析时返回 null，不阻断订单创建。 */
    private LocalDate parseOptionalDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** 比较两个币种是否一致；双方都为空时视为一致，避免因历史空值误阻断。 */
    private boolean currencyEquals(String left, String right) {
        if (left == null || left.isBlank()) {
            return right == null || right.isBlank();
        }
        return left.equalsIgnoreCase(right);
    }

    /**
     * 买入/申购下单时立即生成付款流水
     * 
     * 分录模板（保持借贷平衡）：
     * - CASH CREDIT × N（付款账户余额减少，按 funding_line 拆分）
     * - RECEIVABLE DEBIT（待结算应收增加，金额 = 付款总额）
     * 
     * 结算时再生成第二笔流水（SettlementService 负责）：
     * - RECEIVABLE CREDIT（待结算应收清零）
     * - POSITION DEBIT / 关联账户 CASH DEBIT（持仓/余额增加）
     * - FEE DEBIT（手续费）
     */
    private void generateBuyOrderLedger(Order order, List<OrderFundingLine> fundingLines, BigDecimal totalAmount) {
        // 获取用户信息
        com.timelordtty.dca.dto.AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        String ownerType = currentUser.getFamilyId() != null ? "FAMILY" : "PERSONAL";
        Long ownerUserId = currentUser.getId();
        Long ownerFamilyId = currentUser.getFamilyId();

        // 获取产品信息（用于备注）
        ProductMaster product = productMasterMapper.selectById(order.getProductId());
        String productName = product != null ? product.getProductName() : "产品" + order.getProductId();

        List<LedgerPosting> postings = new ArrayList<>();

        // 1. CASH CREDIT：付款账户余额减少（按 funding_line 拆分）
        for (OrderFundingLine fundingLine : fundingLines) {
            if (fundingLine.getAmount() != null && fundingLine.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                LedgerPosting cashPosting = new LedgerPosting();
                cashPosting.setPostingType("CREDIT");
                cashPosting.setAccountId(fundingLine.getAccountId());
                cashPosting.setAccountType("CASH");
                cashPosting.setAmount(fundingLine.getAmount());
                cashPosting.setCurrency(fundingLine.getCurrency() != null ? fundingLine.getCurrency() : "CNY");
                postings.add(cashPosting);
            }
        }

        // 2. RECEIVABLE DEBIT：待结算应收增加
        Account receivableAccount = accountService.getOrCreateVirtualAccount(
            "RECEIVABLE", "RECEIVABLE", ownerType, ownerUserId, ownerFamilyId, null, null);
        LedgerPosting receivablePosting = new LedgerPosting();
        receivablePosting.setPostingType("DEBIT");
        receivablePosting.setAccountId(receivableAccount.getId());
        receivablePosting.setAccountType("RECEIVABLE");
        receivablePosting.setAmount(totalAmount);
        receivablePosting.setCurrency("CNY");
        postings.add(receivablePosting);

        // 3. 创建流水（使用订单发起时间）
        String orderTypeLabel = "BUY".equals(order.getOrderType()) ? "买入" : "申购";
        String note = String.format("下单付款: %s %s，金额%.2f元", orderTypeLabel, productName, totalAmount);
        
        String requestedAtStr = order.getRequestedAt()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        ledgerService.createTransaction(
            order.getUserId(),
            ownerFamilyId,
            order.getOrderType(),
            order.getOrderId(),  // bizGroupKey = orderId
            postings,
            note,
            requestedAtStr,
            null,   // categoryId
            false,  // isReimbursable
            order.getProductId(),
            order.getOrderId()   // orderId - 关联订单，取消时可通过 selectByOrderId 找到
        );
    }

    /**
     * 取消订单：仅允许取消处于 PENDING 状态的订单，删除相关流水记录并恢复余额
     * 
     * 流程说明：
     * 1. 校验订单状态为PENDING
     * 2. 查询并删除与订单相关的所有流水记录（包括下单时创建的付款流水）
     *    - 反向操作恢复账户余额
     * 3. 释放可能残留的reserved_amount（兼容旧逻辑）
     * 4. 删除order_funding_line记录
     * 5. 更新订单状态为CANCELLED
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
        
        // 逐条释放各账户的reserved_amount（历史兼容）
        // 注意：当前 BUY / SUBSCRIPTION 下单已改为直接生成付款账本并扣减 balance，
        // 这里只清理历史遗留或被其它流程写入的 reserved_amount，避免残留占用。
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

    /**
     * 读取订单相关列表或明细，供订单台账、结算确认和资金来源展示使用。
     */
    public List<Order> getPendingOrders() {
        return orderMapper.selectByStatus("PENDING");
    }

    /**
     * 读取订单相关列表或明细，供订单台账、结算确认和资金来源展示使用。
     */
    public List<Order> getOrdersByStatus(String status) {
        return orderMapper.selectByStatus(status);
    }

    /**
     * 读取订单关联对象 ID，用于把订单与用户、结算确认或资金来源明细关联起来。
     */
    public List<Order> getOrdersByUserId(Long userId) {
        return orderMapper.selectByUserId(userId);
    }

    /**
     * 读取订单关联对象 ID，用于把订单与用户、结算确认或资金来源明细关联起来。
     */
    public Order getOrderByOrderId(String orderId) {
        return orderMapper.selectByOrderId(orderId);
    }

    /**
     * 读取订单相关列表或明细，供订单台账、结算确认和资金来源展示使用。
     */
    public List<OrderFundingLine> getOrderFundingLines(String orderId) {
        return orderFundingLineMapper.selectByOrderId(orderId);
    }
    /**
     * 统计指定产品 + 持仓来源账户下仍为 PENDING 的 SELL / REDEMPTION 占用份额。
     *
     * <p>SELL / REDEMPTION 下单不生成账本，占用只存在于 order_funding_line，因此草稿可用份额
     * 必须扣除这里返回的占用份额，避免同一产品 / 来源账户被内部重复占用。</p>
     */
    public BigDecimal sumPendingSellSharesByAccount(Long productId, Long userId, Long accountId) {
        if (productId == null || userId == null || accountId == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal occupied = orderFundingLineMapper.sumPendingSellSharesByAccount(productId, userId, accountId);
        return occupied != null ? occupied : BigDecimal.ZERO;
    }

    /**
     * 读取订单关联对象 ID，用于把订单与用户、结算确认或资金来源明细关联起来。
     */
    public SettlementConfirm getSettlementByOrderId(String orderId) {
        return settlementConfirmMapper.selectByOrderId(orderId);
    }
}

