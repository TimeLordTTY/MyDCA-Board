package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.MultiGoalScenarioDTO.*;
import com.timelordtty.dca.dto.GoalForecastDTO;
import com.timelordtty.dca.dto.GoalTrackingDTO.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MultiGoalScenarioServiceTest extends GoalForecastServiceTest {
    MultiGoalScenarioService multi() throws Exception {
        when(goals.detail("h")).thenReturn(new Goal("h",new Config("第二目标",n("1000"),LocalDate.of(2026,11,30),"CNY","PERSONAL",Measure.CASH,State.ACTIVE,null),Instant.EPOCH));
        when(goals.progress("h")).thenReturn(new Progress("h",Quality.OK,"事实",n("100"),null,null,false,LocalDate.of(2026,10,8),0,false));
        return new MultiGoalScenarioService(goals,service);
    }
    Input input(String a,String b) {
        return new Input("情景",planned("2026-11","2027-01"),List.of(new Allocation("g",a==null?null:n(a)),new Allocation("h",b==null?null:n(b))));
    }
    Request pair(Input a,Input b) { return new Request(List.of(a,b)); }
    @Test void overLimitBlocksBothProjectionsAndNoDoubleFunding() throws Exception {
        var result=multi().compare(pair(input("200","200"),input("150","150")));
        var bad=result.scenarios().get(0);
        assertTrue(bad.months().get(0).overLimit());
        assertEquals(n("400"),bad.months().get(0).specifiedTotal());
        for(var g:bad.goals()) { assertNull(g.forecast().baseline().achievedMonth()); assertNull(g.forecast().baseline().months().get(0).contribution()); }
        var good=result.scenarios().get(1);
        assertFalse(good.months().get(0).overLimit());
        assertEquals(n("550.00"),good.goals().get(0).forecast().baseline().months().get(2).cumulativeProgress());
        assertEquals(2,result.changes().size());
        assertTrue(MultiGoalScenarioService.class.getMethod("compare",Request.class).getAnnotation(org.springframework.transaction.annotation.Transactional.class).readOnly());
        verify(goals,never()).create(any());
        verify(goals,times(1)).detail("h");
        verify(goals,times(1)).progress("h");
        for(var m:List.of("2026-11","2026-12","2027-01")) verify(budgets,times(1)).detail("b"+m);
        verifyNoMoreInteractions(budgets);
    }
    @Test void unspecifiedDoesNotAllocateRemainderAndResultsAreDeterministic() throws Exception {
        var s=multi(); var request=pair(input("100",null),input("100",null));
        var first=s.compare(request); var again=s.compare(request);
        assertEquals(first.scenarios(),again.scenarios());
        var result=first.scenarios().get(0);
        assertEquals("UNSPECIFIED",result.months().get(0).allocationStatus());
        assertNull(result.goals().get(1).forecast().baseline().months().get(0).contribution());
        assertEquals(n("100.00"),result.goals().get(0).forecast().baseline().months().get(0).contribution());
    }
    @Test void deadlineConflictsAndUnknownEvidenceStayBoundToResults() throws Exception {
        var s=multi();
        var result=s.compare(pair(input("0","300"),input("150","150"))).scenarios().get(0);
        assertEquals("AFTER_TARGET_MONTH",result.goals().get(1).deadlineStatus());
        when(goals.progress("h")).thenReturn(new Progress("h",Quality.PARTIAL,"缺数据",null,n("20"),null,null,LocalDate.of(2026,10,8),0,false));
        result=s.compare(pair(input("100","100"),input("100","100"))).scenarios().get(0);
        assertEquals(Quality.PARTIAL,result.goals().get(1).forecast().baseline().quality());
        assertNull(result.goals().get(1).forecast().baseline().achievedMonth());
        assertEquals(n("20"),result.goals().get(1).forecast().actualProgress().knownValue());
    }
    @Test void invalidAndMixedScopeInputsRejected() throws Exception {
        var s=multi();
        assertThrows(IllegalArgumentException.class,()->s.compare(pair(input("-1","0"),input("0","0"))));
        var duplicate=new Input("x",planned("2026-11","2026-11"),List.of(new Allocation("g",BigDecimal.ZERO),new Allocation("g",BigDecimal.ZERO)));
        assertThrows(IllegalArgumentException.class,()->s.compare(pair(duplicate,duplicate)));
        when(goals.detail("h")).thenReturn(new Goal("h",new Config("x",n("1000"),LocalDate.of(2027,1,1),"CNY","FAMILY",Measure.CASH,State.ACTIVE,null),Instant.EPOCH));
        assertThrows(IllegalArgumentException.class,()->s.compare(pair(input("0","0"),input("0","0"))));
        verifyNoInteractions(budgets);
    }
    @Test void changedHorizonAndRateAreReported() throws Exception {
        var s=multi(); var a=input("100","100");
        var r=new GoalForecastDTO.Request("2026-11","2026-12",GoalForecastDTO.Mode.PLANNED,
                a.cashflow().months().subList(0,2),null,n("0.12"));
        var b=new Input("b",r,a.allocations());
        var result=s.compare(pair(a,b));
        assertEquals(List.of("endMonth","annualRate","months"),result.changes().stream().map(Change::field).toList());
    }
    @Test void cardinalityAuthorizationAndMissingBudget() throws Exception {
        var s=multi();
        var one=new Input("x",planned("2026-11","2026-11"),List.of(new Allocation("g",n("1"))));
        assertThrows(IllegalArgumentException.class,()->s.compare(pair(one,one)));
        var nine=new ArrayList<Allocation>();
        for(int i=0;i<9;i++) nine.add(new Allocation("g"+i,n("1")));
        var many=new Input("x",one.cashflow(),nine);
        assertThrows(IllegalArgumentException.class,()->s.compare(pair(many,many)));
        when(goals.detail("h")).thenThrow(new IllegalArgumentException("目标不存在或无权访问"));
        assertThrows(IllegalArgumentException.class,()->s.compare(pair(input("1","1"),input("1","1"))));
        verifyNoInteractions(budgets);
        setup();
        var readable=multi();
        var missing=new GoalForecastDTO.Request("2026-11","2026-11",GoalForecastDTO.Mode.PLANNED,
                List.of(new GoalForecastDTO.MonthInput("2026-11",null,true)),null,null);
        var a=new Input("x",missing,input("1","1").allocations());
        var result=readable.compare(pair(a,a)).scenarios().get(0);
        assertNull(result.months().get(0).overLimit());
        assertNull(result.months().get(0).sharedUpperBound());
        assertEquals(Quality.UNKNOWN,result.goals().get(0).forecast().baseline().quality());
    }
}
