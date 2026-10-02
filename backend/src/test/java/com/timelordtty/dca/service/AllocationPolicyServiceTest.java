package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.AllocationPolicyDTO.*;
import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.dto.FinanceRadarDTO;
import com.timelordtty.dca.mapper.AllocationPolicyMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AllocationPolicyServiceTest {
    static BigDecimal n(String s) { return new BigDecimal(s); }
    static Config config(String scope,Boolean enabled,BigDecimal threshold,List<BigDecimal> stages) {
        return new Config(scope,1L,null,n("0.6"),n("0.5"),n("0.7"),threshold,stages,enabled,"用户配置");
    }
    static Policy policy(Config c) { return new Policy("p",c,Instant.EPOCH,RiskWatchService.DISCLAIMER); }
    static FinanceRadarDTO facts(String market) {
        var date=LocalDate.of(2026,10,2);
        return new FinanceRadarDTO(date,"PERSONAL",new FinanceRadarDTO.Assets("OK",n("200"),n("600"),n("800"),n("0"),n("1000"),n("1000")),null,
                List.of(new FinanceRadarDTO.MarketFact(1L,market,date,date,null,null),
                        new FinanceRadarDTO.MarketFact(2L,"OK",date,date,null,null)),List.of());
    }
    static HoldingService.HoldingInfo holding(long id,String value,String cost) {
        var h=new HoldingService.HoldingInfo(); h.setProductId(id); h.setAssetType("EQUITY");
        h.setTotalShares(n("10")); h.setMarketValue(n(value)); h.setTotalCost(cost==null?null:n(cost)); return h;
    }
    @Test void inclusiveBoundariesAndUnroundedDeviation() {
        var p=policy(config("PERSONAL",true,null,null));
        for(var row:List.of(new String[]{"500","300","IN_RANGE"},new String[]{"700","100","IN_RANGE"},
                new String[]{"499.9999999999","300.0000000001","BELOW_BAND"},new String[]{"700.0000000001","99.9999999999","ABOVE_BAND"})) {
            var result=AllocationPolicyService.observe(p,facts("OK"),List.of(holding(1,row[0],"400"),holding(2,row[1],"200")));
            assertEquals(Status.valueOf(row[2]),result.status()); assertFalse(result.reason().isBlank());
        }
    }
    @Test void explicitReturnAndStagesAtEquality() {
        var positions=List.of(holding(1,"600","400"),holding(2,"200","200"));
        var result=AllocationPolicyService.observe(policy(config("PERSONAL",true,n("0.5"),null)),facts("OK"),positions);
        assertEquals(Status.TAKE_PROFIT_WATCH,result.status()); assertEquals(0,n("0.5").compareTo(result.returnRate()));
        result=AllocationPolicyService.observe(policy(config("PERSONAL",true,null,List.of(n("0.2"),n("0.5"),n("0.8")))),facts("OK"),positions);
        assertEquals(Status.TAKE_PROFIT_WATCH,result.status()); assertEquals(List.of(n("0.2"),n("0.5")),result.reachedTakeProfitThresholds());
        assertEquals(Status.IN_RANGE,AllocationPolicyService.observe(policy(config("PERSONAL",true,n("0.6"),null)),facts("OK"),positions).status());
    }
    @Test void unknownIsNeverZeroOrNormal() {
        var p=policy(config("PERSONAL",true,n("0.5"),null));
        var h=holding(1,"600",null); var other=holding(2,"200","200");
        var r=AllocationPolicyService.observe(p,facts("OK"),List.of(h,other));
        assertEquals(Status.UNKNOWN,r.status()); assertNull(r.returnRate()); assertNotNull(r.allocation());
        assertEquals(Status.UNKNOWN,AllocationPolicyService.observe(policy(config("PERSONAL",true,null,null)),facts("OK"),List.of(h,other)).status());
        h.setTotalCost(n("400"));
        assertEquals(Status.UNKNOWN,AllocationPolicyService.observe(p,facts("UNKNOWN"),List.of(h,other)).status());
        assertEquals(Status.UNKNOWN,AllocationPolicyService.observe(p,facts("STALE"),List.of(h,other)).status());
        other.setAssetType(null);
        assertEquals(Status.UNKNOWN,AllocationPolicyService.observe(p,facts("OK"),List.of(h,other)).status());
        other.setAssetType("EQUITY"); other.setMarketValue(null);
        assertEquals(Status.UNKNOWN,AllocationPolicyService.observe(p,facts("OK"),List.of(h,other)).status());
        assertEquals(Status.UNKNOWN,AllocationPolicyService.observe(p,null,List.of()).status());
        assertEquals(Status.UNKNOWN,AllocationPolicyService.observe(p,facts("OK"),List.of()).status());
    }
    @Test void categoryAndCashTargets() {
        var c=new Config("PERSONAL",null,"EQUITY",n("0.8"),n("0.7"),n("0.9"),null,null,true,null);
        var positions=List.of(holding(1,"600","400"),holding(2,"200","200"));
        assertEquals(Status.IN_RANGE,AllocationPolicyService.observe(policy(c),facts("OK"),positions).status());
        c=new Config("PERSONAL",null,"CASH",n("0.2"),n("0.2"),n("0.2"),null,null,true,null);
        assertEquals(Status.IN_RANGE,AllocationPolicyService.observe(policy(c),facts("OK"),positions).status());
    }
    @Test void ownerFamilyIsolationEnableDisableAndReadOnly() throws Exception {
        var store=mock(AllocationPolicyMapper.class); var users=mock(UserService.class); var families=mock(FamilyService.class);
        var radar=mock(FinanceRadarService.class); var holdings=mock(HoldingService.class);
        var u=new AuthResponse.UserInfo(); u.setId(7L); u.setFamilyId(9L); when(users.getCurrentUser()).thenReturn(u);
        var service=new AllocationPolicyService(store,users,families,radar,holdings);
        Map<String,String> saved=new HashMap<>();
        when(store.insert(anyString(),eq(7L),eq(9L),anyString())).thenAnswer(a->{saved.put(a.getArgument(0),a.getArgument(3));return 1;});
        when(store.detail(anyString(),eq(7L),eq(9L))).thenAnswer(a->saved.get(a.getArgument(0)));
        when(store.update(anyString(),eq(7L),eq(9L),anyString())).thenAnswer(a->{saved.put(a.getArgument(0),a.getArgument(3));return 1;});
        var p=service.create(config("PERSONAL",false,null,null)); clearInvocations(store);
        assertEquals(Status.UNKNOWN,service.evaluate(p.id()).status()); verifyNoInteractions(radar,holdings);
        verify(store).detail(p.id(),7L,9L); verifyNoMoreInteractions(store);
        service.edit(p.id(),config("PERSONAL",true,null,null));
        when(radar.getRadar(7L,9L,"PERSONAL")).thenReturn(facts("OK"));
        when(holdings.calculateHoldings(7L,null)).thenReturn(List.of(holding(1,"600","400"),holding(2,"200","200")));
        clearInvocations(store);
        assertEquals(Status.IN_RANGE,service.evaluate(p.id()).status()); service.evaluate(p.id());
        verify(store,times(2)).detail(p.id(),7L,9L); verifyNoMoreInteractions(store);
        verify(radar,times(2)).getRadar(7L,9L,"PERSONAL"); verifyNoMoreInteractions(radar);
        verify(holdings,times(2)).calculateHoldings(7L,null); verifyNoMoreInteractions(holdings);
        u.setId(8L); assertThrows(IllegalArgumentException.class,()->service.evaluate(p.id()));
        assertThrows(IllegalArgumentException.class,()->service.edit(p.id(),config("PERSONAL",true,null,null)));
        u.setId(7L); u.setFamilyId(10L); assertThrows(IllegalArgumentException.class,()->service.detail(p.id()));
        u.setFamilyId(9L); var family=service.create(config("FAMILY",true,null,null));
        when(radar.getRadar(7L,9L,"FAMILY")).thenReturn(facts("OK"));
        when(holdings.calculateHoldings(null,9L)).thenReturn(List.of(holding(1,"600","400"),holding(2,"200","200")));
        assertEquals(Status.IN_RANGE,service.evaluate(family.id()).status()); verify(families,times(2)).assertAdmin(7L,9L);
        verify(holdings).calculateHoldings(null,9L);
        doThrow(new IllegalArgumentException("无权访问")).when(families).assertAdmin(7L,9L);
        assertThrows(IllegalArgumentException.class,()->service.evaluate(family.id()));
        u.setFamilyId(null); assertThrows(IllegalArgumentException.class,()->service.create(config("FAMILY",true,null,null)));
    }
    @Test void rejectsImplicitOrInvalidConfiguration() {
        var users=mock(UserService.class); when(users.getCurrentUser()).thenReturn(new AuthResponse.UserInfo());
        var store=mock(AllocationPolicyMapper.class);
        var service=new AllocationPolicyService(store,users,mock(FamilyService.class),mock(FinanceRadarService.class),mock(HoldingService.class));
        assertThrows(IllegalArgumentException.class,()->service.create(config("PERSONAL",null,null,null)));
        assertThrows(IllegalArgumentException.class,()->service.create(config("PERSONAL",true,n("-0.1"),null)));
        assertThrows(IllegalArgumentException.class,()->service.create(config("PERSONAL",true,null,List.of(n("0.5"),n("0.5")))));
        assertThrows(IllegalArgumentException.class,()->service.create(new Config("PERSONAL",1L,null,null,null,null,null,null,true,null)));
        assertThrows(IllegalArgumentException.class,()->service.create(new Config("PERSONAL",1L,null,n("0.6"),n("0.7"),n("1"),null,null,true,null)));
        assertThrows(IllegalArgumentException.class,()->service.list(-1,20)); verifyNoInteractions(store);
    }
    @Test void mapperScopeAndManualMigration() throws Exception {
        for(var method:AllocationPolicyMapper.class.getDeclaredMethods()) {
            var select=method.getAnnotation(org.apache.ibatis.annotations.Select.class);
            var update=method.getAnnotation(org.apache.ibatis.annotations.Update.class);
            var sql=select!=null?select.value()[0]:update!=null?update.value()[0]:null;
            if(sql!=null) { assertTrue(sql.contains("owner_user_id=#{user}")); assertTrue(sql.contains("owner_family_id <=> #{family}")); }
        }
        String sql=java.nio.file.Files.readString(java.nio.file.Path.of("migrations/20261002_allocation_policy.sql"));
        assertTrue(sql.contains("Manual deployment only")); assertEquals(1,sql.split("CREATE TABLE",-1).length-1);
        assertFalse(sql.matches("(?is).*\\b(UPDATE|DELETE|ALTER|INSERT)\\b.*"));
        String init=java.nio.file.Files.readString(java.nio.file.Path.of("sql/initsql/allocation_policy.sql"));
        assertEquals(sql.substring(sql.indexOf("CREATE TABLE")),init.substring(init.indexOf("CREATE TABLE")));
        assertTrue(AllocationPolicyService.class.getMethod("evaluate",String.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class).readOnly());
    }
}
