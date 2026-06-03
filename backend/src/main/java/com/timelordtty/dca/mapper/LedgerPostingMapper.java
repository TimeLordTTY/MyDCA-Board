package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.LedgerPosting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
/**
 * 业务注释规范化: LedgerPostingMapper Mapper 接口，负责 MyBatis SQL 映射和持久化访问，真实业务规则由服务层保证。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public interface LedgerPostingMapper {
    /**
     * 业务注释规范化: 读取 selectByTxnId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param txnId 流水业务 ID，用于聚合一笔复式记账交易下的所有分录。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<LedgerPosting> selectByTxnId(@Param("txnId") String txnId);
    /**
     * 业务注释规范化: 读取 selectByTxnIds 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param txnIds txnIds 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<LedgerPosting> selectByTxnIds(@Param("txnIds") List<String> txnIds);
    /**
     * 业务注释规范化: 读取 selectByAccountId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param accountId 关联账户 ID，指向承载资金、持仓或虚拟科目的账户。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<LedgerPosting> selectByAccountId(@Param("accountId") Long accountId);
    List<LedgerPosting> selectByAccountTypeAndOwner(@Param("accountType") String accountType, 
                                                     @Param("ownerUserId") Long ownerUserId, 
                                                     @Param("ownerFamilyId") Long ownerFamilyId);
    /**
     * 业务注释规范化: 处理 sumDebitByAccount 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param accountId 关联账户 ID，指向承载资金、持仓或虚拟科目的账户。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    BigDecimal sumDebitByAccount(@Param("accountId") Long accountId);
    /**
     * 业务注释规范化: 处理 sumCreditByAccount 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param accountId 关联账户 ID，指向承载资金、持仓或虚拟科目的账户。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    BigDecimal sumCreditByAccount(@Param("accountId") Long accountId);
    /**
     * 业务注释规范化: 写入 insert 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param posting posting 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int insert(LedgerPosting posting);
    /**
     * 业务注释规范化: 处理 batchInsert 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param postings postings 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int batchInsert(@Param("postings") List<LedgerPosting> postings);
    
    /**
     * 查询某账户的所有分录（按交易时间排序，用于重算历史余额）
     */
    /**
     * 业务注释规范化: 读取 selectByAccountIdOrderByTxnTime 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param accountId 关联账户 ID，指向承载资金、持仓或虚拟科目的账户。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<LedgerPosting> selectByAccountIdOrderByTxnTime(@Param("accountId") Long accountId);
    
    /**
     * 查询多个账户的所有分录（按交易时间排序，用于统一重算历史余额）
     */
    /**
     * 业务注释规范化: 读取 selectByAccountIdsOrderByTxnTime 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param accountIds accountIds 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<LedgerPosting> selectByAccountIdsOrderByTxnTime(@Param("accountIds") List<Long> accountIds);
    
    /**
     * 批量更新分录的历史余额
     */
    /**
     * 业务注释规范化: 处理 batchUpdateBalanceAfter 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param updates updates 日期字段，用于交易、确认、净值或统计周期口径。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int batchUpdateBalanceAfter(@Param("updates") List<LedgerPosting> updates);
    
    /**
     * 根据交易ID删除所有分录
     */
    /**
     * 业务注释规范化: 删除 deleteByTxnId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param txnId 流水业务 ID，用于聚合一笔复式记账交易下的所有分录。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int deleteByTxnId(@Param("txnId") String txnId);
    
    /**
     * 查询所有有分录的账户ID（用于全量重算历史余额）
     */
    /**
     * 业务注释规范化: 读取 selectDistinctAccountIds 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<Long> selectDistinctAccountIds();
    
    /**
     * 查询指定账户的最新交易时间（用于判断新交易是否需要重算历史）
     * @param accountIds 账户ID列表
     * @return 最新交易时间，如果没有分录则返回null
     */
    /**
     * 业务注释规范化: 读取 selectLatestTxnTimeByAccountIds 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param accountIds accountIds 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    java.time.LocalDateTime selectLatestTxnTimeByAccountIds(@Param("accountIds") List<Long> accountIds);
}

