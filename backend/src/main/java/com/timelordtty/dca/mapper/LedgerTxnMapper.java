package com.timelordtty.dca.mapper;

import com.timelordtty.dca.dto.LedgerStatsPostingDTO;
import com.timelordtty.dca.dto.LedgerStatsQueryDTO;
import com.timelordtty.dca.model.LedgerTxn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
/**
 * 业务注释规范化: LedgerTxnMapper Mapper 接口，负责 MyBatis SQL 映射和持久化访问，真实业务规则由服务层保证。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public interface LedgerTxnMapper {
    /**
     * 业务注释规范化: 读取 selectById 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    LedgerTxn selectById(@Param("id") Long id);
    /**
     * 业务注释规范化: 读取 selectByTxnId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param txnId 流水业务 ID，用于聚合一笔复式记账交易下的所有分录。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    LedgerTxn selectByTxnId(@Param("txnId") String txnId);
    /**
     * 业务注释规范化: 读取 selectByOrderId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param orderId 关联订单 ID，用于串联订单创建、资金冻结、结算确认和流水入账。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<LedgerTxn> selectByOrderId(@Param("orderId") String orderId);
    /**
     * 业务注释规范化: 读取 selectByBizGroupKey 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param bizGroupKey bizGroupKey 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<LedgerTxn> selectByBizGroupKey(@Param("bizGroupKey") String bizGroupKey);
    List<LedgerTxn> selectByCondition(@Param("userId") Long userId, @Param("txnType") String txnType, 
                                       @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                                       @Param("productId") Long productId, @Param("accountId") Long accountId,
                                       @Param("childAccountIds") List<Long> childAccountIds,
                                       @Param("note") String note,
                                       @Param("offset") Integer offset, @Param("limit") Integer limit);
    int countByCondition(@Param("userId") Long userId, @Param("txnType") String txnType, 
                         @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                         @Param("productId") Long productId, @Param("accountId") Long accountId,
                         @Param("childAccountIds") List<Long> childAccountIds,
                         @Param("note") String note);
    /**
     * 业务注释规范化: 写入 insert 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param txn txn 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int insert(LedgerTxn txn);
    /**
     * 业务注释规范化: 更新 update 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param txn txn 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int update(LedgerTxn txn);
    /**
     * 业务注释规范化: 删除 deleteByTxnId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param txnId 流水业务 ID，用于聚合一笔复式记账交易下的所有分录。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int deleteByTxnId(@Param("txnId") String txnId);
    /**
     * 业务注释规范化: 读取 selectStatsPostings 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param query query 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<LedgerStatsPostingDTO> selectStatsPostings(LedgerStatsQueryDTO query);
}

