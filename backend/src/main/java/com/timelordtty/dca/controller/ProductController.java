package com.timelordtty.dca.controller;

import com.timelordtty.dca.mapper.ProductMasterMapper;
import com.timelordtty.dca.model.ProductMaster;
import com.timelordtty.dca.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 产品控制器
 */
@RestController
@RequestMapping("/api/v2/products")
public class ProductController {

    /**
     * 依赖的 ProductService 服务，用于复用该领域的业务校验和事务逻辑。
     */
    private final ProductService productService;

    /**
     * 处理写入类 API，将请求参数校验后委托给 Service 层。
     * 是否产生账本、账户或订单变更由对应 Service 事务边界决定。
     */
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * 处理只读查询 API，按当前用户和请求参数返回对应资源，不写入账本或修改持仓。
     */
    @GetMapping
    public ResponseEntity<List<ProductMaster>> getProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) String channel) {
        List<ProductMaster> products = productService.getProducts(keyword, assetType, channel);
        return ResponseEntity.ok(products);
    }

    /**
     * 返回当前场景的业务数据，用于前后端传递或服务层计算。
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductMaster> getProduct(@PathVariable Long id) {
        ProductMaster product = productService.getProduct(id);
        return ResponseEntity.ok(product);
    }

    /**
     * 处理写入类 API，将请求参数校验后委托给 Service 层。
     * 是否产生账本、账户或订单变更由对应 Service 事务边界决定。
     */
    @PostMapping
    public ResponseEntity<ProductMaster> createProduct(@RequestBody ProductMaster product) {
        ProductMaster created = productService.createProduct(product);
        return ResponseEntity.ok(created);
    }

    /**
     * 处理写入类 API，将请求参数校验后委托给 Service 层。
     * 是否产生账本、账户或订单变更由对应 Service 事务边界决定。
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductMaster> updateProduct(@PathVariable Long id, @RequestBody ProductMaster product) {
        product.setId(id);
        ProductMaster updated = productService.updateProduct(product);
        return ResponseEntity.ok(updated);
    }
    
    /**
     * 刷新单个产品的行情数据
     * POST /api/v2/products/{id}/refresh-market-data
     * 
     * @param id 产品ID
     * @return 刷新结果
     */
    @PostMapping("/{id}/refresh-market-data")
    public ResponseEntity<java.util.Map<String, Object>> refreshMarketData(@PathVariable Long id) {
        ProductMaster product = productService.getProduct(id);
        if (product == null) {
            return ResponseEntity.notFound().build();
        }
        productService.refreshMarketData(product);
        return ResponseEntity.ok(java.util.Map.of(
            "message", "行情数据采集任务已启动，将在后台执行",
            "productId", id,
            "productCode", product.getProductCode()
        ));
    }
    
    /**
     * 刷新所有产品的行情数据
     * POST /api/v2/products/refresh-all-market-data
     * 
     * @return 刷新结果
     */
    @PostMapping("/refresh-all-market-data")
    public ResponseEntity<java.util.Map<String, Object>> refreshAllMarketData() {
        productService.refreshAllMarketData();
        return ResponseEntity.ok(java.util.Map.of(
            "message", "全量行情数据采集任务已启动，将在后台执行"
        ));
    }
    
    /**
     * 批量更新产品排序
     * POST /api/v2/products/sort-order
     * 请求体：[{"id": 1, "sortOrder": 1}, {"id": 2, "sortOrder": 2}, ...]
     */
    @PostMapping("/sort-order")
    public ResponseEntity<Void> updateProductSortOrder(@RequestBody List<ProductSortOrderRequest> requests) {
        List<ProductMasterMapper.ProductSortOrderUpdate> updates = new java.util.ArrayList<>();
        for (ProductSortOrderRequest req : requests) {
            updates.add(new ProductMasterMapper.ProductSortOrderUpdate(req.getId(), req.getSortOrder()));
        }
        productService.batchUpdateSortOrder(updates);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 产品排序请求DTO
     */
    public static class ProductSortOrderRequest {
        /**
         * 主键 ID，用于数据库内部唯一定位记录。
         */
        private Long id;
        /**
         * 请求或响应字段，用于前后端传递该场景的业务信息。
         */
        private Integer sortOrder;
        
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

