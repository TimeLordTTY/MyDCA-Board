package com.timelordtty.dca.service;

import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.mapper.LedgerPostingMapper;
import com.timelordtty.dca.mapper.LedgerTxnMapper;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.LedgerPosting;
import com.timelordtty.dca.model.LedgerTxn;
import org.springframework.stereotype.Service;

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
    private final AccountMapper accountMapper;

    public HoldingService(LedgerPostingMapper ledgerPostingMapper, LedgerTxnMapper ledgerTxnMapper,
                          AccountMapper accountMapper) {
        this.ledgerPostingMapper = ledgerPostingMapper;
        this.ledgerTxnMapper = ledgerTxnMapper;
        this.accountMapper = accountMapper;
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
     * @return 持仓信息Map，key为productId，value为HoldingInfo
     */
    public Map<Long, HoldingInfo> calculateHoldings(Long userId) {
        // 查询所有POSITION类型的账户（虚拟账户）
        List<Account> accounts = accountMapper.selectByOwner(userId, null);
        List<Account> positionAccounts = accounts.stream()
                .filter(a -> "VIRTUAL".equals(a.getAccountKind()) && "POSITION".equals(a.getAccountType()))
                .toList();

        Map<Long, HoldingInfo> holdings = new HashMap<>();

        for (Account positionAccount : positionAccounts) {
            // 查询该持仓账户的所有分录
            List<LedgerPosting> postings = ledgerPostingMapper.selectByAccountId(positionAccount.getId());

            for (LedgerPosting posting : postings) {
                if (!"POSITION".equals(posting.getAccountType())) {
                    continue;
                }

                // 通过ledger_txn获取productId
                LedgerTxn txn = ledgerTxnMapper.selectByTxnId(posting.getTxnId());
                if (txn == null || txn.getProductId() == null) {
                    continue; // 跳过没有productId的交易
                }

                Long productId = txn.getProductId();

                // 创建或获取持仓信息
                HoldingInfo holding = holdings.getOrDefault(productId, new HoldingInfo());
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
        }

        // 计算平均成本
        for (Map.Entry<Long, HoldingInfo> entry : holdings.entrySet()) {
            HoldingInfo holding = entry.getValue();
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
        private BigDecimal totalShares;
        private BigDecimal totalCost;
        private BigDecimal avgCost;
        private BigDecimal marketValue;
        private BigDecimal unrealizedPnl;

        // Getters and setters
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
}

