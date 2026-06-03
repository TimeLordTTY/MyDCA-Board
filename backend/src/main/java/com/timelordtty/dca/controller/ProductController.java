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
/**
 * 业务注释规范化: ProductController 控制器，负责接收前端请求、读取用户上下文，并将业务处理委托给服务层。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class ProductController {

    /**
     * 业务注释规范化: productService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final ProductService productService;

    /**
     * 业务注释规范化: 处理 ProductController 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productService productService 业务字段，承载该对象在后端流程中的核心属性。
     */
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    /**
     * 业务注释规范化: 查询 getProducts 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param false false 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<List<ProductMaster>> getProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) String channel) {
        List<ProductMaster> products = productService.getProducts(keyword, assetType, channel);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    /**
     * 业务注释规范化: 查询 getProduct 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<ProductMaster> getProduct(@PathVariable Long id) {
        ProductMaster product = productService.getProduct(id);
        return ResponseEntity.ok(product);
    }

    @PostMapping
    /**
     * 业务注释规范化: 创建 createProduct 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param product product 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<ProductMaster> createProduct(@RequestBody ProductMaster product) {
        ProductMaster created = productService.createProduct(product);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    /**
     * 业务注释规范化: 更新 updateProduct 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @param product product 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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
    /**
     * 业务注释规范化: 处理 refreshMarketData 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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
    /**
     * 业务注释规范化: 处理 refreshAllMarketData 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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
    /**
     * 业务注释规范化: 更新 updateProductSortOrder 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param requests requests 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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
         * 业务注释规范化: 主键 ID，用于在后端内部唯一定位该业务记录。
         */
        private Long id;
        /**
         * 业务注释规范化: sortOrder 业务字段，承载该对象在后端流程中的核心属性。
         */
        private Integer sortOrder;
        
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

