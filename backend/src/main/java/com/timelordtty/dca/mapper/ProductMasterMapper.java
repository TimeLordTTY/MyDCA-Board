package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.ProductMaster;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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
        private Long id;
        private Integer sortOrder;
        
        public ProductSortOrderUpdate(Long id, Integer sortOrder) {
            this.id = id;
            this.sortOrder = sortOrder;
        }
        
        public Long getId() {
            return id;
        }
        
        public void setId(Long id) {
            this.id = id;
        }
        
        public Integer getSortOrder() {
            return sortOrder;
        }
        
        public void setSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
        }
    }
}

