package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
/**
 * 业务注释规范化: UserMapper Mapper 接口，负责 MyBatis SQL 映射和持久化访问，真实业务规则由服务层保证。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public interface UserMapper {
    /**
     * 业务注释规范化: 读取 selectById 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    User selectById(@Param("id") Long id);
    /**
     * 业务注释规范化: 读取 selectByUsername 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param username username 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    User selectByUsername(@Param("username") String username);
    /**
     * 业务注释规范化: 读取 selectActiveUserIds 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<Long> selectActiveUserIds();
    /**
     * 业务注释规范化: 写入 insert 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param user user 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int insert(User user);
    /**
     * 业务注释规范化: 更新 update 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param user user 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int update(User user);
    int updateProfile(@Param("id") Long id,
                      @Param("nickname") String nickname,
                      @Param("email") String email,
                      @Param("phone") String phone);
    /**
     * 业务注释规范化: 更新 updatePasswordHash 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @param passwordHash passwordHash 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int updatePasswordHash(@Param("id") Long id, @Param("passwordHash") String passwordHash);
    /**
     * 业务注释规范化: 更新 updateLastLoginAt 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @param lastLoginAt lastLoginAt 时间字段，用于记录业务动作发生或审计时间。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int updateLastLoginAt(@Param("id") Long id, @Param("lastLoginAt") LocalDateTime lastLoginAt);
}

