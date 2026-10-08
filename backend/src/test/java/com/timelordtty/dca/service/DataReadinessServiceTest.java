package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.*;
import com.timelordtty.dca.dto.DataReadinessDTO.State;
import com.timelordtty.dca.dto.AuthResponse.UserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataReadinessServiceTest {
    UserService users=mock(UserService.class);
    FamilyService families=mock(FamilyService.class);
    GoalTrackingService goals=mock(GoalTrackingService.class);
    MonthlyBudgetService budgets=mock(MonthlyBudgetService.class);
    RiskWatchService risks=mock(RiskWatchService.class);
    AllocationPolicyService policies=mock(AllocationPolicyService.class);
    ResearchPlanService research=mock(ResearchPlanService.class);
    FinanceRadarService radar=mock(FinanceRadarService.class);
    DataReadinessService service=new DataReadinessService(users,families,goals,budgets,risks,policies,research,radar);
    UserInfo owner=new UserInfo();
    @BeforeEach void setup() {
        owner.setId(7L); owner.setFamilyId(9L); when(users.getCurrentUser()).thenReturn(owner);
    }
    @Test void emptyDataRemainsUnknownAndNeverWrites() throws Exception {
        var report=service.diagnose("PERSONAL","2026-10");
        assertEquals(10,report.evidence().size());
        for(var e:report.evidence()) assertNotEquals(State.READY,e.state());
        assertEquals(State.UNKNOWN,find(report,"SCHEMA").state());
        assertEquals(State.UNKNOWN,find(report,"FORECAST_INPUTS").state());
        verify(radar).getRadar(7L,9L,"PERSONAL");
        verify(goals).list(0,50); verify(budgets).list(0,50); verify(risks).list(0,50);
        verify(policies).list(0,50); verify(research).list(0,50);
        verifyNoMoreInteractions(goals,budgets,risks,policies,research,radar);
    }
    @Test void familyRequiresAdminBeforeAnyRead() {
        doThrow(new RuntimeException("secret internal account")).when(families).assertAdmin(7L,9L);
        assertThrows(AccessDeniedException.class,()->service.diagnose("FAMILY","2026-10"));
        verifyNoInteractions(goals,budgets,risks,policies,research,radar);
        owner.setFamilyId(null);
        assertThrows(AccessDeniedException.class,()->service.diagnose("FAMILY","2026-10"));
    }
    @Test void ownerAndFamilyIdentityComeOnlyFromCurrentUser() {
        service.diagnose("FAMILY","2026-10");
        verify(families).assertAdmin(7L,9L); verify(radar).getRadar(7L,9L,"FAMILY");
        owner.setId(18L); owner.setFamilyId(21L);
        service.diagnose("FAMILY","2026-10");
        verify(families).assertAdmin(18L,21L); verify(radar).getRadar(18L,21L,"FAMILY");
    }
    @Test void failedReadsAreSanitizedUnavailable() throws Exception {
        when(goals.list(0,50)).thenThrow(new IllegalStateException("jdbc password path table secret"));
        var report=service.diagnose("PERSONAL","2026-10");
        assertEquals(State.UNAVAILABLE,find(report,"GOALS").state());
        assertFalse(report.toString().contains("secret"));
        assertEquals(State.UNKNOWN,find(report,"SCHEMA").state());
    }
    @Test void missingCostsAndIncompleteFamilyRemainPartial() {
        var zero=BigDecimal.ZERO;
        var assets=new FinanceRadarDTO.Assets("OK",zero,null,zero,zero,zero,zero);
        var facts=new FinanceRadarDTO(LocalDate.now(),"PERSONAL",assets,null,List.of(),List.of());
        assertEquals(State.PARTIAL,DataReadinessService.assetEvidence(facts,"PERSONAL").state());
        assets=new FinanceRadarDTO.Assets("OK",BigDecimal.TEN,zero,zero,zero,BigDecimal.TEN,BigDecimal.TEN);
        facts=new FinanceRadarDTO(LocalDate.now(),"FAMILY",assets,null,List.of(),List.of());
        assertEquals(State.PARTIAL,DataReadinessService.assetEvidence(facts,"FAMILY").state());
    }
    @Test void staleMissingAndFutureMarketDatesNeverBecomeReady() {
        var today=LocalDate.now();
        for(var date:java.util.Arrays.asList(today.minusDays(4),today.plusDays(1),null)) {
            var facts=new FinanceRadarDTO(today,"PERSONAL",null,null,
                    List.of(new FinanceRadarDTO.MarketFact(1L,"OK",date,today,"OK",today)),List.of());
            assertEquals(State.PARTIAL,DataReadinessService.marketEvidence(facts).state());
        }
        var facts=new FinanceRadarDTO(today,"PERSONAL",null,null,
                List.of(new FinanceRadarDTO.MarketFact(1L,"OK",today,today,"OK",today)),List.of());
        assertEquals(State.READY,DataReadinessService.marketEvidence(facts).state());
    }
    @Test void monthMismatchAndUnknownProgressRemainUnknownOrPartial() throws Exception {
        var config=new GoalTrackingDTO.Config("goal",BigDecimal.TEN,LocalDate.now(),"USD","PERSONAL",
                GoalTrackingDTO.Measure.CASH,GoalTrackingDTO.State.ACTIVE,null);
        when(goals.list(0,50)).thenReturn(List.of(new GoalTrackingDTO.Goal("g",config,Instant.now())));
        var budget=new MonthlyBudgetDTO.Budget("b",new MonthlyBudgetDTO.Config("budget","2026-09","CNY","PERSONAL",List.of()),Instant.now());
        when(budgets.list(0,50)).thenReturn(List.of(budget));
        var report=service.diagnose("PERSONAL","2026-10");
        assertEquals(State.PARTIAL,find(report,"GOALS").state());
        assertEquals(State.UNKNOWN,find(report,"BUDGETS").state());
        verify(budgets,never()).comparison(anyString());
    }
    @Test void invalidParametersDoNotRead() {
        assertThrows(IllegalArgumentException.class,()->service.diagnose("ALL","2026-10"));
        assertThrows(IllegalArgumentException.class,()->service.diagnose("PERSONAL","2026-13"));
        verifyNoInteractions(users,radar);
    }
    @Test void partialEvidenceAcrossBudgetRiskAllocationAndResearchNeverWrites() throws Exception {
        var budget=new MonthlyBudgetDTO.Budget("b",new MonthlyBudgetDTO.Config("budget","2026-10","CNY","PERSONAL",List.of()),Instant.now());
        when(budgets.list(0,50)).thenReturn(List.of(budget));
        when(budgets.comparison("b")).thenReturn(new MonthlyBudgetDTO.Comparison("b",MonthlyBudgetDTO.Quality.PARTIAL,
                null,null,null,null,null,null,null,null,null,1,List.of(),List.of()));
        var rule=new RiskWatchDTO.Rule("r",new RiskWatchDTO.Config("PERSONAL",RiskWatchDTO.Type.RETURN,1L,null,
                BigDecimal.ONE,null,RiskWatchDTO.Direction.ABOVE,RiskWatchDTO.Severity.INFO,null,false),Instant.now(),"");
        when(risks.list(0,50)).thenReturn(List.of(rule));
        when(risks.history("r",0,1)).thenReturn(List.of());
        var policy=new AllocationPolicyDTO.Policy("p",new AllocationPolicyDTO.Config("PERSONAL",1L,null,
                null,null,null,null,List.of(),true,null),Instant.now(),"");
        when(policies.list(0,50)).thenReturn(List.of(policy));
        when(policies.evaluate("p")).thenReturn(new AllocationPolicyDTO.Evaluation("p",AllocationPolicyDTO.Status.UNKNOWN,
                "missing",null,null,List.of(),Instant.now(),""));
        var plan=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        plan.putArray("warnings").add("dataset changed");
        when(research.list(0,50)).thenReturn(List.of(plan));
        var report=service.diagnose("PERSONAL","2026-10");
        for(var area:List.of("BUDGETS","RISK_RULES","ALLOCATION","RESEARCH"))
            assertEquals(State.PARTIAL,find(report,area).state());
        verify(risks,never()).evaluate(anyString());
        verify(budgets).list(0,50); verify(budgets).comparison("b");
        verify(risks).list(0,50); verify(risks).history("r",0,1);
        verify(policies).list(0,50); verify(policies).evaluate("p"); verify(research).list(0,50);
        verifyNoMoreInteractions(budgets,risks,policies,research);
    }
    private static DataReadinessDTO.Evidence find(DataReadinessDTO report,String area) {
        return report.evidence().stream().filter(e->e.area().equals(area)).findFirst().orElseThrow();
    }
}
