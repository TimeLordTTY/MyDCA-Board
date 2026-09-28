package com.timelordtty.dca.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BacktestLabServiceTest {
    @TempDir Path root;

    @Test void rejectsPathsStrategiesAndExecutableParameters() throws Exception {
        Files.writeString(root.resolve("nav.csv"), "date,nav\n2024-01-01,1\n2024-02-01,1.1\n");
        BacktestLabService service = new BacktestLabService(root, root.resolve("missing.py"), "python");
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
        BacktestLabService service = new BacktestLabService(root, Path.of("../scripts/backtest/run_backtest.py"), "python");
        var first = service.run("nav.csv", "pure_sip", "1", Map.of("contribution", 100));
        assertSame(first, service.run("nav.csv", "pure_sip", "1", Map.of("contribution", 100)));
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> service.run("nav.csv", "pure_sip", "1", Map.of("contribution", 100)));
            var b = executor.submit(() -> service.run("nav.csv", "pure_sip", "1", Map.of("contribution", 100)));
            assertSame(a.get(), b.get());
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, service.recent().size());
        assertTrue(first.has("baseline"));
        Files.writeString(data, "date,nav\n2024-01-01,1\n2024-02-01,1.2\n");
        var changed = service.run("nav.csv", "pure_sip", "1", Map.of("contribution", 100));
        assertNotEquals(first.path("run_id").asText(), changed.path("run_id").asText());
    }
}
