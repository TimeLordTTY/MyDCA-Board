package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.MonthlyBudgetDTO.*;
import com.timelordtty.dca.dto.MonthlyBudgetFact;
import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.mapper.MonthlyBudgetMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonthlyBudgetServiceTest {
    static BigDecimal n(String v) { return new BigDecimal(v); }
    static Config config(String month, String scope) {
        return new Config("月度计划",month,"CNY",scope,List.of(
            new Item("收入",Kind.INCOME,1L,n("100")),
            new Item("房租",Kind.FIXED_EXPENSE,2L,n("60")),
            new Item("消费",Kind.FLEXIBLE_EXPENSE,3L,n("30")),
            new Item("预留",Kind.RESERVE,null,n("20"))));
    }
    static MonthlyBudgetFact fact(String date,Long category,String type,String direction,String amount) {
        var f=new MonthlyBudgetFact(); f.setTxnId(UUID.randomUUID().toString());
        f.setTradeDate(date==null?null:LocalDate.parse(date)); f.setCategoryId(category);
        f.setAccountType(type); f.setPostingType(direction); f.setAmount(amount==null?null:n(amount));
        f.setCurrency("CNY"); return f;
    }
    static Comparison observe(List<MonthlyBudgetFact> facts) {
        return MonthlyBudgetService.observe(new Budget("b",config("2024-02","PERSONAL"),Instant.EPOCH),facts);
    }
    @Test void planActualNegativeSurplusAndOverspending() {
        var r=observe(List.of(fact("2024-02-01",1L,"INCOME","CREDIT","100"),
            fact("2024-02-29",2L,"EXPENSE","DEBIT","70"),
            fact("2024-02-15",3L,"EXPENSE","DEBIT","40"),
            fact("2024-02-16",3L,"EXPENSE","CREDIT","5")));
        assertEquals(Quality.OK,r.quality()); assertEquals(n("-10"),r.plannedSurplus());
        assertEquals(n("100"),r.actualIncome()); assertEquals(n("105"),r.actualExpenses());
        assertEquals(n("-5"),r.actualSurplus()); assertEquals(n("-15"),r.remainingBudget());
        assertTrue(r.overspent()); assertTrue(r.items().get(1).overspent());
        assertEquals(n("35"),r.items().get(2).actual());
        assertNull(r.items().get(3).actual()); assertNull(r.items().get(3).overspent());
        assertTrue(r.warnings().contains("计划结余为负"));
    }
    @Test void knownEmptyReadIsZeroButMissingReadAndUnmatchedDataStayUnknown() {
        assertEquals(Quality.OK,observe(List.of()).quality());
        assertEquals(n("0"),observe(List.of()).actualExpenses());
        for(var facts:Arrays.asList(null,List.of(fact("2024-02-01",null,null,null,null)),
                List.of(fact(null,1L,"INCOME","CREDIT","100")),
                List.of(fact("2024-03-01",1L,"INCOME","CREDIT","100")))) {
            var r=observe(facts); assertEquals(Quality.UNKNOWN,r.quality());
            assertNull(r.actualSurplus()); assertNull(r.remainingBudget()); assertNull(r.overspent());
        }
        var r=observe(List.of(fact("2024-02-01",1L,"INCOME","CREDIT","50"),
                fact("2024-02-02",99L,"EXPENSE","DEBIT","10")));
        assertEquals(Quality.PARTIAL,r.quality()); assertEquals(1,r.unmatchedPostings());
        assertEquals(n("50"),r.items().get(0).knownActual()); assertNull(r.actualIncome());
    }
    @Test void missingAmountsCurrenciesAndDirectionsDoNotBecomeZero() {
        for(var bad:List.of(fact("2024-02-02",2L,"EXPENSE","DEBIT",null),
                fact("2024-02-02",2L,"EXPENSE","INVALID","10"),
                fact("2024-02-02",2L,"EXPENSE","DEBIT","-10"))) {
            var r=observe(List.of(fact("2024-02-01",1L,"INCOME","CREDIT","100"),bad));
            assertEquals(Quality.PARTIAL,r.quality()); assertNull(r.items().get(1).actual());
            assertNull(r.items().get(1).remaining()); assertNull(r.actualSurplus());
        }
        var usd=fact("2024-02-02",2L,"EXPENSE","DEBIT","10"); usd.setCurrency("USD");
        assertEquals(Quality.UNKNOWN,observe(List.of(usd)).quality());
        var refund=fact("2024-02-01",1L,"INCOME","DEBIT","10");
        assertEquals(n("-10"),observe(List.of(refund)).actualIncome());
    }
    @Test void isolationMetadataEditingMonthBoundariesAndNoFinancialWrites() throws Exception {
        var store=mock(MonthlyBudgetMapper.class); var users=mock(UserService.class);
        var families=mock(FamilyService.class); var u=new AuthResponse.UserInfo();
        u.setId(7L); u.setFamilyId(9L); when(users.getCurrentUser()).thenReturn(u);
        var service=new MonthlyBudgetService(store,users,families);
        Map<String,String> saved=new HashMap<>();
        when(store.insert(anyString(),eq(7L),eq(9L),anyString())).thenAnswer(a->{saved.put(a.getArgument(0),a.getArgument(3));return 1;});
        when(store.detail(anyString(),eq(7L),eq(9L))).thenAnswer(a->saved.get(a.getArgument(0)));
        when(store.update(anyString(),eq(7L),eq(9L),anyString())).thenAnswer(a->{saved.put(a.getArgument(0),a.getArgument(3));return 1;});
        var b=service.create(config("2024-02","PERSONAL"));
        assertEquals(b.createdAt(),service.edit(b.id(),b.config()).createdAt());
        clearInvocations(store);
        service.comparison(b.id());
        verify(store).detail(b.id(),7L,9L);
        verify(store).facts(7L,null,LocalDate.of(2024,2,1),LocalDate.of(2024,3,1));
        verifyNoMoreInteractions(store);
        assertTrue(MonthlyBudgetService.class.getMethod("comparison",String.class).getAnnotation(Transactional.class).readOnly());
        final String personalId=b.id();
        u.setId(8L); assertThrows(IllegalArgumentException.class,()->service.comparison(personalId));
        u.setId(7L); u.setFamilyId(10L); assertThrows(IllegalArgumentException.class,()->service.edit(personalId,config("2024-02","PERSONAL")));
        u.setFamilyId(9L); b=service.create(config("2026-12","FAMILY"));
        clearInvocations(store); service.comparison(b.id());
        verify(store).facts(7L,9L,LocalDate.of(2026,12,1),LocalDate.of(2027,1,1));
        verify(families,atLeastOnce()).assertAdmin(7L,9L);
        doThrow(new IllegalArgumentException("无权限")).when(families).assertAdmin(7L,9L);
        final String familyId=b.id(); assertThrows(IllegalArgumentException.class,()->service.comparison(familyId));
    }
    @Test void invalidConfigurationAndDuplicateCategoriesAreRejected() {
        var store=mock(MonthlyBudgetMapper.class); var users=mock(UserService.class);
        var u=new AuthResponse.UserInfo(); u.setId(7L); when(users.getCurrentUser()).thenReturn(u);
        var service=new MonthlyBudgetService(store,users,mock(FamilyService.class));
        for(var month:Arrays.asList(null,"2024-2","2024-13","0999-12","9999-12"))
            assertThrows(IllegalArgumentException.class,()->service.create(config(month,"PERSONAL")));
        for(var amount:Arrays.asList(null,n("-1"),n("1.001"),n("1000000000000000000"))) {
            var c=new Config("预算","2024-02","CNY","PERSONAL",List.of(new Item("消费",Kind.FIXED_EXPENSE,2L,amount)));
            assertThrows(IllegalArgumentException.class,()->service.create(c));
        }
        var duplicate=new Config("预算","2024-02","CNY","PERSONAL",List.of(
            new Item("固定",Kind.FIXED_EXPENSE,2L,n("10")),new Item("弹性",Kind.FLEXIBLE_EXPENSE,2L,n("20"))));
        assertThrows(IllegalArgumentException.class,()->service.create(duplicate));
        assertThrows(IllegalArgumentException.class,()->service.create(config("2024-02","OTHER")));
        assertThrows(IllegalArgumentException.class,()->service.list(Integer.MAX_VALUE,50));
        assertThrows(IllegalArgumentException.class,()->service.list(0,51)); verifyNoInteractions(store);
    }
    @Test void sqlRetainsMissingEvidenceAndConstrainsOwnerFamilyAndHalfOpenMonth() throws Exception {
        var cfg=new org.apache.ibatis.session.Configuration();
        cfg.addMapper(MonthlyBudgetMapper.class);
        var args=new HashMap<String,Object>(); args.put("user",7L); args.put("family",9L);
        args.put("start",LocalDate.of(2024,2,1)); args.put("end",LocalDate.of(2024,3,1));
        var bound=cfg.getMappedStatement(MonthlyBudgetMapper.class.getName()+".facts").getBoundSql(args);
        String sql=bound.getSql();
        assertTrue(sql.contains("LEFT JOIN ledger_posting"));
        assertTrue(sql.contains("lt.user_id=? AND lt.family_id <=> ?"));
        assertTrue(sql.contains("lt.trade_date >= ? AND lt.trade_date < ?"));
        assertTrue(sql.contains("lt.trade_date IS NULL"));
        assertTrue(sql.contains("lt.status='CONFIRMED'"));
        assertTrue(sql.contains("COALESCE(lt.is_reversed,0)=0"));
        assertTrue(sql.contains("!= 'REVERSAL'"));
        assertEquals(List.of("user","family","start","end"),bound.getParameterMappings().stream()
            .map(org.apache.ibatis.mapping.ParameterMapping::getProperty).toList());
        for(var name:List.of("detail","list","update")) {
            String scoped=cfg.getMappedStatement(MonthlyBudgetMapper.class.getName()+"."+name).getBoundSql(args).getSql();
            assertTrue(scoped.contains("owner_user_id=? AND owner_family_id <=> ?"));
        }
        var migration=java.nio.file.Files.readString(java.nio.file.Path.of("migrations/20261002_monthly_budget.sql"));
        assertEquals(migration,java.nio.file.Files.readString(java.nio.file.Path.of("sql/initsql/monthly_budget.sql")));
        assertTrue(migration.contains("CREATE TABLE monthly_budget"));
        assertFalse(migration.contains("ALTER TABLE"));
    }

}
