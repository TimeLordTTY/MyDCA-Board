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

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<List<ProductMaster>> getProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) String channel) {
        List<ProductMaster> products = productService.getProducts(keyword, assetType, channel);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductMaster> getProduct(@PathVariable Long id) {
        ProductMaster product = productService.getProduct(id);
        return ResponseEntity.ok(product);
    }

    @PostMapping
    public ResponseEntity<ProductMaster> createProduct(@RequestBody ProductMaster product) {
        ProductMaster created = productService.createProduct(product);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductMaster> updateProduct(@PathVariable Long id, @RequestBody ProductMaster product) {
        product.setId(id);
        ProductMaster updated = productService.updateProduct(product);
        return ResponseEntity.ok(updated);
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
        private Long id;
        private Integer sortOrder;
        
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

