package com.timelordtty.dca.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.dto.BacktestRunDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BacktestCompareServiceTest {
    @TempDir Path root;

    private UserService users(long id) {
        UserService users = mock(UserService.class);
        var info = new AuthResponse.UserInfo();
        info.setId(id);
        info.setFamilyId(9L);
        when(users.getCurrentUser()).thenReturn(info);
        return users;
    }

    @Test void limitsScopeFailuresAndStableRedactedExports() throws Exception {
        BacktestRunRepository repository = new BacktestRunRepository(root.resolve("history"));
        UserService owner = users(7);
        BacktestLabService lab = new BacktestLabService(root, Path.of("../scripts/backtest/run_backtest.py"),
                "python", repository, owner);
        Files.writeString(root.resolve("a.csv"), "date,nav\n2024-01-01,1\n2024-02-01,1.1\n");
        Files.writeString(root.resolve("b.csv"), "date,nav\n2024-02-01,1\n2024-03-01,1.2\n");
        String first = lab.run("a.csv", "pure_sip", "1", Map.of()).path("history_run_id").asText();
        String second = lab.run("b.csv", "profit_recycle", "2", Map.of()).path("history_run_id").asText();
        BacktestCompareService compare = new BacktestCompareService(repository, owner);
        Instant time = Instant.parse("2026-09-29T01:00:00Z");

        assertThrows(IllegalArgumentException.class, () -> compare.compare(List.of(first), time));
        assertThrows(IllegalArgumentException.class, () -> compare.compare(List.of(first, first), time));
        assertThrows(IllegalArgumentException.class, () -> compare.compare(List.of(first, second, first, second, first, second), time));
        assertThrows(IllegalArgumentException.class,
                () -> new BacktestCompareService(repository, users(8)).compare(List.of(first, second), time));

        ObjectNode report = compare.compare(List.of(first, second), time);
        assertEquals(2, report.path("runs").size());
        assertEquals(3, report.path("warnings").size());
        String json = report.toString();
        String markdown = report.path("markdown").asText();
        assertEquals(json, compare.compare(List.of(first, second), time).toString());
        assertEquals(markdown, compare.compare(List.of(first, second), time).path("markdown").asText());
        assertTrue(markdown.contains(first));
        assertTrue(markdown.contains("canonical params"));
        assertTrue(markdown.contains("历史回测不代表未来表现"));
        assertTrue(json.contains("dataset_hash"));
        assertTrue(json.contains("baseline_delta"));
        assertFalse(json.contains(root.toAbsolutePath().toString()));
        assertFalse(json.contains("a.csv"));
        assertFalse(json.contains("b.csv"));
        assertFalse(json.toLowerCase().contains("token"));

        assertThrows(IllegalArgumentException.class,
                () -> lab.run("missing.csv", "pure_sip", "1", Map.of()));
        String failed = lab.history(0, 10).stream().filter(r -> !"SUCCESS".equals(r.status()))
                .findFirst().orElseThrow().historyRunId();
        assertThrows(IllegalArgumentException.class, () -> compare.compare(List.of(first, failed), time));

        List<String> five = new ArrayList<>(List.of(first, second));
        for (int i = 0; i < 3; i++) {
            BacktestRunDTO source = repository.find(first, 7L, 9L);
            String id = UUID.randomUUID().toString();
            repository.save(new BacktestRunDTO(id, 7L, 9L, source.dataset(), source.datasetHash(),
                    source.strategy(), source.strategyVersion(), source.canonicalParams(), source.paramsHash(),
                    source.engineVersion(), source.startedAt(), source.finishedAt(), source.status(), false,
                    null, source.metrics(), source.result()));
            five.add(id);
        }
        assertEquals(5, compare.compare(five, time).path("runs").size());

        BacktestRunDTO source = repository.find(first, 7L, 9L);
        ObjectNode poisoned = (ObjectNode) source.result().deepCopy();
        poisoned.put("token", "private-token");
        poisoned.put("absolute_path", root.toAbsolutePath().toString());
        poisoned.put("raw_csv", "date,secret\n2024-01-01,private-value");
        String poisonedId = UUID.randomUUID().toString();
        repository.save(new BacktestRunDTO(poisonedId, 7L, 9L, source.dataset(), source.datasetHash(),
                source.strategy(), source.strategyVersion(), "{\"token\":\"private-token\"}", source.paramsHash(),
                source.engineVersion(), source.startedAt(), source.finishedAt(), source.status(), false,
                null, source.metrics(), poisoned));
        String redacted = compare.compare(List.of(first, poisonedId), time).toString();
        assertFalse(redacted.contains("private-token"));
        assertFalse(redacted.contains("private-value"));
        assertFalse(redacted.contains(root.toAbsolutePath().toString()));
    }
}
