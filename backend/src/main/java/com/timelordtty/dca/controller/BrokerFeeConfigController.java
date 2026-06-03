package com.timelordtty.dca.controller;

import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.mapper.BrokerFeeConfigMapper;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.BrokerFeeConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 券商费率配置控制器
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v2/accounts/{accountId}/broker-fee-configs")
public class BrokerFeeConfigController {

    private final BrokerFeeConfigMapper brokerFeeConfigMapper;
    private final AccountMapper accountMapper;

    public BrokerFeeConfigController(BrokerFeeConfigMapper brokerFeeConfigMapper,
                                    AccountMapper accountMapper) {
        this.brokerFeeConfigMapper = brokerFeeConfigMapper;
        this.accountMapper = accountMapper;
    }

    /**
     * 获取券商账户的所有费率配置
     * 
     * @param accountId 券商账户ID
     * @return 费率配置列表
     */
    @GetMapping
    public ResponseEntity<List<BrokerFeeConfig>> getFeeConfigs(@PathVariable Long accountId) {
        // 验证账户类型是否为BROKER
        Account account = accountMapper.selectById(accountId);
        if (account == null) {
            return ResponseEntity.notFound().build();
        }
        if (!"BROKER".equals(account.getAccountType())) {
            return ResponseEntity.badRequest().build();
        }
        
        List<BrokerFeeConfig> configs = brokerFeeConfigMapper.selectByAccountId(accountId);
        return ResponseEntity.ok(configs);
    }

    /**
     * 获取单个费率配置
     * 
     * @param accountId 券商账户ID
     * @param id 费率配置ID
     * @return 费率配置对象
     */
    @GetMapping("/{id}")
    public ResponseEntity<BrokerFeeConfig> getFeeConfig(@PathVariable Long accountId, @PathVariable Long id) {
        BrokerFeeConfig config = brokerFeeConfigMapper.selectById(id);
        if (config == null || !config.getAccountId().equals(accountId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(config);
    }

    /**
     * 创建费率配置
     * 
     * @param accountId 券商账户ID
     * @param config 费率配置对象
     * @return 创建的费率配置对象
     */
    @PostMapping
    public ResponseEntity<BrokerFeeConfig> createFeeConfig(@PathVariable Long accountId, 
                                                           @RequestBody BrokerFeeConfig config) {
        // 验证账户类型是否为BROKER
        Account account = accountMapper.selectById(accountId);
        if (account == null) {
            return ResponseEntity.notFound().build();
        }
        if (!"BROKER".equals(account.getAccountType())) {
            return ResponseEntity.badRequest().build();
        }
        
        config.setAccountId(accountId);
        if (config.getIsActive() == null) {
            config.setIsActive(true);
        }
        
        brokerFeeConfigMapper.insert(config);
        return ResponseEntity.ok(config);
    }

    /**
     * 更新费率配置
     * 
     * @param accountId 券商账户ID
     * @param id 费率配置ID
     * @param config 费率配置对象
     * @return 更新后的费率配置对象
     */
    @PutMapping("/{id}")
    public ResponseEntity<BrokerFeeConfig> updateFeeConfig(@PathVariable Long accountId,
                                                           @PathVariable Long id,
                                                           @RequestBody BrokerFeeConfig config) {
        // 验证配置是否存在且属于该账户
        BrokerFeeConfig existing = brokerFeeConfigMapper.selectById(id);
        if (existing == null || !existing.getAccountId().equals(accountId)) {
            return ResponseEntity.notFound().build();
        }
        
        config.setId(id);
        config.setAccountId(accountId);
        brokerFeeConfigMapper.update(config);
        
        BrokerFeeConfig updated = brokerFeeConfigMapper.selectById(id);
        return ResponseEntity.ok(updated);
    }

    /**
     * 删除费率配置
     * 
     * @param accountId 券商账户ID
     * @param id 费率配置ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteFeeConfig(@PathVariable Long accountId, @PathVariable Long id) {
        // 验证配置是否存在且属于该账户
        BrokerFeeConfig existing = brokerFeeConfigMapper.selectById(id);
        if (existing == null || !existing.getAccountId().equals(accountId)) {
            return ResponseEntity.notFound().build();
        }
        
        brokerFeeConfigMapper.deleteById(id);
        return ResponseEntity.ok(Map.of("accountId", accountId, "id", id, "deleted", true));
    }
}
