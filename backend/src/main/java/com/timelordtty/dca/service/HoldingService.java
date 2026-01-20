package com.timelordtty.dca.service;

import com.timelordtty.dca.mapper.LedgerPostingMapper;
import com.timelordtty.dca.mapper.LedgerTxnMapper;
import com.timelordtty.dca.mapper.ProductMasterMapper;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.LedgerPosting;
import com.timelordtty.dca.model.LedgerTxn;
import com.timelordtty.dca.model.ProductMaster;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
public class HoldingService {

    private final LedgerPostingMapper ledgerPostingMapper;
    private final LedgerTxnMapper ledgerTxnMapper;
    private final ProductMasterMapper productMasterMapper;
    private final ProductService productService;
    private final AccountService accountService;
    private final LedgerService ledgerService;

    public HoldingService(LedgerPostingMapper ledgerPostingMapper, LedgerTxnMapper ledgerTxnMapper,
                          ProductMasterMapper productMasterMapper,
                          ProductService productService, AccountService accountService, LedgerService ledgerService) {
        this.ledgerPostingMapper = ledgerPostingMapper;
        this.ledgerTxnMapper = ledgerTxnMapper;
        this.productMasterMapper = productMasterMapper;
        this.productService = productService;
        this.accountService = accountService;
        this.ledgerService = ledgerService;
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
     * @return 持仓信息Map，key为productId，value为HoldingInfo
     */
    public Map<Long, HoldingInfo> calculateHoldings(Long userId, Long familyId) {
        // 直接查询所有POSITION类型的分录（属于该用户或家庭的）
        // 这样更直接，不需要先查账户
        List<LedgerPosting> postings = ledgerPostingMapper.selectByAccountTypeAndOwner("POSITION", userId, familyId);

        Map<Long, HoldingInfo> holdings = new HashMap<>();

        for (LedgerPosting posting : postings) {
            // 通过ledger_txn获取productId
            LedgerTxn txn = ledgerTxnMapper.selectByTxnId(posting.getTxnId());
            if (txn == null || txn.getProductId() == null) {
                continue; // 跳过没有productId的交易
            }

            Long productId = txn.getProductId();

            // 创建或获取持仓信息
            HoldingInfo holding = holdings.get(productId);
            if (holding == null) {
                holding = new HoldingInfo();
                holding.setProductId(productId);
            }
            if (holding.getTotalShares() == null) {
                holding.setTotalShares(BigDecimal.ZERO);
            }
            if (holding.getTotalCost() == null) {
                holding.setTotalCost(BigDecimal.ZERO);
            }

            // 计算份额和成本
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

            holdings.put(productId, holding);
        }

        // 计算平均成本
        for (Map.Entry<Long, HoldingInfo> entry : holdings.entrySet()) {
            HoldingInfo holding = entry.getValue();
            // 回填产品信息，供前端展示/行情查询使用
            if (holding.getProductId() == null) {
                holding.setProductId(entry.getKey());
            }
            ProductMaster product = productMasterMapper.selectById(entry.getKey());
            if (product != null) {
                holding.setProductCode(product.getProductCode());
                holding.setProductName(product.getProductName());
                holding.setChannel(product.getChannel());
                holding.setAssetType(product.getAssetType());
            }
            if (holding.getTotalShares() != null && 
                holding.getTotalShares().compareTo(BigDecimal.ZERO) > 0 &&
                holding.getTotalCost() != null) {
                BigDecimal avgCost = holding.getTotalCost().divide(
                    holding.getTotalShares(), 6, java.math.RoundingMode.HALF_UP);
                holding.setAvgCost(avgCost);
            } else {
                holding.setAvgCost(BigDecimal.ZERO);
            }
            // marketValue和unrealizedPnl需要外部行情数据，本服务不计算
            holding.setMarketValue(BigDecimal.ZERO);
            holding.setUnrealizedPnl(BigDecimal.ZERO);
        }

        return holdings;
    }

    public static class HoldingInfo {
        private Long productId;
        private String productCode;
        private String productName;
        private String channel;
        private String assetType;
        private BigDecimal totalShares;
        private BigDecimal totalCost;
        private BigDecimal avgCost;
        private BigDecimal marketValue;
        private BigDecimal unrealizedPnl;

        // Getters and setters
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public String getProductCode() { return productCode; }
        public void setProductCode(String productCode) { this.productCode = productCode; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
        public String getAssetType() { return assetType; }
        public void setAssetType(String assetType) { this.assetType = assetType; }
        public BigDecimal getTotalShares() { return totalShares; }
        public void setTotalShares(BigDecimal totalShares) { this.totalShares = totalShares; }
        public BigDecimal getTotalCost() { return totalCost; }
        public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
        public BigDecimal getAvgCost() { return avgCost; }
        public void setAvgCost(BigDecimal avgCost) { this.avgCost = avgCost; }
        public BigDecimal getMarketValue() { return marketValue; }
        public void setMarketValue(BigDecimal marketValue) { this.marketValue = marketValue; }
        public BigDecimal getUnrealizedPnl() { return unrealizedPnl; }
        public void setUnrealizedPnl(BigDecimal unrealizedPnl) { this.unrealizedPnl = unrealizedPnl; }
    }

    /**
     * 初始持仓导入DTO
     */
    public static class InitialHoldingImport {
        private String productCode;
        private String productName;
        private String channel; // EXCHANGE or OTC
        private BigDecimal shares;
        private BigDecimal costPrice;
        private String note;

        // Getters and setters
        public String getProductCode() { return productCode; }
        public void setProductCode(String productCode) { this.productCode = productCode; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
        public BigDecimal getShares() { return shares; }
        public void setShares(BigDecimal shares) { this.shares = shares; }
        public BigDecimal getCostPrice() { return costPrice; }
        public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
        public String getNote() { return note; }
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
    private ProductMaster findOrCreateProduct(InitialHoldingImport holding) {
        // 先尝试查找产品（根据产品代码和渠道）
        String market = "EXCHANGE".equals(holding.getChannel()) ? "SH" : "NA"; // 简化处理，实际应该根据产品代码判断
        ProductMaster product = productMasterMapper.selectByCode(holding.getProductCode(), holding.getChannel(), market);
        
        if (product != null) {
            return product;
        }
        
        // 如果产品不存在，创建新产品
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
}

