package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.UserFamilyRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
/**
 * 业务注释规范化: UserFamilyRoleMapper Mapper 接口，负责 MyBatis SQL 映射和持久化访问，真实业务规则由服务层保证。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public interface UserFamilyRoleMapper {
    /**
     * 业务注释规范化: 读取 selectByUserId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param userId 所属用户 ID，用于限定个人数据权限和查询范围。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<UserFamilyRole> selectByUserId(@Param("userId") Long userId);
    /**
     * 业务注释规范化: 读取 selectByFamilyId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param familyId 所属家庭 ID，用于家庭视角下的数据隔离。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<UserFamilyRole> selectByFamilyId(@Param("familyId") Long familyId);
    /**
     * 业务注释规范化: 读取 selectRole 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param userId 所属用户 ID，用于限定个人数据权限和查询范围。
     * @param familyId 所属家庭 ID，用于家庭视角下的数据隔离。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    String selectRole(@Param("userId") Long userId, @Param("familyId") Long familyId);
    /**
     * 业务注释规范化: 统计 countAdmins 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param familyId 所属家庭 ID，用于家庭视角下的数据隔离。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int countAdmins(@Param("familyId") Long familyId);
    /**
     * 业务注释规范化: 更新 updateRole 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param userId 所属用户 ID，用于限定个人数据权限和查询范围。
     * @param familyId 所属家庭 ID，用于家庭视角下的数据隔离。
     * @param role role 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int updateRole(@Param("userId") Long userId, @Param("familyId") Long familyId, @Param("role") String role);
    /**
     * 业务注释规范化: 写入 insert 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param userFamilyRole userFamilyRole 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int insert(UserFamilyRole userFamilyRole);
    /**
     * 业务注释规范化: 删除 delete 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param userId 所属用户 ID，用于限定个人数据权限和查询范围。
     * @param familyId 所属家庭 ID，用于家庭视角下的数据隔离。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int delete(@Param("userId") Long userId, @Param("familyId") Long familyId);
}

