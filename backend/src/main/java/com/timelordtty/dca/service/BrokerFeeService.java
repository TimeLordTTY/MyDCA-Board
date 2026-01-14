package com.timelordtty.dca.service;

import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.mapper.BrokerFeeConfigMapper;
import com.timelordtty.dca.mapper.FundSellFeeTierMapper;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.BrokerFeeConfig;
import com.timelordtty.dca.model.FundSellFeeTier;
import com.timelordtty.dca.model.ProductMaster;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 券商费率服务（BrokerFeeService）
 * 
 * 职责：查询和计算券商账户的费率配置
 * 
 * 费率计算逻辑：
 * 1. 优先从 broker_fee_config 表查询费率配置
 * 2. 如果找不到，使用产品表的默认费率
 * 3. 手续费 = max(交易金额 × 费率, 最低手续费)
 * 4. LOF场内申购需要结合产品表的申购费率，然后乘以折扣率
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@Service
public class BrokerFeeService {

    private final BrokerFeeConfigMapper brokerFeeConfigMapper;
    private final AccountMapper accountMapper;
    private final FundSellFeeTierMapper fundSellFeeTierMapper;

    public BrokerFeeService(BrokerFeeConfigMapper brokerFeeConfigMapper,
                           AccountMapper accountMapper,
                           FundSellFeeTierMapper fundSellFeeTierMapper) {
        this.brokerFeeConfigMapper = brokerFeeConfigMapper;
        this.accountMapper = accountMapper;
        this.fundSellFeeTierMapper = fundSellFeeTierMapper;
    }

    /**
     * 根据产品类型和券商账户，确定费率规则类型
     * 
     * @param product 产品对象
     * @param orderType 订单类型：BUY/SELL/SUBSCRIPTION/REDEMPTION
     * @return 费率规则类型
     */
    private String determineFeeRuleType(ProductMaster product, String orderType) {
        if (product == null) {
            return "DEFAULT";
        }

        String assetType = product.getAssetType();
        String channel = product.getChannel();
        String market = product.getMarket();

        // LOF场内申购特殊处理
        if ("LOF".equals(assetType) && "EXCHANGE".equals(channel) 
            && ("SUBSCRIPTION".equals(orderType) || "BUY".equals(orderType))) {
            return "LOF_SUBSCRIPTION";
        }

        // LOF场内交易
        if ("LOF".equals(assetType) && "EXCHANGE".equals(channel)) {
            return "LOF";
        }

        // ETF
        if ("ETF".equals(assetType)) {
            return "ETF";
        }

        // 股票
        if ("STOCK".equals(assetType)) {
            return "STOCK";
        }

        // 可转债
        if ("CONVERTIBLE_BOND".equals(assetType) || assetType != null && assetType.contains("BOND")) {
            if ("SH".equals(market)) {
                return "CONVERTIBLE_BOND_SH";
            } else if ("SZ".equals(market)) {
                return "CONVERTIBLE_BOND_SZ";
            }
        }

        // 逆回购
        if ("BOND_REPO".equals(assetType)) {
            return "BOND_REPO";
        }

        // 场外基金
        if ("OTC".equals(channel)) {
            return "FUND_OTC";
        }

        return "DEFAULT";
    }

    /**
     * 查询费率配置
     * 
     * @param accountId 券商账户ID
     * @param feeRuleType 费率规则类型
     * @return 费率配置对象，如果不存在则返回null
     */
    public BrokerFeeConfig getFeeConfig(Long accountId, String feeRuleType) {
        if (accountId == null || feeRuleType == null) {
            return null;
        }
        return brokerFeeConfigMapper.selectByAccountAndRuleType(accountId, feeRuleType);
    }

