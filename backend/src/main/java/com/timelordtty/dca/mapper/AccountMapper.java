package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface AccountMapper {
    Account selectById(@Param("id") Long id);
    List<Account> selectByIds(@Param("ids") List<Long> ids);
    Account selectByCode(@Param("accountCode") String accountCode);
    List<Account> selectByOwner(@Param("ownerUserId") Long ownerUserId, @Param("ownerFamilyId") Long ownerFamilyId);
    List<Account> selectVirtualAccountsByOwner(@Param("ownerUserId") Long ownerUserId,
                                               @Param("ownerFamilyId") Long ownerFamilyId,
                                               @Param("virtualSubtype") String virtualSubtype);
    List<Account> selectChildren(@Param("parentAccountId") Long parentAccountId);
    List<Account> selectLeafAccounts(@Param("ownerUserId") Long ownerUserId, @Param("ownerFamilyId") Long ownerFamilyId);
    List<Account> selectByLinkedProduct(@Param("productId") Long productId, 
                                       @Param("ownerUserId") Long ownerUserId, 
                                       @Param("ownerFamilyId") Long ownerFamilyId);
    Account selectByLinkedProductId(@Param("productId") Long productId);
    int insert(Account account);
    int update(Account account);
    int updateBalance(@Param("id") Long id, @Param("balance") BigDecimal balance);
    int updateReservedAmount(@Param("id") Long id, @Param("reservedAmount") BigDecimal reservedAmount);
    int updateInitialShares(@Param("id") Long id, @Param("initialShares") BigDecimal initialShares);
}

