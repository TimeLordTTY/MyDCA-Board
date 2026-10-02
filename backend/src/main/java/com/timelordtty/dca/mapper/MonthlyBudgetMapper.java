package com.timelordtty.dca.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;

/** Authenticated owner AND current family constrain every budget access. */
@Mapper
public interface MonthlyBudgetMapper {
    @Insert("INSERT INTO monthly_budget(id,owner_user_id,owner_family_id,payload) VALUES(#{id},#{user},#{family},#{payload})")
    int insert(@Param("id") String id, @Param("user") Long user, @Param("family") Long family, @Param("payload") String payload);
    @Select("SELECT payload FROM monthly_budget WHERE id=#{id} AND owner_user_id=#{user} AND owner_family_id <=> #{family}")
    String detail(@Param("id") String id, @Param("user") Long user, @Param("family") Long family);
    @Select("SELECT payload FROM monthly_budget WHERE owner_user_id=#{user} AND owner_family_id <=> #{family} ORDER BY created_at DESC,id DESC LIMIT #{size} OFFSET #{offset}")
    List<String> list(@Param("user") Long user, @Param("family") Long family, @Param("offset") int offset, @Param("size") int size);
    @Update("UPDATE monthly_budget SET payload=#{payload} WHERE id=#{id} AND owner_user_id=#{user} AND owner_family_id <=> #{family}")
    int update(@Param("id") String id, @Param("user") Long user, @Param("family") Long family, @Param("payload") String payload);
    @Select("""
        SELECT lt.txn_id AS txnId, lt.category_id AS categoryId, lt.trade_date AS tradeDate,
               lp.account_type AS accountType, lp.posting_type AS postingType,
               lp.amount AS amount, lp.currency AS currency
        FROM ledger_txn lt
        LEFT JOIN ledger_posting lp ON lp.txn_id=lt.txn_id AND lp.account_type IN ('INCOME','EXPENSE')
        WHERE lt.status='CONFIRMED' AND COALESCE(lt.is_reversed,0)=0
          AND COALESCE(lt.relation_type,'NONE') != 'REVERSAL'
          AND lt.txn_type IN ('INCOME','EXPENSE','REIMBURSE_IN','REIMBURSE_OUT')
          AND lt.user_id=#{user} AND lt.family_id <=> #{family}
          AND (lt.trade_date IS NULL OR (lt.trade_date >= #{start} AND lt.trade_date < #{end}))
        ORDER BY lt.txn_id, lp.id
        """)
    List<com.timelordtty.dca.dto.MonthlyBudgetFact> facts(@Param("user") Long user,
        @Param("family") Long family, @Param("start") java.time.LocalDate start,
        @Param("end") java.time.LocalDate end);
}
