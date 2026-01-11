package com.timelordtty.dca.service;

import com.timelordtty.dca.mapper.ProductMasterMapper;
import com.timelordtty.dca.model.ProductMaster;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 产品服务（ProductService）
 *
 * 职责：产品主数据的查询与维护，提供按条件检索、单项查询及增删改等基础操作
 *
 * 说明：产品主数据用于下单、风控与看板展示，变更需谨慎并考虑向下兼容性（例如费率变更可能影响历史计算）
 */
@Service
public class ProductService {

    private final ProductMasterMapper productMasterMapper;

    public ProductService(ProductMasterMapper productMasterMapper) {
        this.productMasterMapper = productMasterMapper;
    }

    public List<ProductMaster> getProducts(String keyword, String assetType, String channel) {
        return productMasterMapper.selectByCondition(keyword, assetType, channel);
    }

    public ProductMaster getProduct(Long id) {
        return productMasterMapper.selectById(id);
    }

    public ProductMaster createProduct(ProductMaster product) {
        productMasterMapper.insert(product);
        return product;
    }

    public ProductMaster updateProduct(ProductMaster product) {
        productMasterMapper.update(product);
        return product;
    }
    
    /**
     * 批量更新产品排序
     * @param updates 排序更新列表，每个元素包含产品ID和新的排序值
     * @return 更新的记录数
     */
    public int batchUpdateSortOrder(List<ProductMasterMapper.ProductSortOrderUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            return 0;
        }
        return productMasterMapper.batchUpdateSortOrder(updates);
    }
}

