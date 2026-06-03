package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
/**
 * 业务注释规范化: AccountMapper Mapper 接口，负责 MyBatis SQL 映射和持久化访问，真实业务规则由服务层保证。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public interface AccountMapper {
    /**
     * 业务注释规范化: 读取 selectById 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    Account selectById(@Param("id") Long id);
    /**
     * 业务注释规范化: 读取 selectByIds 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param ids ids 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<Account> selectByIds(@Param("ids") List<Long> ids);
    /**
     * 业务注释规范化: 读取 selectByCode 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param accountCode accountCode 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    Account selectByCode(@Param("accountCode") String accountCode);
    /**
     * 业务注释规范化: 读取 selectByOwner 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param ownerUserId ownerUserId 关联 ID，用于连接对应业务对象并保持数据引用关系。
     * @param ownerFamilyId ownerFamilyId 关联 ID，用于连接对应业务对象并保持数据引用关系。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<Account> selectByOwner(@Param("ownerUserId") Long ownerUserId, @Param("ownerFamilyId") Long ownerFamilyId);
    List<Account> selectVirtualAccountsByOwner(@Param("ownerUserId") Long ownerUserId,
                                               @Param("ownerFamilyId") Long ownerFamilyId,
                                               @Param("virtualSubtype") String virtualSubtype);
    /**
     * 业务注释规范化: 读取 selectChildren 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param parentAccountId 父级账户 ID，用于表达平台账户与资金分区的层级关系。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<Account> selectChildren(@Param("parentAccountId") Long parentAccountId);
    /**
     * 业务注释规范化: 读取 selectLeafAccounts 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param ownerUserId ownerUserId 关联 ID，用于连接对应业务对象并保持数据引用关系。
     * @param ownerFamilyId ownerFamilyId 关联 ID，用于连接对应业务对象并保持数据引用关系。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<Account> selectLeafAccounts(@Param("ownerUserId") Long ownerUserId, @Param("ownerFamilyId") Long ownerFamilyId);
    List<Account> selectByLinkedProduct(@Param("productId") Long productId, 
                                       @Param("ownerUserId") Long ownerUserId, 
                                       @Param("ownerFamilyId") Long ownerFamilyId);
    /**
     * 业务注释规范化: 读取 selectByLinkedProductId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    Account selectByLinkedProductId(@Param("productId") Long productId);
    /**
     * 业务注释规范化: 读取 selectAllLinkedAccounts 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<Account> selectAllLinkedAccounts();
    /**
     * 业务注释规范化: 写入 insert 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param account account 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int insert(Account account);
    /**
     * 业务注释规范化: 更新 update 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param account account 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int update(Account account);
    /**
     * 业务注释规范化: 更新 updateBalance 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @param balance 账面余额，由初始余额和已确认分录推导，不应绕过记账规则直接修改。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int updateBalance(@Param("id") Long id, @Param("balance") BigDecimal balance);
    /**
     * 业务注释规范化: 更新 updateReservedAmount 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @param reservedAmount 冻结或占用金额，通常来源于未结算订单，结算或取消时释放。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int updateReservedAmount(@Param("id") Long id, @Param("reservedAmount") BigDecimal reservedAmount);
    /**
     * 业务注释规范化: 更新 updateInitialShares 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @param initialShares initialShares 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int updateInitialShares(@Param("id") Long id, @Param("initialShares") BigDecimal initialShares);
}

