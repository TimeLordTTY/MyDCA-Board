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
/**
 * 业务注释规范化: BrokerFeeConfigController 控制器，负责接收前端请求、读取用户上下文，并将业务处理委托给服务层。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class BrokerFeeConfigController {

    /**
     * 业务注释规范化: brokerFeeConfigMapper 金额字段，用于表达该场景下的资金规模或费用口径。
     */
    private final BrokerFeeConfigMapper brokerFeeConfigMapper;
    /**
     * 业务注释规范化: accountMapper 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final AccountMapper accountMapper;

    /**
     * 业务注释规范化: 处理 BrokerFeeConfigController 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param brokerFeeConfigMapper brokerFeeConfigMapper 金额字段，用于表达该场景下的资金规模或费用口径。
     * @param accountMapper accountMapper 业务字段，承载该对象在后端流程中的核心属性。
     */
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
    /**
     * 业务注释规范化: 查询 getFeeConfigs 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param accountId 关联账户 ID，指向承载资金、持仓或虚拟科目的账户。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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
    /**
     * 业务注释规范化: 查询 getFeeConfig 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param accountId 关联账户 ID，指向承载资金、持仓或虚拟科目的账户。
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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
    /**
     * 业务注释规范化: 创建 createFeeConfig 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param accountId 关联账户 ID，指向承载资金、持仓或虚拟科目的账户。
     * @param config config 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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
    /**
     * 业务注释规范化: 更新 updateFeeConfig 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param accountId 关联账户 ID，指向承载资金、持仓或虚拟科目的账户。
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @param config config 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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
    /**
     * 业务注释规范化: 删除 deleteFeeConfig 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param accountId 关联账户 ID，指向承载资金、持仓或虚拟科目的账户。
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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
