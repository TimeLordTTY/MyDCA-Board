package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.LedgerPosting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface LedgerPostingMapper {
    List<LedgerPosting> selectByTxnId(@Param("txnId") String txnId);
    List<LedgerPosting> selectByTxnIds(@Param("txnIds") List<String> txnIds);
    List<LedgerPosting> selectByAccountId(@Param("accountId") Long accountId);
    List<LedgerPosting> selectByAccountTypeAndOwner(@Param("accountType") String accountType, 
                                                     @Param("ownerUserId") Long ownerUserId, 
                                                     @Param("ownerFamilyId") Long ownerFamilyId);
    BigDecimal sumDebitByAccount(@Param("accountId") Long accountId);
    BigDecimal sumCreditByAccount(@Param("accountId") Long accountId);
    int insert(LedgerPosting posting);
    int batchInsert(@Param("postings") List<LedgerPosting> postings);
    
    /**
     * 查询某账户的所有分录（按交易时间排序，用于重算历史余额）
     */
    List<LedgerPosting> selectByAccountIdOrderByTxnTime(@Param("accountId") Long accountId);
    
    /**
     * 查询多个账户的所有分录（按交易时间排序，用于统一重算历史余额）
     */
    List<LedgerPosting> selectByAccountIdsOrderByTxnTime(@Param("accountIds") List<Long> accountIds);
    
    /**
     * 批量更新分录的历史余额
     */
    int batchUpdateBalanceAfter(@Param("updates") List<LedgerPosting> updates);
    
    /**
     * 根据交易ID删除所有分录
     */
    int deleteByTxnId(@Param("txnId") String txnId);
    
    /**
     * 查询所有有分录的账户ID（用于全量重算历史余额）
     */
    List<Long> selectDistinctAccountIds();
    
    /**
     * 查询指定账户的最新交易时间（用于判断新交易是否需要重算历史）
     * @param accountIds 账户ID列表
     * @return 最新交易时间，如果没有分录则返回null
     */
    java.time.LocalDateTime selectLatestTxnTimeByAccountIds(@Param("accountIds") List<Long> accountIds);
}

