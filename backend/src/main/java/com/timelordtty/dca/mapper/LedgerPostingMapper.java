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
}

