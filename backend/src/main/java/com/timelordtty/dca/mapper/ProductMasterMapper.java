package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.ProductMaster;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * ProductMasterMapper 组件，定义 MyBatis SQL 映射，返回持久化对象或统计视图。
 */
@Mapper
public interface ProductMasterMapper {
    ProductMaster selectById(@Param("id") Long id);
    ProductMaster selectByCode(@Param("productCode") String productCode, @Param("channel") String channel, @Param("market") String market);
    
    /** 只按产品代码查找（不限制市场），用于初始持仓导入时避免重复创建产品 */
    ProductMaster selectByCodeOnly(@Param("productCode") String productCode);
    List<ProductMaster> selectByCondition(@Param("keyword") String keyword, @Param("assetType") String assetType, @Param("channel") String channel);
    int insert(ProductMaster product);
    int update(ProductMaster product);
    int batchUpdateSortOrder(@Param("updates") List<ProductSortOrderUpdate> updates);
    
    /**
     * 产品排序更新DTO
     */
    class ProductSortOrderUpdate {
        /**
         * 主键 ID，用于数据库内部唯一定位记录。
         */
        private Long id;
        /**
         * 请求或响应字段，用于前后端传递该场景的业务信息。
         */
        private Integer sortOrder;
        
        /**
         * 注入 ProductSortOrderUpdate 所需的 Mapper 和 Service 依赖，建立对应业务协作关系。
         */
        public ProductSortOrderUpdate(Long id, Integer sortOrder) {
            this.id = id;
            this.sortOrder = sortOrder;
        }
        
        /**
         * 返回主键 ID，用于数据库内部唯一定位记录。
         */
        public Long getId() {
            return id;
        }
        
        /**
         * 设置主键 ID，用于数据库内部唯一定位记录。
         */
        public void setId(Long id) {
            this.id = id;
        }
        
        /**
         * 返回当前场景的业务数据，用于前后端传递或服务层计算。
         */
        public Integer getSortOrder() {
            return sortOrder;
        }
        
        /**
         * 设置当前场景的业务数据，用于前后端传递或服务层计算。
         */
        public void setSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
        }
    }
}

