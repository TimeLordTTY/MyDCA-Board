package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.AllocationPolicyDTO.*;
import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.mapper.AllocationPolicyMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.timelordtty.dca.service.AllocationPolicyServiceTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RebalancePreviewEngineTest {
    @Test void multiAssetBoundariesConservationAndRounding() {
        var p=policy(config("PERSONAL",true,null,null));
        for(var row:List.of(new String[]{"400","400","BELOW_BAND","200.00","100.00"},
                new String[]{"500","300","IN_RANGE","100.00","0.00"},
                new String[]{"700","100","IN_RANGE","-100.00","0.00"},
                new String[]{"750","50","ABOVE_BAND","-150.00","-50.00"},
                new String[]{"499.999","300.001","BELOW_BAND","100.00","0.00"})) {
            var r=RebalancePreviewEngine.calculate(p,facts("OK"),List.of(holding(1,row[0],null),holding(2,row[1],null)));
            assertEquals(row[2],r.status()); assertEquals(n(row[3]),r.targetScenario().selectedAdjustment());
            assertEquals(n(row[4]),r.bandScenario().selectedAdjustment());
            assertEquals(0,r.targetScenario().selectedAdjustment().add(r.targetScenario().remainderAdjustment()).signum());
            assertEquals(0,r.bandScenario().selectedAdjustment().add(r.bandScenario().remainderAdjustment()).signum());
            assertEquals(0,n(row[0]).divide(n("1000"),12,java.math.RoundingMode.HALF_UP).compareTo(r.currentWeight()));
            assertEquals(0,n(row[0]).subtract(n("600")).divide(n("10")).compareTo(r.deviationPercentagePoints()));
            assertTrue(r.warnings().stream().anyMatch(w->"FEES".equals(w.code()) && "NOT_MODELED".equals(w.status())));
            assertEquals("UNKNOWN",r.prices().get(0).priceSource()); assertEquals(facts("OK").date(),r.dataDate());
        }
    }
    @Test void cashAndCategory() {
        var positions=List.of(holding(1,"600",null),holding(2,"200",null));
        for(String type:List.of("CASH","EQUITY")) {
            var p=policy(new Config("PERSONAL",null,type,n("0.5"),n("0.4"),n("0.6"),null,null,true,null));
            var r=RebalancePreviewEngine.calculate(p,facts("OK"),positions);
            assertEquals(type.equals("CASH")?n("300.00"):n("-300.00"),r.targetScenario().selectedAdjustment());
        }
    }
    @Test void missingPricesStaleAndInconsistentSnapshotsAreUnknown() {
        var p=policy(config("PERSONAL",true,null,null));
        var positions=List.of(holding(1,"600",null),holding(2,"200",null));
        for(String market:List.of("UNKNOWN","STALE")) {
            var r=RebalancePreviewEngine.calculate(p,facts(market),positions);
            assertEquals("UNKNOWN",r.status()); assertNull(r.currentWeight()); assertNull(r.targetScenario());
        }
        positions.get(1).setMarketValue(null);
        assertEquals("UNKNOWN",RebalancePreviewEngine.calculate(p,facts("OK"),positions).status());
        assertEquals("UNKNOWN",RebalancePreviewEngine.calculate(p,facts("OK"),List.of()).status());
        assertEquals("UNKNOWN",RebalancePreviewEngine.calculate(p,null,positions).status());
        var f=facts("OK");
        var missingPrices=new com.timelordtty.dca.dto.FinanceRadarDTO(f.date(),f.scope(),f.assets(),null,List.of(),List.of());
        assertEquals("UNKNOWN",RebalancePreviewEngine.calculate(p,missingPrices,List.of(holding(1,"800",null))).status());
        var a=f.assets();
        var inconsistent=new com.timelordtty.dca.dto.FinanceRadarDTO(f.date(),f.scope(),
                new com.timelordtty.dca.dto.FinanceRadarDTO.Assets("OK",n("201"),a.investmentCost(),a.positionValue(),a.liabilities(),a.totalAssets(),a.netWorth()),null,f.markets(),List.of());
        assertEquals("UNKNOWN",RebalancePreviewEngine.calculate(p,inconsistent,List.of(holding(1,"800",null))).status());
    }
    @Test void repeatedPreviewHasOnlyScopedReadsAndDisabledDoesNotReadFinance() throws Exception {
        var store=mock(AllocationPolicyMapper.class); var users=mock(UserService.class);
        var families=mock(FamilyService.class); var radar=mock(FinanceRadarService.class); var holdings=mock(HoldingService.class);
        var u=new AuthResponse.UserInfo(); u.setId(7L); u.setFamilyId(9L); when(users.getCurrentUser()).thenReturn(u);
        var json=new ObjectMapper().findAndRegisterModules();
        when(store.detail("p",7L,9L)).thenReturn(json.writeValueAsString(policy(config("PERSONAL",true,null,null))));
        when(radar.getRadar(7L,9L,"PERSONAL")).thenReturn(facts("OK"));
        when(holdings.calculateHoldings(7L,null)).thenReturn(List.of(holding(1,"600",null),holding(2,"200",null)));
        var service=new AllocationPolicyService(store,users,families,radar,holdings);
        assertEquals(service.preview("p"),service.preview("p"));
        verify(store,times(2)).detail("p",7L,9L); verifyNoMoreInteractions(store);
        verify(radar,times(2)).getRadar(7L,9L,"PERSONAL"); verifyNoMoreInteractions(radar);
        verify(holdings,times(2)).calculateHoldings(7L,null); verifyNoMoreInteractions(holdings);
        u.setId(8L); assertThrows(IllegalArgumentException.class,()->service.preview("p")); u.setId(7L);
        when(store.detail("p",7L,9L)).thenReturn(json.writeValueAsString(policy(config("PERSONAL",false,null,null))));
        clearInvocations(radar,holdings); assertEquals("UNKNOWN",service.preview("p").status()); verifyNoInteractions(radar,holdings);
        when(store.detail("p",7L,9L)).thenReturn(json.writeValueAsString(policy(config("FAMILY",true,null,null))));
        service.preview("p"); verify(families).assertAdmin(7L,9L); verify(holdings).calculateHoldings(null,9L);
        doThrow(new IllegalArgumentException("无权访问")).when(families).assertAdmin(7L,9L);
        clearInvocations(radar,holdings); assertThrows(IllegalArgumentException.class,()->service.preview("p")); verifyNoInteractions(radar,holdings);
        assertTrue(AllocationPolicyService.class.getMethod("preview",String.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class).readOnly());
    }
}
