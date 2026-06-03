package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.Family;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
/**
 * 业务注释规范化: FamilyMapper Mapper 接口，负责 MyBatis SQL 映射和持久化访问，真实业务规则由服务层保证。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public interface FamilyMapper {
    /**
     * 业务注释规范化: 读取 selectById 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    Family selectById(@Param("id") Long id);
    /**
     * 业务注释规范化: 读取 selectByCode 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param familyCode familyCode 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    Family selectByCode(@Param("familyCode") String familyCode);
    /**
     * 业务注释规范化: 写入 insert 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param family family 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int insert(Family family);
    /**
     * 业务注释规范化: 更新 update 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param family family 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int update(Family family);
}

