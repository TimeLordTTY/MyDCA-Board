package com.timelordtty.dca.service;

import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.mapper.NavMapper;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.Nav;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * MMF（货币基金类型）平台份额计算服务
 * 
 * 功能说明：
 * 1. MMF 平台关联产品后，设置初始份额
 * 2. 总金额 = 初始份额 × 最新净值
 * 3. 子账户可设置为固定金额（如房租预备金 4000 元，不随净值变化）
 * 4. 非固定子账户共享剩余金额/份额
 * 
 * @author timelordtty
 */
@Service
/**
 * 业务注释规范化: MmfSharesService 服务类，负责业务规则、账户、账本流水、订单或持仓数据的组合处理。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class MmfSharesService {

    /**
     * 业务注释规范化: accountMapper 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final AccountMapper accountMapper;
    /**
     * 业务注释规范化: navMapper 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final NavMapper navMapper;

    /**
     * 业务注释规范化: 处理 MmfSharesService 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param accountMapper accountMapper 业务字段，承载该对象在后端流程中的核心属性。
     * @param navMapper navMapper 业务字段，承载该对象在后端流程中的核心属性。
     */
    public MmfSharesService(AccountMapper accountMapper, NavMapper navMapper) {
        this.accountMapper = accountMapper;
        this.navMapper = navMapper;
    }

    /**
     * 计算 MMF 平台的份额分配详情
     * 
     * @param platformId MMF 平台账户 ID
     * @return 份额分配详情，如果不是 MMF 平台则返回 null
     */
    /**
     * 业务注释规范化: 计算 calculateShares 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param platformId platformId 关联 ID，用于连接对应业务对象并保持数据引用关系。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public MmfSharesDetail calculateShares(Long platformId) {
        Account platform = accountMapper.selectById(platformId);
        if (platform == null || !"MMF".equals(platform.getAccountType())) {
            return null;
        }
        
        // 如果平台没有关联产品或初始份额，返回 null
        if (platform.getLinkedProductId() == null || platform.getInitialShares() == null) {
            return null;
        }
        
        // 获取最新净值
        BigDecimal nav = getLatestNav(platform.getLinkedProductId());
        if (nav == null || nav.compareTo(BigDecimal.ZERO) <= 0) {
            nav = BigDecimal.ONE; // 默认净值为 1（货币基金）
        }
        
        MmfSharesDetail detail = new MmfSharesDetail();
        detail.setPlatformId(platformId);
        detail.setPlatformName(platform.getAccountName());
        detail.setProductId(platform.getLinkedProductId());
        detail.setNav(nav);
        detail.setTotalShares(platform.getInitialShares());
        detail.setTotalAmount(platform.getInitialShares().multiply(nav).setScale(2, RoundingMode.HALF_UP));
        
        // 获取子账户
        List<Account> children = accountMapper.selectChildren(platformId);
        List<ChildAccountShares> childShares = new ArrayList<>();
        
        BigDecimal fixedTotalAmount = BigDecimal.ZERO;
        BigDecimal fixedTotalShares = BigDecimal.ZERO;
        List<Account> nonFixedChildren = new ArrayList<>();
        
        // 先处理固定金额子账户
        for (Account child : children) {
            if (Boolean.TRUE.equals(child.getIsFixedAmount()) && child.getFixedAmount() != null) {
                ChildAccountShares cs = new ChildAccountShares();
                cs.setAccountId(child.getId());
                cs.setAccountName(child.getAccountName());
                cs.setIsFixedAmount(true);
                cs.setAmount(child.getFixedAmount());
                cs.setShares(child.getFixedAmount().divide(nav, 6, RoundingMode.HALF_UP));
                childShares.add(cs);
                
                fixedTotalAmount = fixedTotalAmount.add(child.getFixedAmount());
                fixedTotalShares = fixedTotalShares.add(cs.getShares());
            } else {
                nonFixedChildren.add(child);
            }
        }
        
        // 计算非固定子账户的份额
        BigDecimal remainingAmount = detail.getTotalAmount().subtract(fixedTotalAmount);
        BigDecimal remainingShares = detail.getTotalShares().subtract(fixedTotalShares);
        
        if (!nonFixedChildren.isEmpty()) {
            // 如果只有一个非固定子账户，全部分配给它
            if (nonFixedChildren.size() == 1) {
                Account child = nonFixedChildren.get(0);
                ChildAccountShares cs = new ChildAccountShares();
                cs.setAccountId(child.getId());
                cs.setAccountName(child.getAccountName());
                cs.setIsFixedAmount(false);
                cs.setAmount(remainingAmount.max(BigDecimal.ZERO));
                cs.setShares(remainingShares.max(BigDecimal.ZERO));
                childShares.add(cs);
            } else {
                // 多个非固定子账户，平均分配剩余份额
                BigDecimal sharePerChild = remainingShares.divide(
                    BigDecimal.valueOf(nonFixedChildren.size()), 6, RoundingMode.HALF_UP);
                BigDecimal amountPerChild = remainingAmount.divide(
                    BigDecimal.valueOf(nonFixedChildren.size()), 2, RoundingMode.HALF_UP);
                
                for (Account child : nonFixedChildren) {
                    ChildAccountShares cs = new ChildAccountShares();
                    cs.setAccountId(child.getId());
                    cs.setAccountName(child.getAccountName());
                    cs.setIsFixedAmount(false);
                    cs.setAmount(amountPerChild.max(BigDecimal.ZERO));
                    cs.setShares(sharePerChild.max(BigDecimal.ZERO));
                    childShares.add(cs);
                }
            }
        }
        
        detail.setChildAccounts(childShares);
        detail.setAllocatedAmount(fixedTotalAmount.add(
            childShares.stream()
                .filter(cs -> !cs.getIsFixedAmount())
                .map(ChildAccountShares::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)));
        detail.setUnallocatedAmount(detail.getTotalAmount().subtract(detail.getAllocatedAmount()));
        
        return detail;
    }
    
    /**
     * 获取产品最新净值
     */
    /**
     * 业务注释规范化: 查询 getLatestNav 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该私有方法封装局部复杂逻辑，用于保持统计、展示或校验口径一致。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    private BigDecimal getLatestNav(Long productId) {
        Nav latestNav = navMapper.selectLatest(productId);
        return latestNav != null ? latestNav.getNav() : null;
    }
    
    /**
     * MMF 份额分配详情
     */
    @Data
    public static class MmfSharesDetail {
        /** 平台账户 ID */
        /**
         * 业务注释规范化: platformId 关联 ID，用于连接对应业务对象并保持数据引用关系。
         */
        private Long platformId;
        /** 平台名称 */
        /**
         * 业务注释规范化: platformName 业务字段，承载该对象在后端流程中的核心属性。
         */
        private String platformName;
        /** 关联产品 ID */
        /**
         * 业务注释规范化: 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
         */
        private Long productId;
        /** 最新净值 */
        /**
         * 业务注释规范化: 产品净值，用于按份额折算市值、收益或确认金额。
         */
        private BigDecimal nav;
        /** 总份额 */
        /**
         * 业务注释规范化: totalShares 业务字段，承载该对象在后端流程中的核心属性。
         */
        private BigDecimal totalShares;
        /** 总金额 = 总份额 × 净值 */
        /**
         * 业务注释规范化: totalAmount 金额字段，用于表达该场景下的资金规模或费用口径。
         */
        private BigDecimal totalAmount;
        /** 已分配金额 */
        /**
         * 业务注释规范化: allocatedAmount 金额字段，用于表达该场景下的资金规模或费用口径。
         */
        private BigDecimal allocatedAmount;
        /** 未分配金额 */
        /**
         * 业务注释规范化: unallocatedAmount 金额字段，用于表达该场景下的资金规模或费用口径。
         */
        private BigDecimal unallocatedAmount;
        /** 子账户份额列表 */
        /**
         * 业务注释规范化: childAccounts 业务字段，承载该对象在后端流程中的核心属性。
         */
        private List<ChildAccountShares> childAccounts;
    }
    
    /**
     * 子账户份额详情
     */
    @Data
    public static class ChildAccountShares {
        /** 账户 ID */
        /**
         * 业务注释规范化: 关联账户 ID，指向承载资金、持仓或虚拟科目的账户。
         */
        private Long accountId;
        /** 账户名称 */
        /**
         * 业务注释规范化: accountName 业务字段，承载该对象在后端流程中的核心属性。
         */
        private String accountName;
        /** 是否固定金额 */
        /**
         * 业务注释规范化: isFixedAmount 金额字段，用于表达该场景下的资金规模或费用口径。
         */
        private Boolean isFixedAmount;
        /** 金额 */
        /**
         * 业务注释规范化: 业务金额，通常以账户币种计价，正负含义由交易类型和分录方向决定。
         */
        private BigDecimal amount;
        /** 份额 */
        /**
         * 业务注释规范化: 产品份额，适用于基金、ETF、货币基金等按份额管理的资产。
         */
        private BigDecimal shares;
    }
}
