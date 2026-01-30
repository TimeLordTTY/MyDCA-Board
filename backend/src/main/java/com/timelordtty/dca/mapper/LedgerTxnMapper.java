package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.LedgerTxn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface LedgerTxnMapper {
    LedgerTxn selectById(@Param("id") Long id);
    LedgerTxn selectByTxnId(@Param("txnId") String txnId);
    List<LedgerTxn> selectByOrderId(@Param("orderId") String orderId);
    List<LedgerTxn> selectByBizGroupKey(@Param("bizGroupKey") String bizGroupKey);
    List<LedgerTxn> selectByCondition(@Param("userId") Long userId, @Param("txnType") String txnType, 
                                       @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                                       @Param("productId") Long productId, @Param("accountId") Long accountId,
                                       @Param("offset") Integer offset, @Param("limit") Integer limit);
    int countByCondition(@Param("userId") Long userId, @Param("txnType") String txnType, 
                         @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                         @Param("productId") Long productId, @Param("accountId") Long accountId);
    int insert(LedgerTxn txn);
    int update(LedgerTxn txn);
    int deleteByTxnId(@Param("txnId") String txnId);
}

