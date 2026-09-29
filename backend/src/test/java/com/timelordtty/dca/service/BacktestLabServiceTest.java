package com.timelordtty.dca.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BacktestLabServiceTest {
    @TempDir Path root;

    private UserService users(long id, Long familyId) {
        UserService users = mock(UserService.class);
        var info = new com.timelordtty.dca.dto.AuthResponse.UserInfo();
        info.setId(id);
        info.setFamilyId(familyId);
        when(users.getCurrentUser()).thenReturn(info);
        return users;
    }

    private BacktestLabService service(UserService users) {
        return new BacktestLabService(root, Path.of("../scripts/backtest/run_backtest.py"), "python",
                new BacktestRunRepository(root.resolve("history")), users);
    }

    @Test void rejectsPathsStrategiesAndExecutableParameters() throws Exception {
        Files.writeString(root.resolve("nav.csv"), "date,nav\n2024-01-01,1\n2024-02-01,1.1\n");
        BacktestLabService service = new BacktestLabService(root, root.resolve("missing.py"), "python",
                new BacktestRunRepository(root.resolve("history")), users(7, null));
        assertEquals(java.util.List.of("nav.csv"), service.datasets());
        assertThrows(IllegalArgumentException.class, () -> service.run("../nav.csv", "pure_sip", "1", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> service.run("missing.csv", "pure_sip", "1", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> service.run("nav.csv", "evil", "1", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> service.run("nav.csv", "pure_sip", "1", Map.of("command", "whoami")));
        assertThrows(IllegalArgumentException.class, () -> service.run("nav.csv", "pure_sip", "1", Map.of("interval_days", 1.5)));
        assertThrows(IllegalArgumentException.class, () -> service.run("nav.csv", "pure_sip", "1", Map.of("contribution", Double.POSITIVE_INFINITY)));
    }

    @Test void cachesSameRunAndInvalidatesChangedData() throws Exception {
        Path data = root.resolve("nav.csv");
        Files.writeString(data, "date,nav\n2024-01-01,1\n2024-02-01,1.1\n");
        BacktestLabService service = service(users(7, null));
        var first = service.run("nav.csv", "pure_sip", "1", Map.of("contribution", 100));
        var cached = service.run("nav.csv", "pure_sip", "1", Map.of("contribution", 100));
        assertEquals(first.path("run_id"), cached.path("run_id"));
        assertNotEquals(first.path("history_run_id"), cached.path("history_run_id"));
        assertFalse(first.path("cache_hit").asBoolean());
        assertTrue(cached.path("cache_hit").asBoolean());
        assertTrue(service.detail(cached.path("history_run_id").asText()).cacheHit());
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> service.run("nav.csv", "pure_sip", "1", Map.of("contribution", 100)));
            var b = executor.submit(() -> service.run("nav.csv", "pure_sip", "1", Map.of("contribution", 100)));
            assertEquals(a.get().path("run_id"), b.get().path("run_id"));
        } finally {
            executor.shutdownNow();
        }
        assertEquals(4, service.recent().size());
        assertTrue(first.has("baseline"));
        Files.writeString(data, "date,nav\n2024-01-01,1\n2024-02-01,1.2\n");
        var changed = service.run("nav.csv", "pure_sip", "1", Map.of("contribution", 100));
        assertNotEquals(first.path("run_id").asText(), changed.path("run_id").asText());
        assertFalse(changed.path("cache_hit").asBoolean());
        assertNotEquals(service.detail(first.path("history_run_id").asText()).datasetHash(),
                service.detail(changed.path("history_run_id").asText()).datasetHash());
    }

    @Test void restartScopePaginationAndRedactedFailures() throws Exception {
        Files.writeString(root.resolve("nav.csv"), "date,nav\n2024-01-01,1\n2024-02-01,1.1\n");
        var owner = users(7, 9L);
        var service = service(owner);
        var result = service.run("nav.csv", "pure_sip", "1", Map.of());
        String id = result.path("history_run_id").asText();
        assertEquals("SUCCESS", service(owner).detail(id).status());
        assertEquals("{\"contribution\":100.0,\"interval_days\":30}", service.detail(id).canonicalParams());
        var paramsDigest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(service.detail(id).canonicalParams().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(java.util.HexFormat.of().formatHex(paramsDigest), service.detail(id).paramsHash());
        assertNull(service(users(8, 9L)).detail(id));
        assertNull(service(users(7, 10L)).detail(id));
        assertNull(service.history(0, 1).get(0).result());
        assertTrue(service.history(1, 1).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.history(0, 51));
        assertThrows(IllegalArgumentException.class, () -> service.history(-1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> service.run("../secret.csv", "pure_sip", "1", Map.of("token", "secret")));
        assertThrows(IllegalArgumentException.class,
                () -> service.run("nav.csv", "pure_sip", "1", Map.of("interval_days", 1.5)));
        var missing = new BacktestLabService(root, root.resolve("missing.py"), "python",
                new BacktestRunRepository(root.resolve("history")), owner);
        assertThrows(IllegalStateException.class, () -> missing.run("nav.csv", "pure_sip", "1", Map.of()));
        assertEquals(4, service.history(0, 10).size());
        assertTrue(service.history(0, 10).stream().anyMatch(r -> "FAILED".equals(r.status())));
        assertTrue(service.history(0, 10).stream().anyMatch(r -> "INVALID".equals(r.status())));
        for (int i = 0; i < 11; i++) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.run("missing-secret.csv", "pure_sip", "1", Map.of()));
        }
        assertEquals(1, service.recent().size());
        try (var files = Files.list(root.resolve("history"))) {
            for (var file : files.toList()) {
                assertTrue(Files.size(file) <= BacktestRunRepository.MAX_RECORD_BYTES);
                String stored = Files.readString(file);
                assertFalse(stored.contains("secret.csv"));
                assertFalse(stored.contains("missing-secret.csv"));
                assertFalse(stored.contains("\"secret\""));
                assertFalse(stored.contains(root.toAbsolutePath().toString()));
            }
        }
        assertTrue(java.util.Arrays.stream(BacktestLabService.class.getDeclaredFields())
                .noneMatch(f -> f.getType() == OrderService.class || f.getType() == SettlementService.class));
    }

    @Test void repositoryRejectsOversizedPayload() throws Exception {
        var repo = new BacktestRunRepository(root.resolve("history"));
        var large = com.fasterxml.jackson.databind.node.TextNode.valueOf("x".repeat(BacktestRunRepository.MAX_RECORD_BYTES));
        var record = new com.timelordtty.dca.dto.BacktestRunDTO(java.util.UUID.randomUUID().toString(),
                7L, null, "nav.csv", null, "pure_sip", "1", "{}", null, "1.0.0",
                java.time.Instant.now(), java.time.Instant.now(), "SUCCESS", false, null, null, large);
        assertThrows(IllegalArgumentException.class, () -> repo.save(record));
        assertFalse(Files.exists(root.resolve("history")));
    }

    @Test void timeoutIsRecordedWithoutProcessOutput() throws Exception {
        Files.writeString(root.resolve("nav.csv"), "date,nav\n2024-01-01,1\n");
        Path slow = root.resolve("slow.py");
        Files.writeString(slow, "import time\ntime.sleep(2)\n");
        var service = new BacktestLabService(root, slow, "python", new BacktestRunRepository(root.resolve("history")),
                users(7, null), java.time.Duration.ofMillis(100));
        assertThrows(java.util.concurrent.TimeoutException.class,
                () -> service.run("nav.csv", "pure_sip", "1", Map.of()));
        assertEquals("TIMEOUT", service.history(0, 10).get(0).status());
        assertNull(service.history(0, 10).get(0).result());
    }
}
