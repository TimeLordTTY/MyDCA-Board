package com.timelordtty.dca.service;

import com.timelordtty.dca.mapper.ProductMasterMapper;
import com.timelordtty.dca.model.ProductMaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);

    private final ProductMasterMapper productMasterMapper;
    private final PythonScriptService pythonScriptService;

    public ProductService(ProductMasterMapper productMasterMapper, PythonScriptService pythonScriptService) {
        this.productMasterMapper = productMasterMapper;
        this.pythonScriptService = pythonScriptService;
    }

    public List<ProductMaster> getProducts(String keyword, String assetType, String channel) {
        return productMasterMapper.selectByCondition(keyword, assetType, channel);
    }

    public ProductMaster getProduct(Long id) {
        return productMasterMapper.selectById(id);
    }

    public ProductMaster createProduct(ProductMaster product) {
        productMasterMapper.insert(product);
        
        // 异步触发行情采集（新增产品后需要获取行情数据）
        triggerMarketDataCollection(product);
        
        return product;
    }
    
    /**
     * 触发新产品的行情数据采集
     * 包括：历史行情补齐、实时行情（场内）、净值（场外）
     * 
     * @param product 新创建的产品
     */
    private void triggerMarketDataCollection(ProductMaster product) {
        // 异步执行，避免阻塞产品创建响应
        new Thread(() -> {
            try {
                logger.info("开始为新产品[{}({})]采集行情数据...", product.getProductName(), product.getProductCode());
                
                // 等待2秒，确保数据库事务提交
                Thread.sleep(2000);
                
                // 1. 历史行情补齐（包含场内和场外产品）
                logger.info("执行历史行情补齐...");
                String historyResult = pythonScriptService.backfillMarketHistory();
                logger.info("历史行情补齐完成: {}", historyResult);
                
                // 2. 根据产品渠道类型采集相应行情
                String channel = product.getChannel();
                if ("EXCHANGE".equals(channel)) {
                    // 场内产品：采集实时行情和日K线
                    logger.info("采集场内实时行情...");
                    String realtimeResult = pythonScriptService.collectETFRealtime();
                    logger.info("场内实时行情采集完成: {}", realtimeResult);
                    
                    logger.info("采集场内日K线...");
                    String dailyResult = pythonScriptService.collectETFDaily();
                    logger.info("场内日K线采集完成: {}", dailyResult);
                } else {
                    // 场外产品：采集基金净值
                    logger.info("采集基金净值...");
                    String navResult = pythonScriptService.collectFundNav();
                    logger.info("基金净值采集完成: {}", navResult);
                }
                
                logger.info("新产品[{}]行情数据采集完成！", product.getProductCode());
                
            } catch (Exception e) {
                logger.error("新产品行情采集异常: {}", e.getMessage(), e);
            }
        }, "MarketDataCollector-" + product.getProductCode()).start();
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
    
    /**
     * 刷新单个产品的行情数据
     * 
     * @param product 产品
     */
    public void refreshMarketData(ProductMaster product) {
        triggerMarketDataCollection(product);
    }
    
    /**
     * 刷新所有产品的行情数据
     */
    public void refreshAllMarketData() {
        new Thread(() -> {
            try {
                logger.info("开始全量行情数据采集...");
                
                // 1. 历史行情补齐
                logger.info("执行历史行情补齐...");
                String historyResult = pythonScriptService.backfillMarketHistory();
                logger.info("历史行情补齐完成: {}", historyResult);
                
                // 2. 场内实时行情
                logger.info("采集场内实时行情...");
                String realtimeResult = pythonScriptService.collectETFRealtime();
                logger.info("场内实时行情采集完成: {}", realtimeResult);
                
                // 3. 场内日K线
                logger.info("采集场内日K线...");
                String dailyResult = pythonScriptService.collectETFDaily();
                logger.info("场内日K线采集完成: {}", dailyResult);
                
                // 4. 场外基金净值
                logger.info("采集基金净值...");
                String navResult = pythonScriptService.collectFundNav();
                logger.info("基金净值采集完成: {}", navResult);
                
                logger.info("全量行情数据采集完成！");
                
            } catch (Exception e) {
                logger.error("全量行情采集异常: {}", e.getMessage(), e);
            }
        }, "MarketDataCollector-All").start();
    }
}

