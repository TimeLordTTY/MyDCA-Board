package com.timelordtty.dca.mapper;

import com.timelordtty.dca.dto.LedgerStatsPostingDTO;
import com.timelordtty.dca.dto.LedgerStatsQueryDTO;
import com.timelordtty.dca.model.LedgerTxn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 交易流水 Mapper。
 *
 * <p>负责 ledger_txn 的基础读写以及流水统计所需的只读联查；统计查询方法不产生账本、账户或持仓变更。</p>
 */
@Mapper
public interface LedgerTxnMapper {
    /** 按数据库主键查询单条流水记录。 */
    LedgerTxn selectById(@Param("id") Long id);
    /** 按业务流水号查询单条流水记录。 */
    LedgerTxn selectByTxnId(@Param("txnId") String txnId);
    /** 查询指定订单关联的所有流水，用于订单和结算追踪。 */
    List<LedgerTxn> selectByOrderId(@Param("orderId") String orderId);
    /** 查询同一业务分组下的流水，例如转账成对记录。 */
    List<LedgerTxn> selectByBizGroupKey(@Param("bizGroupKey") String bizGroupKey);
    /**
     * 按流水列表页条件分页查询交易事件。
     *
     * <p>该查询只返回交易事件主表，不直接返回复式分录；账户维度过滤由调用方传入的账户集合控制。</p>
     */
    List<LedgerTxn> selectByCondition(@Param("userId") Long userId, @Param("txnType") String txnType, 
                                       @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                                       @Param("productId") Long productId, @Param("accountId") Long accountId,
                                       @Param("childAccountIds") List<Long> childAccountIds,
                                       @Param("note") String note,
                                       @Param("offset") Integer offset, @Param("limit") Integer limit);
    /** 统计列表页条件下的流水总数，口径需与 selectByCondition 保持一致。 */
    int countByCondition(@Param("userId") Long userId, @Param("txnType") String txnType, 
                         @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                         @Param("productId") Long productId, @Param("accountId") Long accountId,
                         @Param("childAccountIds") List<Long> childAccountIds,
                         @Param("note") String note);
    /** 插入新的交易事件，实际账本平衡性由服务层负责校验。 */
    int insert(LedgerTxn txn);
    /** 更新交易事件状态或关联信息，不负责直接写入分录。 */
    int update(LedgerTxn txn);
    /** 按业务流水号删除交易事件，调用方必须保证相关分录处理已完成。 */
    int deleteByTxnId(@Param("txnId") String txnId);
    /**
     * 查询流水统计需要的扁平化分录数据。
     *
     * <p>该方法只读 ledger_txn、ledger_posting 与账户相关字段，服务层再按类型、账户、分类和周期做聚合，避免重复计算同一笔流水。</p>
     */
    List<LedgerStatsPostingDTO> selectStatsPostings(LedgerStatsQueryDTO query);
}
