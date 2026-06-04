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
     * 产品服务入口，负责场内基金、货币基金等产品资料的查询和同步。
     */
    private final ProductService productService;

    /**
     * 装配产品服务，处理产品主数据的查询和维护接口。
     */
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * 按关键字、资产类型和渠道查询产品主数据列表，用于产品管理页和订单录入下拉选择。
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
     * 按产品 ID 读取产品主数据，用于订单录入、持仓展示和产品编辑。
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductMaster> getProduct(@PathVariable Long id) {
        ProductMaster product = productService.getProduct(id);
        return ResponseEntity.ok(product);
    }

    /**
     * 新增产品主数据记录，保存代码、市场、名称和产品分类等基础资料。
     */
    @PostMapping
    public ResponseEntity<ProductMaster> createProduct(@RequestBody ProductMaster product) {
        ProductMaster created = productService.createProduct(product);
        return ResponseEntity.ok(created);
    }

    /**
     * 更新产品主数据展示信息，不修改历史订单和持仓流水。
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
         * 产品展示顺序，数值越小越靠前，用于产品列表和下拉选择排序。
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
         * 读取产品排序权重，数值越小越靠前展示。
         */
        public Integer getSortOrder() {
            return sortOrder;
        }
        
        /**
         * 设置产品展示顺序，用于保存拖拽排序或批量排序后的前端展示位置。
         */
        public void setSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
        }
    }
}

