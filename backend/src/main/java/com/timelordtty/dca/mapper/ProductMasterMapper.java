package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.ProductMaster;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
/**
 * 业务注释规范化: ProductMasterMapper Mapper 接口，负责 MyBatis SQL 映射和持久化访问，真实业务规则由服务层保证。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public interface ProductMasterMapper {
    /**
     * 业务注释规范化: 读取 selectById 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    ProductMaster selectById(@Param("id") Long id);
    /**
     * 业务注释规范化: 读取 selectByCode 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productCode productCode 业务字段，承载该对象在后端流程中的核心属性。
     * @param channel channel 业务字段，承载该对象在后端流程中的核心属性。
     * @param market market 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    ProductMaster selectByCode(@Param("productCode") String productCode, @Param("channel") String channel, @Param("market") String market);
    
    /** 只按产品代码查找（不限制市场），用于初始持仓导入时避免重复创建产品 */
    /**
     * 业务注释规范化: 读取 selectByCodeOnly 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productCode productCode 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    ProductMaster selectByCodeOnly(@Param("productCode") String productCode);
    /**
     * 业务注释规范化: 读取 selectByCondition 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param keyword keyword 业务字段，承载该对象在后端流程中的核心属性。
     * @param assetType assetType 类型字段，用于区分不同业务分类并驱动处理分支。
     * @param channel channel 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<ProductMaster> selectByCondition(@Param("keyword") String keyword, @Param("assetType") String assetType, @Param("channel") String channel);
    /**
     * 业务注释规范化: 写入 insert 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param product product 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int insert(ProductMaster product);
    /**
     * 业务注释规范化: 更新 update 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param product product 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int update(ProductMaster product);
    /**
     * 业务注释规范化: 处理 batchUpdateSortOrder 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param updates updates 日期字段，用于交易、确认、净值或统计周期口径。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int batchUpdateSortOrder(@Param("updates") List<ProductSortOrderUpdate> updates);
    
    /**
     * 产品排序更新DTO
     */
    /**
     * 业务注释规范化: ProductSortOrderUpdate Mapper 接口，负责 MyBatis SQL 映射和持久化访问，真实业务规则由服务层保证。
     *
     * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
     */
    class ProductSortOrderUpdate {
        /**
         * 业务注释规范化: 主键 ID，用于在后端内部唯一定位该业务记录。
         */
        private Long id;
        /**
         * 业务注释规范化: sortOrder 业务字段，承载该对象在后端流程中的核心属性。
         */
        private Integer sortOrder;
        
        /**
         * 业务注释规范化: 处理 ProductSortOrderUpdate 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
         * @param sortOrder sortOrder 业务字段，承载该对象在后端流程中的核心属性。
         */
        public ProductSortOrderUpdate(Long id, Integer sortOrder) {
            this.id = id;
            this.sortOrder = sortOrder;
        }
        
        /**
         * 业务注释规范化: 查询 getId 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public Long getId() {
            return id;
        }
        
        /**
         * 业务注释规范化: 处理 setId 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setId(Long id) {
            this.id = id;
        }
        
        /**
         * 业务注释规范化: 查询 getSortOrder 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public Integer getSortOrder() {
            return sortOrder;
        }
        
        /**
         * 业务注释规范化: 处理 setSortOrder 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param sortOrder sortOrder 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
        }
    }
}

