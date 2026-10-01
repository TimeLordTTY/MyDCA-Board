package com.timelordtty.dca.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResearchPlanBacktestServiceTest {
    @TempDir Path root;
    @Test void controlledRunsKeepAssociationAndEvidenceAcrossSuccessFailureAndRestart() throws Exception {
        var json = new ObjectMapper();
        var plans = mock(ResearchPlanService.class);
        var users = mock(UserService.class);
        var owner = new com.timelordtty.dca.dto.AuthResponse.UserInfo();
        owner.setId(7L); owner.setFamilyId(9L);
        when(users.getCurrentUser()).thenReturn(owner);
        String id = UUID.randomUUID().toString();
        var plan = json.createObjectNode().put("strategy", "pure_sip").put("strategyVersion", "1").put("status", "DRAFT");
        plan.putObject("paramsDraft");
        when(plans.detail(id)).thenReturn(plan);
        var repo = new BacktestRunRepository(root.resolve("history"));
        var lab = new BacktestLabService(root, Path.of("../scripts/backtest/run_backtest.py"), "python", repo, users);
        var service = new ResearchPlanBacktestService(plans, lab);
        Path data = root.resolve("nav.csv");
        Files.writeString(data, "date,nav\n2024-01-01,1\n2024-02-01,1.1\n");
        var request = new ResearchPlanBacktestService.Run("nav.csv");
        var a = service.run(id, request); var b = service.run(id, request);
        String aid = a.path("history_run_id").asText(), bid = b.path("history_run_id").asText();
        assertNotEquals(aid, bid); assertTrue(b.path("cache_hit").asBoolean());
        var saved = new BacktestRunRepository(root.resolve("history")).find(aid, 7L, 9L);
        assertEquals(id, saved.researchPlanId()); assertEquals("1.0.0", saved.engineVersion());
        assertEquals("{\"contribution\":100.0,\"interval_days\":30}", saved.canonicalParams());
        assertEquals(ResearchPlanService.digest(Files.readAllBytes(data)), saved.datasetHash());
        assertEquals(id, lab.history(0, 10).get(0).researchPlanId());
        var compare = new BacktestCompareService(repo, users);
        assertEquals(id, compare.compare(List.of(aid, bid)).path("runs").get(0).path("research_plan_id").asText());
        var research = new BacktestResearchService(repo, compare, users);
        var bundle = new BacktestEvidenceService(compare, research).export(List.of(aid, bid), null);
        var entries = new HashMap<String, byte[]>();
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bundle.bytes()))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                entries.put(entry.getName(), zip.readAllBytes());
        }
        for (String file : List.of("manifest.json", "compare.json"))
            for (var run : json.readTree(entries.get(file)).path("runs"))
                assertEquals(id, run.path("research_plan_id").asText());
        // v0.15 files predate researchPlanId; they must remain usable alongside new runs.
        Path legacyFile = root.resolve("history").resolve(bid + ".json");
        String original = Files.readString(legacyFile);
        var legacy = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(original);
        legacy.remove("researchPlanId");
        Files.writeString(legacyFile, legacy.toString());
        assertNull(new BacktestRunRepository(root.resolve("history")).find(bid, 7L, 9L).researchPlanId());
        assertEquals(2, compare.compare(List.of(aid, bid)).path("runs").size());
        assertTrue(new BacktestEvidenceService(compare, research).export(List.of(aid, bid), null).bytes().length > 0);
        Files.writeString(legacyFile, original);
        Files.writeString(data, "date,nav\n2024-01-01,1\n2024-02-01,1.2\n");
        var changed = service.run(id, request);
        assertNotEquals(saved.datasetHash(), lab.detail(changed.path("history_run_id").asText()).datasetHash());
        plan.withObject("/paramsDraft").put("code", "__import__('os').system('whoami')");
        assertThrows(IllegalArgumentException.class, () -> service.run(id, request));
        plan.putObject("paramsDraft").put("interval_days", 366);
        assertThrows(IllegalArgumentException.class, () -> service.run(id, request));
        plan.putObject("paramsDraft").put("interval_days", 1.0);
        assertDoesNotThrow(() -> service.run(id, request));
        plan.put("strategyVersion", "99");
        assertThrows(IllegalArgumentException.class, () -> service.run(id, request));
        plan.put("strategyVersion", "1"); plan.putObject("paramsDraft");
        assertThrows(IllegalArgumentException.class, () -> service.run(id, new ResearchPlanBacktestService.Run("missing.csv")));
        var broken = new ResearchPlanBacktestService(plans,
                new BacktestLabService(root, root.resolve("missing.py"), "python", repo, users));
        assertThrows(IllegalStateException.class, () -> broken.run(id, request));
        assertTrue(lab.history(0, 50).stream().anyMatch(r -> "FAILED".equals(r.status()) && id.equals(r.researchPlanId())));
        assertTrue(lab.history(0, 50).stream().anyMatch(r -> "INVALID".equals(r.status()) && id.equals(r.researchPlanId())));
        assertTrue(lab.history(0, 50).stream().allMatch(r -> id.equals(r.researchPlanId())));
        assertThrows(IllegalArgumentException.class, () -> service.run(UUID.randomUUID().toString(), request));
        plan.put("status", "ARCHIVED");
        assertThrows(IllegalArgumentException.class, () -> service.run(id, request));
        assertNull(repo.find(aid, 8L, 9L)); assertNull(repo.find(aid, 7L, 10L));
    }
}
