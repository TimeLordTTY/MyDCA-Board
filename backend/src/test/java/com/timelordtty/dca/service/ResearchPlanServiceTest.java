package com.timelordtty.dca.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.dto.BacktestRunDTO;
import com.timelordtty.dca.mapper.ResearchPlanMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResearchPlanServiceTest {
    @TempDir Path root;
    final ObjectMapper json = new ObjectMapper();

    @Test void snapshotsScopeWarningsLifecycleAndReadOnlyAccess() throws Exception {
        var users = mock(UserService.class);
        var owner = new AuthResponse.UserInfo(); owner.setId(7L); owner.setFamilyId(9L);
        when(users.getCurrentUser()).thenReturn(owner);
        var plans = mock(ResearchPlanMapper.class);
        Map<String,String> storage = new HashMap<>();
        when(plans.insert(anyString(), eq(7L), eq(9L), anyString())).thenAnswer(call -> {
            storage.put(call.getArgument(0), call.getArgument(3)); return 1;
        });
        when(plans.find(anyString(), eq(7L), eq(9L))).thenAnswer(call -> storage.get(call.getArgument(0)));
        when(plans.list(7L,9L,0,20)).thenAnswer(call -> new ArrayList<>(storage.values()));
        when(plans.update(anyString(),eq(7L),eq(9L),anyString(),anyString())).thenAnswer(call -> {
            assertEquals(storage.get(call.getArgument(0)), call.getArgument(4));
            storage.put(call.getArgument(0), call.getArgument(3)); return 1;
        });
        Path data = Files.createDirectory(root.resolve("datasets"));
        Files.writeString(data.resolve("sample.csv"), "date,nav\n2024-01-01,1\n");
        String hash = ResearchPlanService.digest(Files.readAllBytes(data.resolve("sample.csv")));
        var history = new BacktestRunRepository(root.resolve("history"));
        String runId = UUID.randomUUID().toString();
        var result = json.readTree("""
            {"strategy":{"params":{"contribution":100}},"data_range":{"start":"2024-01-01","end":"2024-12-31"},
             "metrics":{"total_return":0.1,"annualized_return":0.1,"max_drawdown":-0.1,"invested":1000,"final_assets":1100,"cash":0,"trade_count":5},
             "baseline":{"final_assets_delta":100,"annualized_return_delta":0.02,"max_drawdown_delta":0}}
            """);
        history.save(new BacktestRunDTO(runId,7L,9L,"sample.csv",hash,"pure_sip","1","{}","unused","1.0.0",
                Instant.EPOCH,Instant.EPOCH,"SUCCESS",false,null,result.path("metrics"),result));
        var compare = new BacktestCompareService(history,users);
        var research = new BacktestResearchService(history,compare,users);
        var service = new ResearchPlanService(plans,research,history,compare,users,data);
        var candidate = research.research(List.of(runId),null).path("candidates").get(0);
        var request = new ResearchPlanService.Create("研究方案","备注",List.of(runId),null,candidate.path("candidate_id").asText());
        var created = service.create(request); String id = created.path("id").asText();
        assertEquals("DRAFT",created.path("status").asText()); assertTrue(created.path("warnings").isEmpty());
        var snapshot = created.path("evidenceSnapshot").deepCopy();
        candidate.withObject("/canonical_params").put("contribution",999);
        assertEquals(100,service.detail(id).path("canonicalParamsSnapshot").path("contribution").asInt());
        String saved = storage.get(id); clearInvocations(plans);
        service.detail(id); service.list(0,20);
        verify(plans,never()).insert(anyString(),any(),any(),anyString());
        verify(plans,never()).update(anyString(),any(),any(),anyString(),anyString());
        assertEquals(saved,storage.get(id));
        owner.setId(8L); assertNull(service.detail(id));
        assertThrows(IllegalArgumentException.class,()->service.create(request));
        assertThrows(IllegalArgumentException.class,()->service.edit(id,new ResearchPlanService.Edit("越权",null,null,null)));
        owner.setId(7L); owner.setFamilyId(10L); assertNull(service.detail(id));
        assertThrows(IllegalArgumentException.class,()->service.create(request)); owner.setFamilyId(9L);
        var edited = service.edit(id,new ResearchPlanService.Edit("新名称","新备注",json.readTree("{\"contribution\":200}"),"ACTIVE"));
        assertEquals(snapshot.toString(),edited.path("evidenceSnapshot").toString());
        assertEquals(100,history.find(runId,7L,9L).result().path("strategy").path("params").path("contribution").asInt());
        assertEquals(200,edited.path("paramsDraft").path("contribution").asInt());
        assertFalse(json.readTree(storage.get(id)).has("warnings"));
        assertThrows(IllegalArgumentException.class,()->service.edit(id,new ResearchPlanService.Edit(null,null,null,"BUY")));
        assertThrows(IllegalArgumentException.class,()->service.edit(id,new ResearchPlanService.Edit(null,null,json.readTree("[]"),null)));
        Path source = root.resolve("history").resolve(runId+".json");
        String originalRun = Files.readString(source);
        var changedRun = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(originalRun);
        changedRun.withObject("/result/strategy/params").put("contribution",300);
        Files.writeString(source,changedRun.toString());
        assertTrue(service.detail(id).path("warnings").toString().contains("证据已变更"));
        assertEquals(snapshot.toString(),service.detail(id).path("evidenceSnapshot").toString());
        changedRun.put("status","FAILED"); Files.writeString(source,changedRun.toString());
        assertTrue(service.detail(id).path("warnings").toString().contains("回测已失效"));
        Files.writeString(source,originalRun);
        Files.writeString(data.resolve("sample.csv"),"changed"); assertTrue(service.detail(id).path("warnings").toString().contains("hash"));
        Files.delete(data.resolve("sample.csv")); assertTrue(service.detail(id).path("warnings").toString().contains("数据集已失效"));
        Files.delete(root.resolve("history").resolve(runId+".json"));
        assertTrue(service.detail(id).path("warnings").toString().contains("回测已失效"));
        assertEquals(snapshot.toString(),service.detail(id).path("evidenceSnapshot").toString());
        service.edit(id,new ResearchPlanService.Edit(null,null,null,"ARCHIVED"));
        assertThrows(IllegalArgumentException.class,()->service.edit(id,new ResearchPlanService.Edit(null,null,null,"ACTIVE")));
        assertThrows(IllegalArgumentException.class,()->service.list(-1,20));
    }
    @Test void concurrentEditDoesNotPretendSuccess() throws Exception {
        var users = mock(UserService.class); var owner = new AuthResponse.UserInfo(); owner.setId(7L);
        when(users.getCurrentUser()).thenReturn(owner);
        var plans = mock(ResearchPlanMapper.class); String id = UUID.randomUUID().toString();
        when(plans.find(id,7L,null)).thenReturn("{\"status\":\"DRAFT\",\"name\":\"旧名称\"}");
        var service = new ResearchPlanService(plans,null,null,null,users,root);
        assertThrows(IllegalStateException.class,()->service.edit(id,new ResearchPlanService.Edit("新名称",null,null,null)));
    }
    @Test void noExecutionDependenciesOrAutomaticMigration() throws Exception {
        for (Class<?> type : List.of(ResearchPlanService.class,ResearchPlanMapper.class))
            assertFalse(Arrays.stream(type.getDeclaredFields()).anyMatch(f ->
                List.of("OrderService","SettlementService","LedgerService","BacktestLabService").contains(f.getType().getSimpleName())));
        assertTrue(Files.readString(Path.of("migrations/20261001_research_plan.sql")).contains("Manual deployment only"));
        String migration = Files.readString(Path.of("migrations/20261001_research_plan.sql"));
        String init = Files.readString(Path.of("sql/initsql/research_plan.sql"));
        assertEquals(migration.substring(migration.indexOf("CREATE TABLE")), init.substring(init.indexOf("CREATE TABLE")));
        assertFalse(Files.readString(Path.of("pom.xml")).contains("flyway"));
        assertFalse(Files.readString(Path.of("pom.xml")).contains("liquibase"));
        String mapper = Files.readString(Path.of("src/main/java/com/timelordtty/dca/mapper/ResearchPlanMapper.java"));
        assertEquals(4,mapper.split("owner_user_id",-1).length-1);
        assertEquals(4,mapper.split("owner_family_id",-1).length-1);
        var configuration = new org.apache.ibatis.session.Configuration();
        configuration.addMapper(ResearchPlanMapper.class);
        for (String operation : List.of("find","list","update","insert")) {
            var bound = configuration.getMappedStatement(ResearchPlanMapper.class.getName()+"."+operation).getBoundSql(new HashMap<>());
            assertTrue(bound.getParameterMappings().stream().anyMatch(p -> p.getProperty().equals("user")));
            assertTrue(bound.getParameterMappings().stream().anyMatch(p -> p.getProperty().equals("family")));
            assertFalse(bound.getSql().contains("#{"));
            if (!operation.equals("insert")) assertTrue(bound.getSql().contains("owner_family_id <=> ?"));
        }
    }
}
