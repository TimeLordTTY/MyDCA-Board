package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.GoalForecastDTO.*;
import com.timelordtty.dca.dto.GoalTrackingDTO.*;
import com.timelordtty.dca.dto.MonthlyBudgetDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoalForecastServiceTest {
    GoalTrackingService goals;
    MonthlyBudgetService budgets;
    GoalForecastService service;
    static BigDecimal n(String s) { return new BigDecimal(s); }
    Goal goal(String currency, String scope, Measure measure) {
        return new Goal("g",new Config("目标",n("1000"),LocalDate.of(2027,12,31),currency,scope,measure,State.ACTIVE,null),Instant.EPOCH);
    }
    Progress progress(Quality q, String value) {
        return new Progress("g",q,"事实",value==null?null:n(value),null,null,value==null?null:n(value).compareTo(n("1000"))>=0,
                LocalDate.of(2026,10,8),0,false);
    }
    MonthlyBudgetDTO.Budget budget(String month, String currency, String scope, String income, String expense, String reserve) {
        return new MonthlyBudgetDTO.Budget("b"+month,new MonthlyBudgetDTO.Config("预算",month,currency,scope,List.of(
                new MonthlyBudgetDTO.Item("收入",MonthlyBudgetDTO.Kind.INCOME,1L,n(income)),
                new MonthlyBudgetDTO.Item("支出",MonthlyBudgetDTO.Kind.FIXED_EXPENSE,2L,n(expense)),
                new MonthlyBudgetDTO.Item("不可动用预留",MonthlyBudgetDTO.Kind.RESERVE,null,n(reserve)))),Instant.EPOCH);
    }
    void select(MonthlyBudgetDTO.Budget b) throws Exception { when(budgets.detail(b.id())).thenReturn(b); }
    Request request(String start, String end, boolean covered, Mode mode, BigDecimal extra, BigDecimal rate) {
        var months=new ArrayList<MonthInput>();
        for(var m=YearMonth.parse(start);!m.isAfter(YearMonth.parse(end));m=m.plusMonths(1))
            months.add(new MonthInput(m.toString(),"b"+m,covered));
        return new Request(start,end,mode,months,extra,rate);
    }
    Request planned(String start, String end) { return request(start,end,true,Mode.PLANNED,null,null); }
    @BeforeEach void setup() throws Exception {
        goals=mock(GoalTrackingService.class); budgets=mock(MonthlyBudgetService.class);
        service=new GoalForecastService(goals,budgets);
        when(goals.detail("g")).thenReturn(goal("CNY","PERSONAL",Measure.CASH));
        when(goals.progress("g")).thenReturn(progress(Quality.OK,"100"));
        select(budget("2026-11","CNY","PERSONAL","1000","600","100"));
        select(budget("2026-12","CNY","PERSONAL","1000","600","100"));
        select(budget("2027-01","CNY","PERSONAL","1000","600","100"));
    }
    @Test void baselineReserveCapsDeterminismAndNoWriters() throws Exception {
        var request=planned("2026-11","2027-01");
        var result=service.forecast("g",request);
        assertEquals(result,service.forecast("g",request));
        assertNull(result.mathematicalScenario());
        assertEquals("2027-01",result.baseline().achievedMonth());
        assertEquals("ACHIEVED",result.baseline().outcome());
        for(var row:result.baseline().months()) {
            assertEquals(n("300.00"),row.surplusUpperBound());
            assertEquals(n("300.00"),row.contribution());
            assertEquals(n("0.00"),row.mathematicalReturn());
            assertNull(row.actualReading());
        }
        verify(goals,times(2)).detail("g"); verify(goals,times(2)).progress("g");
        for(var m:List.of("2026-11","2026-12","2027-01")) verify(budgets,times(2)).detail("b"+m);
        verifyNoMoreInteractions(goals,budgets);
        assertTrue(GoalForecastService.class.getMethod("forecast",String.class,Request.class).getAnnotation(Transactional.class).readOnly());
    }
    @Test void deficitsAndExtraSavingsAreExplicitAndCompletionCaps() throws Exception {
        select(budget("2026-11","CNY","PERSONAL","100","200","50"));
        var r=service.forecast("g",planned("2026-11","2026-11"));
        assertEquals(n("0.00"),r.baseline().months().get(0).contribution());
        assertEquals("UNREACHABLE_WITHIN_RANGE",r.baseline().outcome());
        r=service.forecast("g",request("2026-11","2026-11",true,Mode.PLANNED,n("200"),null));
        assertEquals(n("50.00"),r.baseline().months().get(0).contribution());
        when(goals.progress("g")).thenReturn(progress(Quality.OK,"999"));
        select(budget("2026-11","CNY","PERSONAL","1000","600","100"));
        assertEquals(n("1.00"),service.forecast("g",planned("2026-11","2026-11")).baseline().months().get(0).contribution());
        when(goals.progress("g")).thenReturn(progress(Quality.OK,"1000"));
        r=service.forecast("g",planned("2026-11","2026-11"));
        assertEquals("2026-10",r.baseline().achievedMonth());
        assertEquals(n("0.00"),r.baseline().months().get(0).contribution());
    }
    @Test void partialMissingCurrenciesAndUnknownProgressNeverInventDates() throws Exception {
        var r=service.forecast("g",request("2026-11","2026-12",false,Mode.PLANNED,null,null));
        assertEquals(Quality.PARTIAL,r.baseline().quality());
        assertNull(r.baseline().achievedMonth()); assertNull(r.baseline().months().get(1).cumulativeProgress());
        select(budget("2026-11","USD","PERSONAL","1000","600","100"));
        r=service.forecast("g",planned("2026-11","2026-12"));
        assertEquals(Quality.UNKNOWN,r.baseline().quality()); assertNull(r.baseline().months().get(0).surplusUpperBound());
        var missing=new Request("2026-11","2026-11",Mode.PLANNED,List.of(new MonthInput("2026-11",null,true)),null,null);
        assertEquals(Quality.UNKNOWN,service.forecast("g",missing).baseline().quality());
        when(goals.progress("g")).thenReturn(progress(Quality.PARTIAL,null));
        assertNull(service.forecast("g",planned("2026-12","2026-12")).baseline().months().get(0).contribution());
        when(goals.detail("g")).thenReturn(goal("CNY","PERSONAL",Measure.POSITION_VALUE));
        when(goals.progress("g")).thenReturn(progress(Quality.OK,"100"));
        assertEquals(Quality.UNKNOWN,service.forecast("g",planned("2026-12","2026-12")).baseline().quality());
    }
    @Test void currentMonthDoesNotDoubleCountPostedIncomeAndRetainsPartialEvidence() throws Exception {
        var b=budget("2026-10","CNY","PERSONAL","1000","600","100"); select(b);
        var items=List.of(
                new MonthlyBudgetDTO.ItemComparison(b.config().items().get(0),MonthlyBudgetDTO.Quality.OK,n("1000"),n("1000"),n("0"),null),
                new MonthlyBudgetDTO.ItemComparison(b.config().items().get(1),MonthlyBudgetDTO.Quality.OK,n("200"),n("200"),n("400"),false));
        var actual=new MonthlyBudgetDTO.Comparison(b.id(),MonthlyBudgetDTO.Quality.OK,n("1000"),n("600"),n("100"),n("300"),n("1000"),n("200"),n("800"),n("400"),false,0,items,List.of());
        when(budgets.comparison(b.id())).thenReturn(actual);
        var request=request("2026-10","2026-10",true,Mode.ACTUAL_PLUS_REMAINING,null,null);
        var row=service.forecast("g",request).baseline().months().get(0);
        assertEquals(actual,row.actualReading()); assertEquals(n("0.00"),row.contribution());
        when(budgets.comparison(b.id())).thenReturn(new MonthlyBudgetDTO.Comparison(b.id(),MonthlyBudgetDTO.Quality.PARTIAL,
                n("1000"),n("600"),n("100"),n("300"),null,null,null,null,null,1,List.of(),List.of("不完整")));
        var r=service.forecast("g",request);
        assertEquals(Quality.PARTIAL,r.baseline().quality()); assertNull(r.baseline().achievedMonth());
    }
    @Test void explicitRateProducesSeparateScenario() throws Exception {
        var r=service.forecast("g",request("2026-11","2026-11",true,Mode.PLANNED,null,n("0.12")));
        assertEquals(n("400.00"),r.baseline().months().get(0).cumulativeProgress());
        assertEquals(n("401.00"),r.mathematicalScenario().months().get(0).cumulativeProgress());
        assertEquals(n("1.00"),r.mathematicalScenario().months().get(0).mathematicalReturn());
    }
    @Test void invalidDatesZeroAndOverSixtyMonthsAndScopeAreRejected() throws Exception {
        assertThrows(IllegalArgumentException.class,()->service.forecast("g",planned("2026-12","2026-11")));
        assertThrows(IllegalArgumentException.class,()->service.forecast("g",planned("2026-11","2031-11")));
        assertThrows(IllegalArgumentException.class,()->service.forecast("g",planned("2026-10","2026-10")));
        assertThrows(IllegalArgumentException.class,()->service.forecast("g",planned("2026-09","2026-09")));
        assertThrows(IllegalArgumentException.class,()->service.forecast("g",new Request("2026-1","2026-11",Mode.PLANNED,List.of(),null,null)));
        assertThrows(IllegalArgumentException.class,()->service.forecast("g",request("2026-11","2026-11",true,Mode.PLANNED,n("-1"),null)));
        assertThrows(IllegalArgumentException.class,()->service.forecast("g",request("2026-11","2026-11",true,Mode.PLANNED,null,n("1.01"))));
        select(budget("2026-11","CNY","FAMILY","1000","600","100"));
        assertThrows(IllegalArgumentException.class,()->service.forecast("g",planned("2026-11","2026-11")));
    }
    @Test void authorizationFailureStopsBeforeCashflowReads() throws Exception {
        when(goals.detail("g")).thenThrow(new IllegalArgumentException("目标不存在或无权访问"));
        assertThrows(IllegalArgumentException.class,()->service.forecast("g",planned("2026-11","2026-11")));
        verifyNoInteractions(budgets);
    }
    @Test void sixtyMonthsInclusiveYearBoundaryAndUncoveredGap() throws Exception {
        var request=planned("2026-11","2031-10");
        for(var input:request.months()) select(budget(input.month(),"CNY","PERSONAL","0","0","0"));
        var result=service.forecast("g",request);
        assertEquals(60,result.baseline().months().size());
        assertEquals("2031-10",result.baseline().months().get(59).month());
        assertEquals("UNREACHABLE_WITHIN_RANGE",result.baseline().outcome());
        var gap=service.forecast("g",planned("2026-12","2026-12"));
        assertEquals(Quality.UNKNOWN,gap.baseline().quality());
        assertNull(gap.baseline().achievedMonth());
        assertNull(gap.baseline().months().get(0).contribution());
    }
}
