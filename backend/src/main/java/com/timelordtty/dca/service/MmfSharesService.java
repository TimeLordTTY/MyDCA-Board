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
public class MmfSharesService {

    private final AccountMapper accountMapper;
    private final NavMapper navMapper;

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
        private Long platformId;
        /** 平台名称 */
        private String platformName;
        /** 关联产品 ID */
        private Long productId;
        /** 最新净值 */
        private BigDecimal nav;
        /** 总份额 */
        private BigDecimal totalShares;
        /** 总金额 = 总份额 × 净值 */
        private BigDecimal totalAmount;
        /** 已分配金额 */
        private BigDecimal allocatedAmount;
        /** 未分配金额 */
        private BigDecimal unallocatedAmount;
        /** 子账户份额列表 */
        private List<ChildAccountShares> childAccounts;
    }
    
    /**
     * 子账户份额详情
     */
    @Data
    public static class ChildAccountShares {
        /** 账户 ID */
        private Long accountId;
        /** 账户名称 */
        private String accountName;
        /** 是否固定金额 */
        private Boolean isFixedAmount;
        /** 金额 */
        private BigDecimal amount;
        /** 份额 */
        private BigDecimal shares;
    }
}
