package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.*;
import com.timelordtty.dca.dto.RiskWatchDTO.*;
import com.timelordtty.dca.mapper.RiskWatchMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RiskWatchServiceTest {
    static BigDecimal n(String s) { return new BigDecimal(s); }
    static Config config(Type type,String threshold) {
        return new Config("PERSONAL",type,1L,"EQUITY",n(threshold),n("0.5"),Direction.ABOVE,Severity.WARNING,"观察",false);
    }
    static FinanceRadarDTO facts(String status,BigDecimal total,String marketStatus) {
        return new FinanceRadarDTO(LocalDate.of(2026,10,2),"PERSONAL",
                new FinanceRadarDTO.Assets(status,n("200"),n("600"),n("800"),n("0"),total,total),null,
                List.of(new FinanceRadarDTO.MarketFact(1L,marketStatus,LocalDate.of(2026,9,29),LocalDate.of(2026,9,28),"OK",null)),List.of());
    }
    static HoldingService.HoldingInfo holding(Long product,String value,String cost) {
        var h=new HoldingService.HoldingInfo(); h.setProductId(product); h.setAssetType("EQUITY");
        h.setTotalShares(n("10")); h.setMarketValue(n(value)); h.setTotalCost(n(cost)); return h;
    }
    @Test void ratiosUnknownAndStaleness() {
        var positions=List.of(holding(1L,"300","200"),holding(1L,"300","200"),holding(2L,"200","200"));
        var f=facts("OK",n("1000"),"OK");
        assertEquals(0,n("0.6").compareTo(RiskWatchService.observe(config(Type.CONCENTRATION,"0.6"),f,positions,null)));
        assertEquals(0,n("0.3").compareTo(RiskWatchService.observe(config(Type.ALLOCATION_DEVIATION,"0.3"),f,positions,null)));
        assertEquals(0,n("0.5").compareTo(RiskWatchService.observe(config(Type.RETURN,"0.5"),f,positions,null)));
        assertEquals(n("4"),RiskWatchService.observe(config(Type.STALE,"4"),f,positions,null));
        assertNull(RiskWatchService.observe(config(Type.CONCENTRATION,"0.6"),facts("UNKNOWN",null,"UNKNOWN"),positions,null));
        assertNull(RiskWatchService.observe(config(Type.RETURN,"0.5"),facts("OK",n("1000"),"UNKNOWN"),positions,null));
        assertNull(RiskWatchService.observe(config(Type.DRAWDOWN,"0.1"),f,positions,null));
        var indicator=new com.timelordtty.dca.model.IndicatorDaily(); indicator.setTradeDate(f.date()); indicator.setDrawdownFromPeak(n("-0.2"));
        assertEquals(n("0.2"),RiskWatchService.observe(config(Type.DRAWDOWN,"0.1"),f,positions,indicator));
        indicator.setTradeDate(f.date().minusDays(4)); assertNull(RiskWatchService.observe(config(Type.DRAWDOWN,"0.1"),f,positions,indicator));
    }
    @Test void scopeBoundaryIdempotencyAndNoFinancialWrites() throws Exception {
        var store=mock(RiskWatchMapper.class); var users=mock(UserService.class); var families=mock(FamilyService.class);
        var radar=mock(FinanceRadarService.class); var holdings=mock(HoldingService.class); var indicators=mock(IndicatorService.class);
        var user=new AuthResponse.UserInfo(); user.setId(7L); user.setFamilyId(9L); when(users.getCurrentUser()).thenReturn(user);
        var service=new RiskWatchService(store,users,families,radar,holdings,indicators);
        Map<String,String> rules=new HashMap<>(), snapshots=new HashMap<>();
        when(store.insertRule(anyString(),eq(7L),eq(9L),anyString())).thenAnswer(a->{rules.put(a.getArgument(0),a.getArgument(3));return 1;});
        when(store.rule(anyString(),eq(7L),eq(9L))).thenAnswer(a->rules.get(a.getArgument(0)));
        when(store.snapshot(anyString(),eq(7L),eq(9L))).thenAnswer(a->snapshots.get(a.getArgument(0)));
        when(store.insertSnapshot(anyString(),anyString(),eq(7L),eq(9L),anyString())).thenAnswer(a->{snapshots.put(a.getArgument(0),a.getArgument(4));return 1;});
        when(radar.getRadar(7L,9L,"PERSONAL")).thenReturn(facts("OK",n("1000"),"OK"));
        when(holdings.calculateHoldings(7L,null)).thenReturn(List.of(holding(1L,"600","400"),holding(2L,"200","200")));
        var rule=service.create(config(Type.CONCENTRATION,"0.6")); var first=service.evaluate(rule.id());
        assertTrue(first.matched()); assertEquals(Severity.WARNING,first.severity()); assertEquals(RiskWatchService.DISCLAIMER,first.disclaimer());
        assertEquals(first,service.evaluate(rule.id())); assertEquals(1,snapshots.size());
        verify(store,times(1)).insertSnapshot(anyString(),anyString(),anyLong(),anyLong(),anyString());
        verify(holdings,times(2)).calculateHoldings(7L,null); verifyNoMoreInteractions(holdings); verifyNoInteractions(indicators);
        user.setId(8L); assertThrows(IllegalArgumentException.class,()->service.evaluate(rule.id()));
        user.setId(7L); user.setFamilyId(10L); assertThrows(IllegalArgumentException.class,()->service.history(rule.id(),0,20));
        user.setFamilyId(9L);
        Config family=new Config("FAMILY",Type.NOTE,null,null,null,null,null,Severity.INFO,"备注",true);
        var familyRule=service.create(family); verify(families).assertAdmin(7L,9L);
        when(radar.getRadar(7L,9L,"FAMILY")).thenReturn(facts("OK",n("1000"),"OK"));
        when(holdings.calculateHoldings(null,9L)).thenReturn(List.of());
        var muted=service.evaluate(familyRule.id()); assertEquals("OK",muted.status()); assertTrue(muted.matched());
        verify(holdings).calculateHoldings(null,9L);
    }
    @Test void unknownBelowBoundaryAndConcurrentReuse() throws Exception {
        var store=mock(RiskWatchMapper.class); var users=mock(UserService.class); var radar=mock(FinanceRadarService.class);
        var holdings=mock(HoldingService.class); var user=new AuthResponse.UserInfo(); user.setId(1L);
        when(users.getCurrentUser()).thenReturn(user);
        var service=new RiskWatchService(store,users,mock(FamilyService.class),radar,holdings,mock(IndicatorService.class));
        var json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var c=new Config("PERSONAL",Type.RETURN,1L,null,n("0.5"),null,Direction.BELOW,Severity.CRITICAL,null,false);
        var rule=new Rule("test",c,java.time.Instant.EPOCH,RiskWatchService.DISCLAIMER);
        when(store.rule("test",1L,null)).thenReturn(json.writeValueAsString(rule));
        when(radar.getRadar(1L,null,"PERSONAL")).thenReturn(facts("OK",n("1000"),"OK"));
        when(holdings.calculateHoldings(1L,null)).thenReturn(List.of(holding(1L,"600","400")));
        Map<String,String> saved=new HashMap<>();
        when(store.snapshot(anyString(),eq(1L),isNull())).thenAnswer(a->saved.get(a.getArgument(0)));
        when(store.insertSnapshot(anyString(),eq("test"),eq(1L),isNull(),anyString())).thenAnswer(a->{saved.put(a.getArgument(0),a.getArgument(4));throw new DuplicateKeyException("race");});
        assertTrue(service.evaluate("test").matched());
        when(radar.getRadar(1L,null,"PERSONAL")).thenReturn(facts("UNKNOWN",null,"UNKNOWN"));
        var unknown=service.evaluate("test"); assertEquals("UNKNOWN",unknown.status()); assertNull(unknown.observedValue()); assertFalse(unknown.matched());
        assertEquals(2,saved.size());
        assertThrows(IllegalArgumentException.class,()->service.create(config(Type.CONCENTRATION,"1.1")));
        assertThrows(IllegalArgumentException.class,()->service.list(-1,20));
    }
    @Test void alertLifecycleMuteAckUnknownAndOwnerIsolation() throws Exception {
        var store=mock(RiskWatchMapper.class); var users=mock(UserService.class);
        var radar=mock(FinanceRadarService.class); var holdings=mock(HoldingService.class);
        var user=new AuthResponse.UserInfo(); user.setId(7L); user.setFamilyId(9L);
        when(users.getCurrentUser()).thenReturn(user);
        var service=new RiskWatchService(store,users,mock(FamilyService.class),radar,holdings,mock(IndicatorService.class));
        var json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var rule=new Rule("r",config(Type.STALE,"4"),java.time.Instant.EPOCH,RiskWatchService.DISCLAIMER);
        when(store.rule("r",7L,9L)).thenReturn(json.writeValueAsString(rule));
        Map<String,String> snapshots=new HashMap<>(), events=new LinkedHashMap<>();
        when(store.snapshot(anyString(),eq(7L),eq(9L))).thenAnswer(a->snapshots.get(a.getArgument(0)));
        when(store.insertSnapshot(anyString(),eq("r"),eq(7L),eq(9L),anyString())).thenAnswer(a->{snapshots.put(a.getArgument(0),a.getArgument(4));return 1;});
        when(store.insertEvent(anyString(),eq("r"),eq(7L),eq(9L),anyString())).thenAnswer(a->{events.putIfAbsent(a.getArgument(0),a.getArgument(4));return 1;});
        when(store.events(eq("r"),eq(7L),eq(9L),anyBoolean(),eq(0),eq(20))).thenAnswer(a->new ArrayList<>(events.values()));
        when(radar.getRadar(7L,9L,"PERSONAL")).thenReturn(facts("OK",n("1000"),"OK"));
        when(holdings.calculateHoldings(7L,null)).thenReturn(List.of());
        var snapshot=service.evaluate("r"); service.evaluate("r"); assertEquals(1,events.size());
        String fp=RiskWatchService.fingerprint(snapshot);
        var mutedConfig=new Config("PERSONAL",Type.STALE,1L,"EQUITY",n("4"),n("0.5"),Direction.ABOVE,Severity.WARNING,"观察",true);
        when(store.rule("r",7L,9L)).thenReturn(json.writeValueAsString(new Rule("r",mutedConfig,rule.createdAt(),rule.disclaimer())));
        assertEquals(snapshot,service.evaluate("r")); assertEquals(1,events.size());
        when(store.rule("r",7L,9L)).thenReturn(json.writeValueAsString(rule));
        assertEquals(EventState.OPEN,service.events("r",true,0,20).get(0).state());
        var until=java.time.Instant.now().plusSeconds(3600); service.mute("r",until);
        verify(store).mute("r",7L,9L,until);
        when(store.mutedUntil("r",7L,9L)).thenReturn(until);
        var muted=service.events("r",true,0,20).get(0); assertEquals(EventState.MUTED,muted.state()); assertFalse(muted.visible()); assertTrue(muted.evidence().matched());
        when(store.acknowledge(fp,"r",7L,9L)).thenReturn(1); service.acknowledge("r",fp);
        var ack=java.time.Instant.now(); when(store.acknowledgedAt(fp,"r",7L,9L)).thenReturn(ack);
        when(store.mutedUntil("r",7L,9L)).thenReturn(ack.minusSeconds(1));
        assertEquals(EventState.ACKNOWLEDGED,service.events("r",true,0,20).get(0).state());
        when(radar.getRadar(7L,9L,"PERSONAL")).thenReturn(new FinanceRadarDTO(LocalDate.of(2026,10,2),"PERSONAL",facts("UNKNOWN",null,"UNKNOWN").assets(),null,List.of(),List.of()));
        assertEquals("UNKNOWN",service.evaluate("r").status()); verify(store,never()).resolve(anyString(),anyLong(),anyLong());
        var good=new FinanceRadarDTO(LocalDate.of(2026,10,2),"PERSONAL",facts("OK",n("1000"),"OK").assets(),null,List.of(new FinanceRadarDTO.MarketFact(1L,"OK",LocalDate.of(2026,10,2),LocalDate.of(2026,10,2),"OK",null)),List.of());
        when(radar.getRadar(7L,9L,"PERSONAL")).thenReturn(good); assertFalse(service.evaluate("r").matched());
        verify(store).resolve("r",7L,9L); when(store.resolvedAt(fp,"r",7L,9L)).thenReturn(ack);
        assertEquals(EventState.RESOLVED,service.events("r",false,0,20).get(0).state()); assertEquals(1,events.size());
        user.setId(8L); assertThrows(IllegalArgumentException.class,()->service.acknowledge("r",fp));
        assertThrows(IllegalArgumentException.class,()->service.mute("r",until)); assertThrows(IllegalArgumentException.class,()->service.events("r",true,0,20));
        user.setId(7L); assertThrows(IllegalArgumentException.class,()->service.mute("r",java.time.Instant.EPOCH));
        verify(holdings,times(5)).calculateHoldings(7L,null); verifyNoMoreInteractions(holdings);
    }
    @Test void migrationIsManualMetadataOnly() throws Exception {
        String sql=java.nio.file.Files.readString(java.nio.file.Path.of("migrations/20261002_risk_watch.sql"));
        assertTrue(sql.contains("Manual deployment only")); assertEquals(2,sql.split("CREATE TABLE",-1).length-1);
        assertFalse(sql.matches("(?is).*\\b(UPDATE|DELETE|ALTER|INSERT)\\b.*"));
    }
}