    /**
     * 计算买入/申购手续费
     * 
     * @param accountId 券商账户ID（用于查找费率配置）
     * @param product 产品对象
     * @param orderType 订单类型：BUY/SUBSCRIPTION
     * @param amount 交易金额
     * @return 手续费金额
     */
    public BigDecimal calculateBuyFee(Long accountId, ProductMaster product, String orderType, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // 特殊产品类型：银行理财净值型和货币基金费率为0
        if (product != null && ("BANK_WM_NAV".equals(product.getAssetType()) || "MMF".equals(product.getAssetType()))) {
            return BigDecimal.ZERO;
        }

        // 场内产品：使用券商费率配置
        if (product != null && "EXCHANGE".equals(product.getChannel())) {
            // 确定费率规则类型
            String feeRuleType = determineFeeRuleType(product, orderType);
            
            // 查询费率配置
            BrokerFeeConfig feeConfig = getFeeConfig(accountId, feeRuleType);
            
            BigDecimal feeRate;
            BigDecimal minFee;

            if (feeConfig != null) {
                // 使用券商费率配置
                if ("LOF_SUBSCRIPTION".equals(feeRuleType) && feeConfig.getSubscriptionDiscountRate() != null) {
                    // LOF场内申购：使用产品表的申购费率 × 折扣率
                    BigDecimal productFeeRate = product.getBuyFeeRate() != null 
                        ? product.getBuyFeeRate() 
                        : BigDecimal.ZERO;
                    feeRate = productFeeRate.multiply(feeConfig.getSubscriptionDiscountRate());
                    minFee = feeConfig.getBuyMinFee();
                } else {
                    feeRate = feeConfig.getBuyFeeRate();
                    minFee = feeConfig.getBuyMinFee();
                }
            } else {
                // 如果没有券商费率配置，使用产品表的默认费率
                feeRate = product.getBuyFeeRate() != null ? product.getBuyFeeRate() : BigDecimal.ZERO;
                minFee = BigDecimal.ZERO;
            }

            // 计算手续费：max(交易金额 × 费率, 最低手续费)
            BigDecimal calculatedFee = amount.multiply(feeRate);
            BigDecimal finalFee = calculatedFee.compareTo(minFee) > 0 ? calculatedFee : minFee;

            // 保留2位小数
            return finalFee.setScale(2, RoundingMode.HALF_UP);
        }

        // 场外产品：使用产品表的买入费率（0-100万档）
        if (product != null && "OTC".equals(product.getChannel())) {
            BigDecimal feeRate = product.getBuyFeeRate() != null ? product.getBuyFeeRate() : BigDecimal.ZERO;
            BigDecimal calculatedFee = amount.multiply(feeRate);
            return calculatedFee.setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }

    /**
     * 计算卖出/赎出手续费
     * 
     * @param accountId 券商账户ID（用于查找费率配置）
     * @param product 产品对象
     * @param orderType 订单类型：SELL/REDEMPTION
     * @param amount 交易金额
     * @param holdingDays 持有天数（场外基金需要，用于分段费率计算）
     * @return 手续费金额
     */
    public BigDecimal calculateSellFee(Long accountId, ProductMaster product, String orderType, BigDecimal amount, Integer holdingDays) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // 特殊产品类型：银行理财净值型和货币基金费率为0
        if (product != null && ("BANK_WM_NAV".equals(product.getAssetType()) || "MMF".equals(product.getAssetType()))) {
            return BigDecimal.ZERO;
        }

        // 场内产品：使用券商费率配置
        if (product != null && "EXCHANGE".equals(product.getChannel())) {
            // 确定费率规则类型
            String feeRuleType = determineFeeRuleType(product, orderType);
            
            // 查询费率配置
            BrokerFeeConfig feeConfig = getFeeConfig(accountId, feeRuleType);
            
            BigDecimal feeRate;
            BigDecimal minFee;

            if (feeConfig != null) {
                // 使用券商费率配置
                feeRate = feeConfig.getSellFeeRate();
                minFee = feeConfig.getSellMinFee();
            } else {
                // 如果没有券商费率配置，使用产品表的默认费率
                feeRate = product.getSellFeeRate() != null ? product.getSellFeeRate() : BigDecimal.ZERO;
                minFee = BigDecimal.ZERO;
            }

            // 计算手续费：max(交易金额 × 费率, 最低手续费)
            BigDecimal calculatedFee = amount.multiply(feeRate);
            BigDecimal finalFee = calculatedFee.compareTo(minFee) > 0 ? calculatedFee : minFee;

            // 保留2位小数
            return finalFee.setScale(2, RoundingMode.HALF_UP);
        }

        // 场外产品：按持有天数分段计算费率
        // 注意：普通基金（FUND/LOF）只使用分段费率，不使用产品表的默认卖出费率
        if (product != null && "OTC".equals(product.getChannel())) {
            BigDecimal feeRate = BigDecimal.ZERO;
            
            // 必须有持有天数才能计算分段费率
            if (holdingDays != null && holdingDays >= 0) {
                FundSellFeeTier tier = fundSellFeeTierMapper.selectByProductIdAndHoldingDays(product.getId(), holdingDays);
                if (tier != null) {
                    // 找到分段配置，使用分段费率
                    feeRate = tier.getSellFeeRate();
                } else {
                    // 如果没有找到分段配置，返回0（不再使用产品表的默认费率）
                    // 普通基金应该配置分段费率，如果没有配置则费率为0
                    feeRate = BigDecimal.ZERO;
                }
            } else {
                // 如果没有持有天数，无法确定费率，返回0
                // 场外基金卖出/赎回必须提供持有天数才能计算分段费率
                feeRate = BigDecimal.ZERO;
            }
            
            BigDecimal calculatedFee = amount.multiply(feeRate);
            return calculatedFee.setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }
    
    /**
     * 计算卖出/赎出手续费（重载方法，不提供持有天数时使用默认费率）
     * 
     * @param accountId 券商账户ID（用于查找费率配置）
     * @param product 产品对象
     * @param orderType 订单类型：SELL/REDEMPTION
     * @param amount 交易金额
     * @return 手续费金额
     */
    public BigDecimal calculateSellFee(Long accountId, ProductMaster product, String orderType, BigDecimal amount) {
        return calculateSellFee(accountId, product, orderType, amount, null);
    }

    /**
     * 根据订单类型自动计算手续费（买入或卖出）
     * 
     * @param accountId 券商账户ID
     * @param product 产品对象
     * @param orderType 订单类型：BUY/SELL/SUBSCRIPTION/REDEMPTION
     * @param amount 交易金额
     * @param holdingDays 持有天数（卖出/赎回时需要，用于场外基金分段费率计算）
     * @return 手续费金额
     */
    public BigDecimal calculateFee(Long accountId, ProductMaster product, String orderType, BigDecimal amount, Integer holdingDays) {
        if ("BUY".equals(orderType) || "SUBSCRIPTION".equals(orderType)) {
            return calculateBuyFee(accountId, product, orderType, amount);
        } else if ("SELL".equals(orderType) || "REDEMPTION".equals(orderType)) {
            return calculateSellFee(accountId, product, orderType, amount, holdingDays);
        }
        return BigDecimal.ZERO;
    }
    
    /**
     * 根据订单类型自动计算手续费（重载方法，不提供持有天数时使用默认费率）
     * 
     * @param accountId 券商账户ID
     * @param product 产品对象
     * @param orderType 订单类型：BUY/SELL/SUBSCRIPTION/REDEMPTION
     * @param amount 交易金额
     * @return 手续费金额
     */
    public BigDecimal calculateFee(Long accountId, ProductMaster product, String orderType, BigDecimal amount) {
        return calculateFee(accountId, product, orderType, amount, null);
    }

    /**
     * 从资金来源账户中查找券商账户（用于费率计算）
     * 
     * @param fundingAccountIds 资金来源账户ID列表
     * @return 券商账户ID，如果找不到则返回null
     */
    public Long findBrokerAccountId(java.util.List<Long> fundingAccountIds) {
        if (fundingAccountIds == null || fundingAccountIds.isEmpty()) {
            return null;
        }

        for (Long accountId : fundingAccountIds) {
            Account account = accountMapper.selectById(accountId);
            if (account != null && "BROKER".equals(account.getAccountType())) {
                return accountId;
            }
            // 如果是子账户，查找父账户
            if (account != null && account.getParentAccountId() != null) {
                Account parentAccount = accountMapper.selectById(account.getParentAccountId());
                if (parentAccount != null && "BROKER".equals(parentAccount.getAccountType())) {
                    return parentAccount.getId();
                }
            }
        }

        return null;
    }
}
