package com.timelordtty.dca.service;

import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.mapper.LedgerPostingMapper;
import com.timelordtty.dca.mapper.LedgerTxnMapper;
import com.timelordtty.dca.mapper.NavMapper;
import com.timelordtty.dca.mapper.OrderFundingLineMapper;
import com.timelordtty.dca.mapper.OrderMapper;
import com.timelordtty.dca.mapper.ProductMasterMapper;
import com.timelordtty.dca.mapper.SettlementConfirmMapper;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.LedgerPosting;
import com.timelordtty.dca.model.LedgerTxn;
import com.timelordtty.dca.model.Nav;
import com.timelordtty.dca.model.Order;
import com.timelordtty.dca.model.OrderFundingLine;
import com.timelordtty.dca.model.ProductMaster;
import com.timelordtty.dca.model.SettlementConfirm;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 持仓计算服务（HoldingService）
 * 
 * 职责：基于会计分录（POSITION 类型）聚合持仓信息，计算持仓份额、成本、平均成本与未实现盈亏等
 * 
 * 持仓计算公式：
 * 1. 总份额（totalShares）= Σ(POSITION DEBIT shares) - Σ(POSITION CREDIT shares)
 * 2. 总成本（totalCost）= Σ(POSITION DEBIT amount) - Σ(POSITION CREDIT amount)
 * 3. 平均成本（avgCost）= totalCost / totalShares（如果totalShares > 0）
 * 4. 持仓市值（marketValue）= totalShares × 当前净值（需要外部行情数据，本服务不计算）
 * 5. 未实现盈亏（unrealizedPnl）= marketValue - totalCost（需要外部行情数据，本服务不计算）
 * 
 * 计算逻辑：
 * - 从ledger_posting查询所有account_type=POSITION的分录
 * - 通过ledger_txn获取productId（每个持仓账户对应一个产品）
 * - 按productId聚合，计算每个产品的持仓信息
 * 
 * 业务规则：
 * - 持仓计算基于流水，实时计算，不依赖快照
 * - 持仓市值和未实现盈亏需要外部行情数据，本服务仅计算基础数据（份额、成本）
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@Service
/**
 * 业务注释规范化: HoldingService 服务类，负责业务规则、账户、账本流水、订单或持仓数据的组合处理。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class HoldingService {

    /**
     * 业务注释规范化: ledgerPostingMapper 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final LedgerPostingMapper ledgerPostingMapper;
    /**
     * 业务注释规范化: ledgerTxnMapper 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final LedgerTxnMapper ledgerTxnMapper;
    /**
     * 业务注释规范化: productMasterMapper 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final ProductMasterMapper productMasterMapper;
    /**
     * 业务注释规范化: productService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final ProductService productService;
    /**
     * 业务注释规范化: accountService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final AccountService accountService;
    /**
     * 业务注释规范化: ledgerService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final LedgerService ledgerService;
    /**
     * 业务注释规范化: orderMapper 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final OrderMapper orderMapper;
    /**
     * 业务注释规范化: orderFundingLineMapper 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final OrderFundingLineMapper orderFundingLineMapper;
    /**
     * 业务注释规范化: settlementConfirmMapper 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final SettlementConfirmMapper settlementConfirmMapper;
    /**
     * 业务注释规范化: accountMapper 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final AccountMapper accountMapper;
    /**
     * 业务注释规范化: navMapper 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final NavMapper navMapper;

    /**
     * 业务注释规范化: 处理 HoldingService 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param ledgerPostingMapper ledgerPostingMapper 业务字段，承载该对象在后端流程中的核心属性。
     * @param ledgerTxnMapper ledgerTxnMapper 业务字段，承载该对象在后端流程中的核心属性。
     * @param productMasterMapper productMasterMapper 业务字段，承载该对象在后端流程中的核心属性。
     * @param productService productService 业务字段，承载该对象在后端流程中的核心属性。
     * @param accountService accountService 业务字段，承载该对象在后端流程中的核心属性。
     * @param ledgerService ledgerService 业务字段，承载该对象在后端流程中的核心属性。
     * @param orderMapper orderMapper 业务字段，承载该对象在后端流程中的核心属性。
     * @param orderFundingLineMapper orderFundingLineMapper 业务字段，承载该对象在后端流程中的核心属性。
     * @param settlementConfirmMapper settlementConfirmMapper 业务字段，承载该对象在后端流程中的核心属性。
     * @param accountMapper accountMapper 业务字段，承载该对象在后端流程中的核心属性。
     * @param navMapper navMapper 业务字段，承载该对象在后端流程中的核心属性。
     */
    public HoldingService(LedgerPostingMapper ledgerPostingMapper, LedgerTxnMapper ledgerTxnMapper,
                          ProductMasterMapper productMasterMapper,
                          ProductService productService, @Lazy AccountService accountService, LedgerService ledgerService,
                          OrderMapper orderMapper, OrderFundingLineMapper orderFundingLineMapper,
                          SettlementConfirmMapper settlementConfirmMapper, AccountMapper accountMapper,
                          NavMapper navMapper) {
        this.ledgerPostingMapper = ledgerPostingMapper;
        this.ledgerTxnMapper = ledgerTxnMapper;
        this.productMasterMapper = productMasterMapper;
        this.productService = productService;
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.orderMapper = orderMapper;
        this.orderFundingLineMapper = orderFundingLineMapper;
        this.settlementConfirmMapper = settlementConfirmMapper;
        this.accountMapper = accountMapper;
        this.navMapper = navMapper;
    }

    /**
     * 计算实时持仓（基于流水聚合）
     * 
     * 流程说明：
     * 1. 查询所有POSITION类型的账户（虚拟账户，account_kind=VIRTUAL, account_type=POSITION）
     * 2. 对每个POSITION账户，查询所有分录（ledger_posting）
     * 3. 通过ledger_txn获取productId（每个持仓账户对应一个产品，通过账户名称匹配）
     * 4. 按productId聚合，计算总份额、总成本、平均成本
     * 
     * 计算公式：
     * - totalShares = Σ(DEBIT shares) - Σ(CREDIT shares)
     * - totalCost = Σ(DEBIT amount) - Σ(CREDIT amount)
     * - avgCost = totalCost / totalShares（如果totalShares > 0）
     * 
     * 注意：
     * - 持仓市值（marketValue）和未实现盈亏（unrealizedPnl）需要外部行情数据，本服务不计算
     * - 持仓账户名称格式："持仓账户-{产品名称}"，通过名称可以匹配产品
     * 
     * @param userId 用户ID
     * @param familyId 家庭ID，可为空
     * @return 持仓信息列表（同一产品在不同券商会有多条）
     */
    /**
     * 业务注释规范化: 计算 calculateHoldings 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param userId 所属用户 ID，用于限定个人数据权限和查询范围。
     * @param familyId 所属家庭 ID，用于家庭视角下的数据隔离。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public List<HoldingInfo> calculateHoldings(Long userId, Long familyId) {
        // 直接查询所有POSITION类型的分录（属于该用户或家庭的）
        // 这样更直接，不需要先查账户
        List<LedgerPosting> postings = ledgerPostingMapper.selectByAccountTypeAndOwner("POSITION", userId, familyId);

        Map<String, HoldingInfo> holdings = new HashMap<>();

        // 1) 基于 POSITION 分录计算基础持仓成本和份额
        for (LedgerPosting posting : postings) {
            // 通过ledger_txn获取productId
            LedgerTxn txn = ledgerTxnMapper.selectByTxnId(posting.getTxnId());
            if (txn == null || txn.getProductId() == null) {
                continue; // 跳过没有productId的交易
            }

            Long productId = txn.getProductId();

            // 推断券商：券商维度 POSITION 账户会挂在 BROKER 父账户下
            Long brokerAccountId = null;
            String brokerAccountName = null;
            Account positionAccount = accountMapper.selectById(posting.getAccountId());
            if (positionAccount != null && positionAccount.getParentAccountId() != null) {
                Account parent = accountMapper.selectById(positionAccount.getParentAccountId());
                if (parent != null && "BROKER".equals(parent.getAccountType()) && parent.getParentAccountId() == null) {
                    brokerAccountId = parent.getId();
                    brokerAccountName = parent.getAccountName();
                }
            }

            String key = (brokerAccountId != null ? brokerAccountId.toString() : "NULL") + ":" + productId;

            // 创建或获取持仓信息
            HoldingInfo holding = holdings.get(key);
            if (holding == null) {
                holding = new HoldingInfo();
                holding.setProductId(productId);
                holding.setBrokerAccountId(brokerAccountId);
                holding.setBrokerAccountName(brokerAccountName);
            }
            if (holding.getTotalShares() == null) {
                holding.setTotalShares(BigDecimal.ZERO);
            }
            if (holding.getTotalCost() == null) {
                holding.setTotalCost(BigDecimal.ZERO);
            }

            // 计算份额和成本（仅基于 POSITION 分录）
            if ("DEBIT".equals(posting.getPostingType())) {
                // DEBIT：持仓增加
                if (posting.getShares() != null) {
                    holding.setTotalShares(holding.getTotalShares().add(posting.getShares()));
                }
                holding.setTotalCost(holding.getTotalCost().add(posting.getAmount()));
            } else if ("CREDIT".equals(posting.getPostingType())) {
                // CREDIT：持仓减少
                if (posting.getShares() != null) {
                    holding.setTotalShares(holding.getTotalShares().subtract(posting.getShares()));
                }
                holding.setTotalCost(holding.getTotalCost().subtract(posting.getAmount()));
            }

            holdings.put(key, holding);
        }

        // 查询有关联产品的账户（通过 linked_product_id 和 initial_shares）
        // 这些账户的 initial_shares 应该作为该产品的持仓
        // 注意：只有在没有 POSITION 分录时才使用账户的 initial_shares，避免重复计算
        List<Account> allAccounts = accountMapper.selectByOwner(userId, familyId);
        for (Account account : allAccounts) {
            if (account.getLinkedProductId() != null && account.getInitialShares() != null 
                && account.getInitialShares().compareTo(BigDecimal.ZERO) > 0) {
                
                Long productId = account.getLinkedProductId();
                
                // 如果已经有 POSITION 分录的持仓数据，跳过账户的 initial_shares
                // 避免重复计算（POSITION 分录已经是完整的持仓记录）
                String key = "NULL:" + productId;
                if (holdings.containsKey(key) && holdings.get(key).getTotalShares().compareTo(BigDecimal.ZERO) > 0) {
                    continue;
                }
                
                // 创建或获取持仓信息
                HoldingInfo holding = holdings.get(key);
                if (holding == null) {
                    holding = new HoldingInfo();
                    holding.setProductId(productId);
                    holding.setBrokerAccountId(null);
                    holding.setBrokerAccountName(null);
                    holding.setTotalShares(BigDecimal.ZERO);
                    holding.setTotalCost(BigDecimal.ZERO);
                }
                
                // 将账户的 initial_shares 加入持仓
                holding.setTotalShares(holding.getTotalShares().add(account.getInitialShares()));
                
                // 关联账户产品（如稳利宝）当前金额应按持仓份额 * 最新净值计算。
                // 子账户 balance 是资金信封/分配口径，不能作为该产品总持仓成本或市值口径。
                BigDecimal cost = BigDecimal.ZERO;
                Nav latestNav = navMapper.selectLatest(productId);
                if (latestNav != null && latestNav.getNav() != null) {
                    cost = account.getInitialShares().multiply(latestNav.getNav()).setScale(2, RoundingMode.HALF_UP);
                } else if (account.getBalance() != null && account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                    cost = account.getBalance();
                }
                holding.setTotalCost(holding.getTotalCost().add(cost));
                
                holdings.put(key, holding);
            }
        }

        // 2) 将“买入/申购”相关手续费计入持仓成本（与券商/同花顺口径对齐）
        //
        // 关键点：
        // - 手续费应当计入“买入成本”，影响平均成本与未实现盈亏
        // - 但“卖出手续费”等已实现费用不应计入剩余持仓成本，否则会导致成本被抬高（典型现象：成本偏大）
        //
        // 因此这里仅将 txn_type=BUY/SUBSCRIPTION 的 FEE 分录加回成本。
        List<LedgerPosting> feePostings = ledgerPostingMapper
            .selectByAccountTypeAndOwner("FEE", userId, familyId);
        for (LedgerPosting feePosting : feePostings) {
            LedgerTxn feeTxn = ledgerTxnMapper.selectByTxnId(feePosting.getTxnId());
            if (feeTxn == null || feeTxn.getProductId() == null) {
                continue;
            }
            // 只统计买入/申购的手续费
            if (!"BUY".equals(feeTxn.getTxnType()) && !"SUBSCRIPTION".equals(feeTxn.getTxnType())) {
                continue;
            }
            Long productId = feeTxn.getProductId();
            // 优先从同一 txn 的 POSITION DEBIT 分录上推断券商维度（新逻辑下可稳定推断）
            Long brokerAccountId = null;
            String brokerAccountName = null;
            List<LedgerPosting> txnPostings = ledgerPostingMapper.selectByTxnId(feePosting.getTxnId());
            if (txnPostings != null) {
                for (LedgerPosting p : txnPostings) {
                    if (!"POSITION".equals(p.getAccountType()) || !"DEBIT".equals(p.getPostingType())) {
                        continue;
                    }
                    Account posAcc = accountMapper.selectById(p.getAccountId());
                    if (posAcc != null && posAcc.getParentAccountId() != null) {
                        Account parent = accountMapper.selectById(posAcc.getParentAccountId());
                        if (parent != null && "BROKER".equals(parent.getAccountType()) && parent.getParentAccountId() == null) {
                            brokerAccountId = parent.getId();
                            brokerAccountName = parent.getAccountName();
                        }
                    }
                    break;
                }
            }
            String key = (brokerAccountId != null ? brokerAccountId.toString() : "NULL") + ":" + productId;
            HoldingInfo holding = holdings.get(key);
            if (holding == null) continue;
            if (holding.getTotalShares() == null || holding.getTotalShares().compareTo(BigDecimal.ZERO) <= 0) continue;
            if (holding.getTotalCost() == null) holding.setTotalCost(BigDecimal.ZERO);
            holding.setTotalCost(holding.getTotalCost().add(feePosting.getAmount()));
            holding.setBrokerAccountId(brokerAccountId);
            holding.setBrokerAccountName(brokerAccountName);
        }

        // 3. 计算平均成本、市值和未实现盈亏
        for (HoldingInfo holding : holdings.values()) {
            Long productId = holding.getProductId();
            
            // 回填产品信息，供前端展示/行情查询使用
            if (holding.getProductId() == null) {
                holding.setProductId(productId);
            }
            ProductMaster product = productMasterMapper.selectById(productId);
            if (product != null) {
                holding.setProductCode(product.getProductCode());
                holding.setProductName(product.getProductName());
                holding.setChannel(product.getChannel());
                holding.setAssetType(product.getAssetType());
            }
            
            // 计算平均成本
            if (holding.getTotalShares() != null && 
                holding.getTotalShares().compareTo(BigDecimal.ZERO) > 0 &&
                holding.getTotalCost() != null) {
                BigDecimal avgCost = holding.getTotalCost().divide(
                    holding.getTotalShares(), 6, java.math.RoundingMode.HALF_UP);
                holding.setAvgCost(avgCost);
            } else {
                holding.setAvgCost(BigDecimal.ZERO);
            }

            // 清仓归零：摊薄成本法在“全部卖出且盈利”时可能出现 totalCost < 0。
            // 对于剩余份额 <= 0 的情况，持仓应视为已清仓，剩余成本/浮盈亏归零，避免前端显示负成本/负余额。
            if (holding.getTotalShares() == null || holding.getTotalShares().compareTo(BigDecimal.ZERO) <= 0) {
                holding.setTotalShares(BigDecimal.ZERO);
                holding.setTotalCost(BigDecimal.ZERO);
                holding.setAvgCost(BigDecimal.ZERO);
                holding.setMarketValue(BigDecimal.ZERO);
                holding.setUnrealizedPnl(BigDecimal.ZERO);
                continue;
            }
            
            // 获取最新净值计算市值和未实现盈亏
            Nav latestNav = navMapper.selectLatest(productId);
            if (latestNav != null && latestNav.getNav() != null 
                && holding.getTotalShares() != null 
                && holding.getTotalShares().compareTo(BigDecimal.ZERO) > 0) {
                
                BigDecimal marketValue = holding.getTotalShares().multiply(latestNav.getNav())
                    .setScale(2, RoundingMode.HALF_UP);
                holding.setMarketValue(marketValue);
                
                // 未实现盈亏 = 市值 - 成本
                if (holding.getTotalCost() != null) {
                    BigDecimal unrealizedPnl = marketValue.subtract(holding.getTotalCost())
                        .setScale(2, RoundingMode.HALF_UP);
                    holding.setUnrealizedPnl(unrealizedPnl);
                } else {
                    holding.setUnrealizedPnl(BigDecimal.ZERO);
                }
            } else {
                holding.setMarketValue(BigDecimal.ZERO);
                holding.setUnrealizedPnl(BigDecimal.ZERO);
            }
        }

        return new ArrayList<>(holdings.values());
    }

    /**
     * 获取指定产品的持仓信息
     * 
     * @param productId 产品ID
     * @param userId 用户ID
     * @param familyId 家庭ID，可为空
     * @return 持仓信息，如果不存在则返回null
     */
    /**
     * 业务注释规范化: 查询 getHolding 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @param userId 所属用户 ID，用于限定个人数据权限和查询范围。
     * @param familyId 所属家庭 ID，用于家庭视角下的数据隔离。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public HoldingInfo getHolding(Long productId, Long userId, Long familyId) {
        List<HoldingInfo> holdings = calculateHoldings(userId, familyId);
        return holdings.stream().filter(h -> h != null && productId.equals(h.getProductId())).findFirst().orElse(null);
    }

    public static class HoldingInfo {
        /**
         * 业务注释规范化: brokerAccountId 关联 ID，用于连接对应业务对象并保持数据引用关系。
         */
        private Long brokerAccountId;
        /**
         * 业务注释规范化: brokerAccountName 业务字段，承载该对象在后端流程中的核心属性。
         */
        private String brokerAccountName;
        /**
         * 业务注释规范化: 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
         */
        private Long productId;
        /**
         * 业务注释规范化: productCode 业务字段，承载该对象在后端流程中的核心属性。
         */
        private String productCode;
        /**
         * 业务注释规范化: productName 业务字段，承载该对象在后端流程中的核心属性。
         */
        private String productName;
        /**
         * 业务注释规范化: channel 业务字段，承载该对象在后端流程中的核心属性。
         */
        private String channel;
        /**
         * 业务注释规范化: assetType 类型字段，用于区分不同业务分类并驱动处理分支。
         */
        private String assetType;
        /**
         * 业务注释规范化: totalShares 业务字段，承载该对象在后端流程中的核心属性。
         */
        private BigDecimal totalShares;
        /**
         * 业务注释规范化: totalCost 金额字段，用于表达该场景下的资金规模或费用口径。
         */
        private BigDecimal totalCost;
        /**
         * 业务注释规范化: avgCost 金额字段，用于表达该场景下的资金规模或费用口径。
         */
        private BigDecimal avgCost;
        /**
         * 业务注释规范化: marketValue 业务字段，承载该对象在后端流程中的核心属性。
         */
        private BigDecimal marketValue;
        /**
         * 业务注释规范化: unrealizedPnl 业务字段，承载该对象在后端流程中的核心属性。
         */
        private BigDecimal unrealizedPnl;

        // Getters and setters
        /**
         * 业务注释规范化: 查询 getBrokerAccountId 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public Long getBrokerAccountId() { return brokerAccountId; }
        /**
         * 业务注释规范化: 处理 setBrokerAccountId 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param brokerAccountId brokerAccountId 关联 ID，用于连接对应业务对象并保持数据引用关系。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setBrokerAccountId(Long brokerAccountId) { this.brokerAccountId = brokerAccountId; }
        /**
         * 业务注释规范化: 查询 getBrokerAccountName 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public String getBrokerAccountName() { return brokerAccountName; }
        /**
         * 业务注释规范化: 处理 setBrokerAccountName 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param brokerAccountName brokerAccountName 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setBrokerAccountName(String brokerAccountName) { this.brokerAccountName = brokerAccountName; }
        /**
         * 业务注释规范化: 查询 getProductId 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public Long getProductId() { return productId; }
        /**
         * 业务注释规范化: 处理 setProductId 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setProductId(Long productId) { this.productId = productId; }
        /**
         * 业务注释规范化: 查询 getProductCode 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public String getProductCode() { return productCode; }
        /**
         * 业务注释规范化: 处理 setProductCode 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param productCode productCode 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setProductCode(String productCode) { this.productCode = productCode; }
        /**
         * 业务注释规范化: 查询 getProductName 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public String getProductName() { return productName; }
        /**
         * 业务注释规范化: 处理 setProductName 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param productName productName 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setProductName(String productName) { this.productName = productName; }
        /**
         * 业务注释规范化: 查询 getChannel 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public String getChannel() { return channel; }
        /**
         * 业务注释规范化: 处理 setChannel 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param channel channel 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setChannel(String channel) { this.channel = channel; }
        /**
         * 业务注释规范化: 查询 getAssetType 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public String getAssetType() { return assetType; }
        /**
         * 业务注释规范化: 处理 setAssetType 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param assetType assetType 类型字段，用于区分不同业务分类并驱动处理分支。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setAssetType(String assetType) { this.assetType = assetType; }
        /**
         * 业务注释规范化: 查询 getTotalShares 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public BigDecimal getTotalShares() { return totalShares; }
        /**
         * 业务注释规范化: 处理 setTotalShares 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param totalShares totalShares 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setTotalShares(BigDecimal totalShares) { this.totalShares = totalShares; }
        /**
         * 业务注释规范化: 查询 getTotalCost 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public BigDecimal getTotalCost() { return totalCost; }
        /**
         * 业务注释规范化: 处理 setTotalCost 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param totalCost totalCost 金额字段，用于表达该场景下的资金规模或费用口径。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
        /**
         * 业务注释规范化: 查询 getAvgCost 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public BigDecimal getAvgCost() { return avgCost; }
        /**
         * 业务注释规范化: 处理 setAvgCost 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param avgCost avgCost 金额字段，用于表达该场景下的资金规模或费用口径。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setAvgCost(BigDecimal avgCost) { this.avgCost = avgCost; }
        /**
         * 业务注释规范化: 查询 getMarketValue 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public BigDecimal getMarketValue() { return marketValue; }
        /**
         * 业务注释规范化: 处理 setMarketValue 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param marketValue marketValue 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setMarketValue(BigDecimal marketValue) { this.marketValue = marketValue; }
        /**
         * 业务注释规范化: 查询 getUnrealizedPnl 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public BigDecimal getUnrealizedPnl() { return unrealizedPnl; }
        /**
         * 业务注释规范化: 处理 setUnrealizedPnl 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param unrealizedPnl unrealizedPnl 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setUnrealizedPnl(BigDecimal unrealizedPnl) { this.unrealizedPnl = unrealizedPnl; }
    }

    /**
     * 初始持仓导入DTO
     */
    public static class InitialHoldingImport {
        /**
         * 业务注释规范化: productCode 业务字段，承载该对象在后端流程中的核心属性。
         */
        private String productCode;
        /**
         * 业务注释规范化: productName 业务字段，承载该对象在后端流程中的核心属性。
         */
        private String productName;
        private String channel; // EXCHANGE or OTC
        /**
         * 业务注释规范化: 产品份额，适用于基金、ETF、货币基金等按份额管理的资产。
         */
        private BigDecimal shares;
        /**
         * 业务注释规范化: costPrice 金额字段，用于表达该场景下的资金规模或费用口径。
         */
        private BigDecimal costPrice;
        /**
         * 业务注释规范化: note 业务字段，承载该对象在后端流程中的核心属性。
         */
        private String note;

        // Getters and setters
        /**
         * 业务注释规范化: 查询 getProductCode 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public String getProductCode() { return productCode; }
        /**
         * 业务注释规范化: 处理 setProductCode 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param productCode productCode 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setProductCode(String productCode) { this.productCode = productCode; }
        /**
         * 业务注释规范化: 查询 getProductName 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public String getProductName() { return productName; }
        /**
         * 业务注释规范化: 处理 setProductName 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param productName productName 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setProductName(String productName) { this.productName = productName; }
        /**
         * 业务注释规范化: 查询 getChannel 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public String getChannel() { return channel; }
        /**
         * 业务注释规范化: 处理 setChannel 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param channel channel 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setChannel(String channel) { this.channel = channel; }
        /**
         * 业务注释规范化: 查询 getShares 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public BigDecimal getShares() { return shares; }
        /**
         * 业务注释规范化: 处理 setShares 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param shares 产品份额，适用于基金、ETF、货币基金等按份额管理的资产。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setShares(BigDecimal shares) { this.shares = shares; }
        /**
         * 业务注释规范化: 查询 getCostPrice 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public BigDecimal getCostPrice() { return costPrice; }
        /**
         * 业务注释规范化: 处理 setCostPrice 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param costPrice costPrice 金额字段，用于表达该场景下的资金规模或费用口径。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
        /**
         * 业务注释规范化: 查询 getNote 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public String getNote() { return note; }
        /**
         * 业务注释规范化: 处理 setNote 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param note note 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setNote(String note) { this.note = note; }
    }

    /**
     * 导入初始持仓
     * 
     * 流程说明：
     * 1. 对每个持仓记录，查找或创建产品（ProductMaster）
     * 2. 获取或创建对应的POSITION账户
     * 3. 创建ADJUST类型的交易，生成POSITION DEBIT分录
     * 
     * 注意：
     * - 如果产品已存在持仓，导入会累加到现有持仓上
     * - 使用ADJUST类型交易，表示这是初始余额调整
     * - 持仓成本 = shares × costPrice
     * 
     * @param userId 用户ID
     * @param familyId 家庭ID，可为空
     * @param holdings 初始持仓列表
     */
    @Transactional
    /**
     * 业务注释规范化: 处理 importInitialHoldings 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param userId 所属用户 ID，用于限定个人数据权限和查询范围。
     * @param familyId 所属家庭 ID，用于家庭视角下的数据隔离。
     * @param holdings holdings 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void importInitialHoldings(Long userId, Long familyId, List<InitialHoldingImport> holdings) {
        for (InitialHoldingImport holding : holdings) {
            // 1. 查找或创建产品
            ProductMaster product = findOrCreateProduct(holding);
            
            // 2. 获取或创建POSITION账户
            String ownerType = familyId != null ? "FAMILY" : "PERSONAL";
            Account positionAccount = accountService.getOrCreatePositionAccount(
                product.getId(),
                product.getProductName(),
                ownerType,
                userId,
                familyId
            );
            
            // 3. 计算持仓成本
            BigDecimal totalCost = holding.getShares().multiply(holding.getCostPrice());
            
            // 4. 创建ADJUST类型的交易和流水
            // 对于POSITION账户，DEBIT表示持仓增加
            // 需要创建平衡的分录：POSITION DEBIT + INCOME CREDIT（表示初始余额调整）
            
            // 获取或创建"初始余额调整"收入账户（用于平衡初始持仓导入）
            Account adjustIncomeAccount = accountService.getOrCreateVirtualAccount(
                "INCOME", "INCOME", ownerType, userId, familyId, null, "初始余额调整"
            );
            
            LedgerPosting positionPosting = new LedgerPosting();
            positionPosting.setAccountId(positionAccount.getId());
            positionPosting.setAccountType("POSITION");
            positionPosting.setPostingType("DEBIT");
            positionPosting.setAmount(totalCost);
            positionPosting.setShares(holding.getShares());
            positionPosting.setCurrency("CNY");
            positionPosting.setNote(holding.getNote() != null ? holding.getNote() : "初始持仓导入");
            
            LedgerPosting incomePosting = new LedgerPosting();
            incomePosting.setAccountId(adjustIncomeAccount.getId());
            incomePosting.setAccountType("INCOME");
            incomePosting.setPostingType("CREDIT");
            incomePosting.setAmount(totalCost);
            incomePosting.setCurrency("CNY");
            incomePosting.setNote("初始持仓导入平衡分录");
            
            String note = String.format("初始持仓导入：%s %s份，成本价%s", 
                product.getProductName(), holding.getShares(), holding.getCostPrice());
            if (holding.getNote() != null && !holding.getNote().isEmpty()) {
                note += "，" + holding.getNote();
            }
            
            LedgerTxn txn = ledgerService.createTransaction(
                userId,
                familyId,
                "ADJUST",
                null,
                List.of(positionPosting, incomePosting),
                note
            );
            
            // 设置产品ID（用于持仓计算）
            txn.setProductId(product.getId());
            ledgerTxnMapper.update(txn);
        }
    }

    /**
     * 查找或创建产品
     * 
     * @param holding 初始持仓信息
     * @return 产品实体
     */
    /**
     * 业务注释规范化: 查找 findOrCreateProduct 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该私有方法封装局部复杂逻辑，用于保持统计、展示或校验口径一致。</p>
     * @param holding holding 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    private ProductMaster findOrCreateProduct(InitialHoldingImport holding) {
        // 先尝试查找产品（只按产品代码查找，不限制市场，避免重复创建）
        ProductMaster product = productMasterMapper.selectByCodeOnly(holding.getProductCode());
        
        if (product != null) {
            return product;
        }
        
        // 如果产品不存在，创建新产品
        // 根据产品代码判断市场（深圳：0/1/2/3开头，上海：5/6/9开头）
        String market;
        if ("EXCHANGE".equals(holding.getChannel())) {
            String firstChar = holding.getProductCode().substring(0, 1);
            if ("0123".contains(firstChar)) {
                market = "SZ";
            } else {
                market = "SH";
            }
        } else {
            market = "NA";
        }
        
        product = new ProductMaster();
        product.setProductCode(holding.getProductCode());
        product.setProductName(holding.getProductName());
        product.setChannel(holding.getChannel());
        product.setMarket(market);
        
        // 根据渠道推断资产类型
        if ("EXCHANGE".equals(holding.getChannel())) {
            // 场内产品，默认为ETF
            product.setAssetType("ETF");
        } else {
            // 场外产品，默认为FUND
            product.setAssetType("FUND");
        }
        
        product.setCurrency("CNY");
        product.setIsQdii(false);
        product.setBuyFeeRate(BigDecimal.ZERO);
        product.setSellFeeRate(BigDecimal.ZERO);
        product.setBuyConfirmOffset(1);
        product.setSellConfirmOffset(1);
        product.setCutoffTime("15:00");
        product.setDataSource("MANUAL");
        product.setIsActive(true);
        product.setNote("初始持仓导入时自动创建");
        
        productService.createProduct(product);
        return product;
    }

    /**
     * 账户持仓信息 DTO
     */
    public static class AccountHoldingInfo {
        /**
         * 业务注释规范化: 关联账户 ID，指向承载资金、持仓或虚拟科目的账户。
         */
        private Long accountId;
        /**
         * 业务注释规范化: accountName 业务字段，承载该对象在后端流程中的核心属性。
         */
        private String accountName;
        /**
         * 业务注释规范化: parentAccountName 业务字段，承载该对象在后端流程中的核心属性。
         */
        private String parentAccountName;
        /**
         * 业务注释规范化: 产品份额，适用于基金、ETF、货币基金等按份额管理的资产。
         */
        private BigDecimal shares;
        /**
         * 业务注释规范化: marketValue 业务字段，承载该对象在后端流程中的核心属性。
         */
        private BigDecimal marketValue;

        // Getters and setters
        /**
         * 业务注释规范化: 查询 getAccountId 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public Long getAccountId() { return accountId; }
        /**
         * 业务注释规范化: 处理 setAccountId 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param accountId 关联账户 ID，指向承载资金、持仓或虚拟科目的账户。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setAccountId(Long accountId) { this.accountId = accountId; }
        /**
         * 业务注释规范化: 查询 getAccountName 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public String getAccountName() { return accountName; }
        /**
         * 业务注释规范化: 处理 setAccountName 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param accountName accountName 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setAccountName(String accountName) { this.accountName = accountName; }
        /**
         * 业务注释规范化: 查询 getParentAccountName 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public String getParentAccountName() { return parentAccountName; }
        /**
         * 业务注释规范化: 处理 setParentAccountName 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param parentAccountName parentAccountName 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setParentAccountName(String parentAccountName) { this.parentAccountName = parentAccountName; }
        /**
         * 业务注释规范化: 查询 getShares 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public BigDecimal getShares() { return shares; }
        /**
         * 业务注释规范化: 处理 setShares 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param shares 产品份额，适用于基金、ETF、货币基金等按份额管理的资产。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setShares(BigDecimal shares) { this.shares = shares; }
        /**
         * 业务注释规范化: 查询 getMarketValue 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public BigDecimal getMarketValue() { return marketValue; }
        /**
         * 业务注释规范化: 处理 setMarketValue 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param marketValue marketValue 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setMarketValue(BigDecimal marketValue) { this.marketValue = marketValue; }
    }

    /**
     * 获取指定产品在各账户的持仓明细
     * 
     * 用于关联账户产品的赎回来源选择
     * 
     * 计算逻辑：
     * 1. 首先检查是否有账户通过 linked_product_id 关联了该产品
     * 2. 如果有关联账户，则根据子账户的金额按比例分配父账户的 initial_shares
     * 3. 如果没有关联账户，则通过分析已确认订单的资金来源来计算
     * 
     * @param productId 产品ID
     * @param userId 用户ID
     * @param familyId 家庭ID，可为空
     * @return 账户持仓明细列表
     */
    /**
     * 业务注释规范化: 查询 getProductHoldingsByAccount 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @param userId 所属用户 ID，用于限定个人数据权限和查询范围。
     * @param familyId 所属家庭 ID，用于家庭视角下的数据隔离。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public List<AccountHoldingInfo> getProductHoldingsByAccount(Long productId, Long userId, Long familyId) {
        List<AccountHoldingInfo> result = new ArrayList<>();
        
        // 获取产品最新净值用于计算市值
        BigDecimal latestNav = BigDecimal.ONE; // 默认净值为1
        Nav latestNavRecord = navMapper.selectLatest(productId);
        if (latestNavRecord != null && latestNavRecord.getNav() != null) {
            latestNav = latestNavRecord.getNav();
        }
        
        // 首先通过 POSITION 分录计算持仓（这是最准确的方式）
        // 查询该产品的所有 POSITION 类型分录
        List<LedgerPosting> positionPostings = ledgerPostingMapper.selectByAccountTypeAndOwner("POSITION", userId, familyId);
        Map<Long, BigDecimal> positionAccountShares = new HashMap<>();
        
        for (LedgerPosting posting : positionPostings) {
            // 通过 ledger_txn 获取 productId
            LedgerTxn txn = ledgerTxnMapper.selectByTxnId(posting.getTxnId());
            if (txn == null || txn.getProductId() == null || !txn.getProductId().equals(productId)) {
                continue; // 跳过不属于该产品的交易
            }
            
            Long accountId = posting.getAccountId();
            BigDecimal shares = posting.getShares();
            if (shares == null) {
                shares = BigDecimal.ZERO;
            }
            
            // 根据 DEBIT/CREDIT 计算份额变化
            if ("DEBIT".equals(posting.getPostingType())) {
                positionAccountShares.merge(accountId, shares, BigDecimal::add);
            } else if ("CREDIT".equals(posting.getPostingType())) {
                positionAccountShares.merge(accountId, shares.negate(), BigDecimal::add);
            }
        }
        
        // 转换 POSITION 账户持仓为结果
        for (Map.Entry<Long, BigDecimal> entry : positionAccountShares.entrySet()) {
            BigDecimal shares = entry.getValue();
            if (shares == null || shares.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            
            Account account = accountMapper.selectById(entry.getKey());
            if (account == null) {
                continue;
            }
            
            AccountHoldingInfo info = new AccountHoldingInfo();
            info.setAccountId(entry.getKey());
            info.setAccountName(account.getAccountName());
            
            if (account.getParentAccountId() != null) {
                Account parent = accountMapper.selectById(account.getParentAccountId());
                if (parent != null) {
                    info.setParentAccountName(parent.getAccountName());
                }
            }
            
            info.setShares(shares.setScale(4, RoundingMode.HALF_UP));
            info.setMarketValue(shares.multiply(latestNav).setScale(2, RoundingMode.HALF_UP));
            result.add(info);
        }
        
        // 如果通过 POSITION 分录找到了持仓，直接返回
        if (!result.isEmpty()) {
            return result;
        }
        
        // 如果没有 POSITION 分录，检查关联账户
        List<Account> linkedAccounts = accountMapper.selectByLinkedProduct(productId, userId, familyId);
        
        if (!linkedAccounts.isEmpty()) {
            // 有关联账户，根据子账户金额按比例计算份额
            for (Account linkedAccount : linkedAccounts) {
                BigDecimal totalShares = linkedAccount.getInitialShares();
                
                // 获取子账户
                List<Account> children = accountMapper.selectChildren(linkedAccount.getId());
                
                if (totalShares == null || totalShares.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                
                BigDecimal totalMarketValue = totalShares.multiply(latestNav).setScale(2, RoundingMode.HALF_UP);
                
                if (children.isEmpty()) {
                    // 没有子账户，整个账户就是持仓
                    AccountHoldingInfo info = new AccountHoldingInfo();
                    info.setAccountId(linkedAccount.getId());
                    info.setAccountName(linkedAccount.getAccountName());
                    info.setShares(totalShares.setScale(4, RoundingMode.HALF_UP));
                    info.setMarketValue(totalShares.multiply(latestNav).setScale(2, RoundingMode.HALF_UP));
                    result.add(info);
                } else {
                    BigDecimal allocatedAmount = BigDecimal.ZERO;
                    for (Account child : children) {
                        if (isUnallocatedAccount(child)) {
                            continue;
                        }
                        if (Boolean.TRUE.equals(child.getIsFixedAmount()) && child.getFixedAmount() != null) {
                            allocatedAmount = allocatedAmount.add(child.getFixedAmount());
                        } else if (child.getBalance() != null && child.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                            allocatedAmount = allocatedAmount.add(child.getBalance());
                        }
                    }

                    // 有子账户，根据子账户金额分配份额；待分配账户使用“总市值 - 已分配金额”
                    for (Account child : children) {
                        BigDecimal childAmount = BigDecimal.ZERO;
                        if (isUnallocatedAccount(child)) {
                            childAmount = totalMarketValue.subtract(allocatedAmount).max(BigDecimal.ZERO);
                        } else if (Boolean.TRUE.equals(child.getIsFixedAmount()) && child.getFixedAmount() != null) {
                            childAmount = child.getFixedAmount();
                        } else if (child.getBalance() != null && child.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                            childAmount = child.getBalance();
                        }

                        if (childAmount.compareTo(BigDecimal.ZERO) <= 0) {
                            continue;
                        }

                        BigDecimal childShares;
                        if (latestNav.compareTo(BigDecimal.ZERO) > 0) {
                            childShares = childAmount.divide(latestNav, 10, RoundingMode.HALF_UP);
                        } else {
                            childShares = BigDecimal.ZERO;
                        }
                        
                        if (childShares.compareTo(BigDecimal.ZERO) <= 0) {
                            continue;
                        }
                        
                        AccountHoldingInfo info = new AccountHoldingInfo();
                        info.setAccountId(child.getId());
                        info.setAccountName(child.getAccountName());
                        info.setParentAccountName(linkedAccount.getAccountName());
                        info.setShares(childShares.setScale(4, RoundingMode.HALF_UP));
                        info.setMarketValue(childAmount.setScale(2, RoundingMode.HALF_UP));
                        result.add(info);
                    }
                }
            }
            
            return result;
        }
        
        // 没有关联账户，通过订单计算
        List<Order> confirmedOrders = orderMapper.selectConfirmedByProductId(productId, userId);
        Map<Long, BigDecimal> accountShares = new HashMap<>();
        
        for (Order order : confirmedOrders) {
            List<OrderFundingLine> fundingLines = orderFundingLineMapper.selectByOrderId(order.getOrderId());
            SettlementConfirm settlement = settlementConfirmMapper.selectByOrderId(order.getOrderId());
            
            if (fundingLines.isEmpty() || settlement == null) {
                continue;
            }
            
            if ("BUY".equals(order.getOrderType()) || "SUBSCRIPTION".equals(order.getOrderType())) {
                BigDecimal totalAmount = fundingLines.stream()
                    .map(OrderFundingLine::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalSharesFromOrder = settlement.getConfirmShares();
                
                if (totalAmount.compareTo(BigDecimal.ZERO) > 0 && totalSharesFromOrder != null && totalSharesFromOrder.compareTo(BigDecimal.ZERO) > 0) {
                    for (OrderFundingLine fl : fundingLines) {
                        BigDecimal accountSharesAlloc = fl.getAmount()
                            .divide(totalAmount, 10, RoundingMode.HALF_UP)
                            .multiply(totalSharesFromOrder);
                        accountShares.merge(fl.getAccountId(), accountSharesAlloc, BigDecimal::add);
                    }
                }
            } else if ("SELL".equals(order.getOrderType()) || "REDEMPTION".equals(order.getOrderType())) {
                BigDecimal totalSharesFromOrder = settlement.getConfirmShares();
                boolean hasExplicitShares = fundingLines.stream().anyMatch(fl -> fl.getShares() != null && fl.getShares().compareTo(BigDecimal.ZERO) > 0);
                
                if (hasExplicitShares) {
                    for (OrderFundingLine fl : fundingLines) {
                        if (fl.getShares() != null && fl.getShares().compareTo(BigDecimal.ZERO) > 0) {
                            accountShares.merge(fl.getAccountId(), fl.getShares().negate(), BigDecimal::add);
                        }
                    }
                } else if (totalSharesFromOrder != null && totalSharesFromOrder.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal totalAmount = fundingLines.stream()
                        .map(OrderFundingLine::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                    
                    if (totalAmount.compareTo(BigDecimal.ZERO) > 0) {
                        for (OrderFundingLine fl : fundingLines) {
                            BigDecimal accountSharesDeduct = fl.getAmount()
                                .divide(totalAmount, 10, RoundingMode.HALF_UP)
                                .multiply(totalSharesFromOrder)
                                .negate();
                            accountShares.merge(fl.getAccountId(), accountSharesDeduct, BigDecimal::add);
                        }
                    }
                }
            }
        }
        
        // 转换为结果列表
        for (Map.Entry<Long, BigDecimal> entry : accountShares.entrySet()) {
            BigDecimal shares = entry.getValue();
            if (shares == null || shares.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            
            Account account = accountService.getAccount(entry.getKey());
            if (account == null) {
                continue;
            }
            
            AccountHoldingInfo info = new AccountHoldingInfo();
            info.setAccountId(entry.getKey());
            info.setAccountName(account.getAccountName());
            
            if (account.getParentAccountId() != null) {
                Account parent = accountService.getAccount(account.getParentAccountId());
                if (parent != null) {
                    info.setParentAccountName(parent.getAccountName());
                }
            }
            
            info.setShares(shares.setScale(4, RoundingMode.HALF_UP));
            info.setMarketValue(shares.multiply(latestNav).setScale(2, RoundingMode.HALF_UP));
            result.add(info);
        }
        
        return result;
    }

    /**
     * 业务注释规范化: 判断 isUnallocatedAccount 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该私有方法封装局部复杂逻辑，用于保持统计、展示或校验口径一致。</p>
     * @param account account 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    private boolean isUnallocatedAccount(Account account) {
        String name = account != null && account.getAccountName() != null ? account.getAccountName() : "";
        String code = account != null && account.getAccountCode() != null ? account.getAccountCode() : "";
        return name.contains("待分配") || code.contains("待分配");
    }
}

