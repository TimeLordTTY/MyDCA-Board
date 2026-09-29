package com.timelordtty.dca.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.dto.BacktestRunDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BacktestResearchServiceTest {
    @TempDir Path root;
    private final ObjectMapper mapper = new ObjectMapper();

    private UserService users(long id) {
        UserService users = mock(UserService.class);
        var info = new AuthResponse.UserInfo();
        info.setId(id);
        info.setFamilyId(9L);
        when(users.getCurrentUser()).thenReturn(info);
        return users;
    }

    private BacktestResearchService service(BacktestRunRepository repository, long id) {
        UserService users = users(id);
        return new BacktestResearchService(repository, new BacktestCompareService(repository, users), users);
    }

    private String save(BacktestRunRepository repository, long owner, String hash, String end,
            double drawdown, double delta, int trades, String status) throws Exception {
        String id = UUID.randomUUID().toString();
        ObjectNode result = mapper.createObjectNode();
        result.putObject("strategy").putObject("params").put("contribution", 100);
        result.putObject("data_range").put("start", "2024-01-01").put("end", end);
        ObjectNode metrics = result.putObject("metrics");
        metrics.put("total_return", 0.1);
        metrics.put("annualized_return", 0.1);
        metrics.put("max_drawdown", drawdown);
        metrics.put("invested", 1000);
        metrics.put("final_assets", 1100);
        metrics.put("cash", 0);
        metrics.put("trade_count", trades);
        result.putObject("baseline").put("final_assets_delta", 100)
                .put("annualized_return_delta", delta).put("max_drawdown_delta", 0);
        repository.save(new BacktestRunDTO(id, owner, 9L, "private.csv", hash, "pure_sip", "1",
                "{\"contribution\":100}", "unused", "1.0.0", Instant.EPOCH, Instant.EPOCH,
                status, false, null, metrics, "SUCCESS".equals(status) ? result : null));
        return id;
    }

    @Test void deterministicBoundariesAndScope() throws Exception {
        var repository = new BacktestRunRepository(root);
        String id = save(repository, 7, "a".repeat(64), "2024-06-29", -0.30, 0.02, 3, "SUCCESS");
        var service = service(repository, 7);
        var exact = new BacktestResearchService.Thresholds(180, 0.30, 0.02, 3);
        var first = service.research(List.of(id), exact);
        assertEquals(first.toString(), service.research(List.of(id), exact).toString());
        assertEquals("WORTH_FURTHER_RESEARCH", first.path("candidates").get(0).path("status").asText());
        assertEquals("CONSISTENT", first.path("candidates").get(0).path("evidence_status").asText());
        assertEquals(180, first.path("candidates").get(0).path("evidence").get(0).path("sample_days").asInt());
        assertFalse(first.toString().contains("private.csv"));
        assertThrows(IllegalArgumentException.class, () -> service.research(List.of(id, id), exact));
        assertThrows(IllegalArgumentException.class, () -> service.research(List.of(id),
                new BacktestResearchService.Thresholds(0, 0.30, 0.02, 3)));
        assertThrows(IllegalArgumentException.class, () -> service(repository, 8).research(List.of(id), exact));
    }

    @Test void distinguishesShortSamplesFailuresAndInconsistentDatasets() throws Exception {
        var repository = new BacktestRunRepository(root);
        String first = save(repository, 7, "a".repeat(64), "2024-02-01", -0.10, 0.02, 3, "SUCCESS");
        String second = save(repository, 7, "b".repeat(64), "2024-02-01", -0.10, 0.02, 3, "SUCCESS");
        String failed = save(repository, 7, "a".repeat(64), "2024-02-01", -0.10, 0.02, 3, "FAILED");
        var service = service(repository, 7);
        var report = service.research(List.of(second, failed, first), new BacktestResearchService.Thresholds(180, 0.3, 0.0, 3));
        assertEquals(1, report.path("excluded_runs").size());
        assertEquals("EVIDENCE_INSUFFICIENT", report.path("candidates").get(0).path("status").asText());
        assertEquals(2, report.path("candidates").get(0).path("dataset_hashes").size());
        assertTrue(report.toString().contains("数据集 hash 不同"));
        assertTrue(report.toString().contains("样本区间不足"));
        assertEquals(report.toString(), service.research(List.of(first, second, failed),
                new BacktestResearchService.Thresholds(180, 0.3, 0.0, 3)).toString());
    }

    @Test void rejectsDrawdownAndBaselineButMissingTradesMeansInsufficientEvidence() throws Exception {
        var repository = new BacktestRunRepository(root);
        String id = save(repository, 7, "a".repeat(64), "2024-12-31", -0.31, -0.01, 2, "SUCCESS");
        var service = service(repository, 7);
        var rejected = service.research(List.of(id), new BacktestResearchService.Thresholds(180, 0.30, 0.0, 2));
        assertEquals("DOES_NOT_MEET_CRITERIA", rejected.path("candidates").get(0).path("status").asText());
        assertEquals(2, rejected.path("candidates").get(0).path("reasons").size());
        var thin = service.research(List.of(id), new BacktestResearchService.Thresholds(180, 0.30, 0.0, 3));
        assertEquals("EVIDENCE_INSUFFICIENT", thin.path("candidates").get(0).path("status").asText());
    }

    @Test void hasNoTradingServiceDependency() {
        assertFalse(Arrays.stream(BacktestResearchService.class.getDeclaredFields())
                .map(field -> field.getType().getSimpleName())
                .anyMatch(name -> List.of("OrderService", "SettlementService", "LedgerService").contains(name)));
    }
}
