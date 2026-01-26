package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.LedgerPosting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface LedgerPostingMapper {
    List<LedgerPosting> selectByTxnId(@Param("txnId") String txnId);
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
     * 批量更新分录的历史余额
     */
    int batchUpdateBalanceAfter(@Param("updates") List<LedgerPosting> updates);
}

