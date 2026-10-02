package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.GoalTrackingDTO.*;
import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.dto.FinanceRadarDTO;
import com.timelordtty.dca.mapper.GoalTrackingMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoalTrackingServiceTest {
    static final LocalDate DATE=LocalDate.of(2026,10,2);
    static BigDecimal n(String s) { return new BigDecimal(s); }
    static Config config(String scope,String currency,LocalDate date,State state,Measure measure) {
        return new Config("长期目标",n("100"),date,currency,scope,measure,state,"备注");
    }
    static FinanceRadarDTO facts(BigDecimal cash,BigDecimal position,BigDecimal total) {
        return new FinanceRadarDTO(DATE,"PERSONAL",new FinanceRadarDTO.Assets(total==null?"UNKNOWN":"OK",
                cash,null,position,null,total,null),null,List.of(),List.of());
    }
    static Progress observe(Config c,FinanceRadarDTO f) {
        return GoalTrackingService.observe(new Goal("g",c,Instant.EPOCH),f);
    }
    @Test void completionAndDateBoundariesIncludingOverachievement() {
        for(var state:State.values()) for(var amount:List.of("0","99.99999999999","100","125")) {
            var r=observe(config("PERSONAL","CNY",DATE,state,Measure.TOTAL_ASSETS),facts(n(amount),n("0"),n(amount)));
            assertEquals(Quality.OK,r.quality()); assertEquals(n(amount).compareTo(n("100"))>=0,r.completed());
            assertEquals(0,r.daysRemaining()); assertFalse(r.overdue());
            assertEquals(0,n(amount).divide(n("100"),12,java.math.RoundingMode.HALF_UP).compareTo(r.completionRate()));
        }
        var f=facts(n("10"),n("0"),n("10"));
        assertTrue(observe(config("PERSONAL","CNY",DATE.minusDays(1),State.ACTIVE,Measure.CASH),f).overdue());
        assertEquals(-1,observe(config("PERSONAL","CNY",DATE.minusDays(1),State.ACTIVE,Measure.CASH),f).daysRemaining());
        assertEquals(1,observe(config("PERSONAL","CNY",DATE.plusDays(1),State.ACTIVE,Measure.CASH),f).daysRemaining());
    }
    @Test void partialUnknownCurrenciesAndInconsistentTotals() {
        var c=config("PERSONAL","CNY",DATE,State.ACTIVE,Measure.TOTAL_ASSETS);
        var r=observe(c,facts(n("0"),null,null));
        assertEquals(Quality.PARTIAL,r.quality()); assertEquals(n("0"),r.knownValue());
        assertNull(r.currentValue()); assertNull(r.completionRate()); assertNull(r.completed());
        for(var f:Arrays.asList(null,facts(null,null,null),facts(n("10"),n("20"),n("40")))) {
            r=observe(c,f); assertEquals(Quality.UNKNOWN,r.quality()); assertNull(r.completionRate());
        }
        assertEquals(Quality.UNKNOWN,observe(config("PERSONAL","USD",DATE,State.ACTIVE,Measure.CASH),facts(n("50"),null,null)).quality());
        assertEquals(Quality.UNKNOWN,observe(config("FAMILY","CNY",DATE,State.ACTIVE,Measure.CASH),facts(n("50"),null,null)).quality());
        assertEquals(Quality.OK,observe(config("PERSONAL","CNY",DATE,State.ACTIVE,Measure.CASH),facts(n("50"),null,null)).quality());
        assertEquals(Quality.OK,observe(config("PERSONAL","CNY",DATE,State.ACTIVE,Measure.POSITION_VALUE),facts(null,n("50"),null)).quality());
    }
    @Test void metadataLifecycleIsolationAndReadOnlyProgress() throws Exception {
        var store=mock(GoalTrackingMapper.class); var users=mock(UserService.class);
        var families=mock(FamilyService.class); var radar=mock(FinanceRadarService.class);
        var u=new AuthResponse.UserInfo(); u.setId(7L); u.setFamilyId(9L); when(users.getCurrentUser()).thenReturn(u);
        var service=new GoalTrackingService(store,users,families,radar);
        Map<String,String> saved=new HashMap<>();
        when(store.insert(anyString(),eq(7L),eq(9L),anyString())).thenAnswer(a->{saved.put(a.getArgument(0),a.getArgument(3)); return 1;});
        when(store.detail(anyString(),eq(7L),eq(9L))).thenAnswer(a->saved.get(a.getArgument(0)));
        when(store.update(anyString(),eq(7L),eq(9L),anyString())).thenAnswer(a->{saved.put(a.getArgument(0),a.getArgument(3)); return 1;});
        var g=service.create(config("PERSONAL","CNY",DATE,State.ACTIVE,Measure.TOTAL_ASSETS));
        for(var state:List.of(State.PAUSED,State.ARCHIVED,State.ACTIVE)) {
            var updated=service.edit(g.id(),config("PERSONAL","CNY",DATE,state,Measure.CASH));
            assertEquals(state,updated.config().state()); assertEquals(g.createdAt(),updated.createdAt());
        }
        clearInvocations(store);
        when(radar.getRadar(7L,9L,"PERSONAL")).thenReturn(facts(n("100"),null,null));
        assertEquals(Quality.OK,service.progress(g.id()).quality());
        verify(store).detail(g.id(),7L,9L); verifyNoMoreInteractions(store);
        verify(radar).getRadar(7L,9L,"PERSONAL"); verifyNoMoreInteractions(radar);
        assertTrue(GoalTrackingService.class.getMethod("progress",String.class).getAnnotation(Transactional.class).readOnly());
        u.setId(8L); assertThrows(IllegalArgumentException.class,()->service.progress(g.id()));
        u.setId(7L); u.setFamilyId(10L); assertThrows(IllegalArgumentException.class,()->service.edit(g.id(),g.config()));
        u.setFamilyId(9L);
        service.create(config("FAMILY","CNY",DATE,State.ACTIVE,Measure.CASH)); verify(families).assertAdmin(7L,9L);
        doThrow(new IllegalArgumentException("无权限")).when(families).assertAdmin(7L,9L);
        assertThrows(IllegalArgumentException.class,()->service.create(config("FAMILY","CNY",DATE,State.ACTIVE,Measure.CASH)));
    }
    @Test void validationRejectsInvalidDatesAmountsAndPagination() {
        var store=mock(GoalTrackingMapper.class); var users=mock(UserService.class);
        var u=new AuthResponse.UserInfo(); u.setId(7L); when(users.getCurrentUser()).thenReturn(u);
        var service=new GoalTrackingService(store,users,mock(FamilyService.class),mock(FinanceRadarService.class));
        for(var amount:Arrays.asList(null,n("0"),n("-1"),n("1.001"),n("1000000000000000000"))) {
            var c=new Config("目标",amount,DATE,"CNY","PERSONAL",Measure.CASH,State.ACTIVE,null);
            assertThrows(IllegalArgumentException.class,()->service.create(c));
        }
        for(var date:Arrays.asList(null,LocalDate.of(999,12,31),LocalDate.of(10000,1,1)))
            assertThrows(IllegalArgumentException.class,()->service.create(config("PERSONAL","CNY",date,State.ACTIVE,Measure.CASH)));
        assertThrows(IllegalArgumentException.class,()->service.create(config("PERSONAL","BAD",DATE,State.ACTIVE,Measure.CASH)));
        assertThrows(IllegalArgumentException.class,()->service.list(Integer.MAX_VALUE,50));
        assertThrows(IllegalArgumentException.class,()->service.list(0,51)); verifyNoInteractions(store);
    }
}
