package com.timelordtty.dca.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.dto.BacktestRunDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BacktestEvidenceServiceTest {
    @TempDir Path root;
    private final ObjectMapper mapper = new ObjectMapper();

    private UserService user(long id) {
        UserService users = mock(UserService.class);
        var info = new AuthResponse.UserInfo();
        info.setId(id);
        info.setFamilyId(9L);
        when(users.getCurrentUser()).thenReturn(info);
        return users;
    }

    private BacktestEvidenceService service(BacktestRunRepository repository, long owner) {
        UserService users = user(owner);
        BacktestCompareService compare = new BacktestCompareService(repository, users);
        return new BacktestEvidenceService(compare, new BacktestResearchService(repository, compare, users));
    }

    private String save(BacktestRunRepository repository, long owner, String status) throws Exception {
        String id = UUID.randomUUID().toString();
        ObjectNode result = mapper.createObjectNode();
        result.put("token", "private-token");
        result.put("absolute_path", "C:\\private\\account.csv");
        result.put("raw_csv", "date,secret\\nprivate-value");
        result.put("image_uri", "content://private-image");
        result.put("notification_text", "private-notification");
        result.putObject("strategy").putObject("params").put("contribution", 100);
        result.putObject("data_range").put("start", "2024-01-01").put("end", "2024-12-31");
        ObjectNode metrics = result.putObject("metrics");
        metrics.put("total_return", 0.1);
        metrics.put("annualized_return", 0.1);
        metrics.put("max_drawdown", -0.1);
        metrics.put("invested", 1000);
        metrics.put("final_assets", 1100);
        metrics.put("cash", 0);
        metrics.put("trade_count", 3);
        result.putObject("baseline").put("final_assets_delta", 100)
                .put("annualized_return_delta", 0.02).put("max_drawdown_delta", 0);
        repository.save(new BacktestRunDTO(id, owner, 9L, "private.csv", "a".repeat(64),
                "pure_sip", "1", "{\"token\":\"private-token\"}", "unused", "1.0.0",
                Instant.EPOCH, Instant.EPOCH, status, false, null, metrics,
                "SUCCESS".equals(status) ? result : null));
        return id;
    }

    private Map<String, byte[]> unzip(byte[] bytes) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                entries.put(entry.getName(), zip.readAllBytes());
        }
        return entries;
    }

    private String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test void ownerScopeRedactionDeterminismAndUtf8() throws Exception {
        var repository = new BacktestRunRepository(root);
        String first = save(repository, 7, "SUCCESS");
        String second = save(repository, 7, "SUCCESS");
        var evidence = service(repository, 7);
        var bundle = evidence.export(List.of(first, second), null);
        assertArrayEquals(bundle.bytes(), evidence.export(List.of(second, first), null).bytes());
        assertEquals(hash(bundle.bytes()), bundle.sha256());
        var files = unzip(bundle.bytes());
        assertEquals(4, files.size());
        var manifest = mapper.readTree(files.get("manifest.json"));
        assertEquals("1", manifest.path("schema_version").asText());
        assertEquals(2, manifest.path("runs").size());
        assertEquals("1", manifest.path("candidate_rule_version").asText());
        for (String name : List.of("compare.json", "research.json", "summary.md"))
            assertEquals(hash(files.get(name)), manifest.path("content_sha256").path(name).asText());
        String summary = new String(files.get("summary.md"), StandardCharsets.UTF_8);
        assertTrue(summary.contains("为何" ) || summary.contains("共同选择"));
        assertTrue(summary.contains("历史回测不代表未来表现"));
        assertTrue(summary.contains("不能推出的结论"));
        for (byte[] file : files.values()) {
            String content = new String(file, StandardCharsets.UTF_8);
            for (String secret : List.of("private-token", "private-value", "private.csv", "C:\\private",
                    "content://", "private-notification")) assertFalse(content.contains(secret));
        }
        assertThrows(IllegalArgumentException.class, () -> service(repository, 8).export(List.of(first, second), null));
        assertThrows(IllegalArgumentException.class, () -> evidence.export(List.of(first, UUID.randomUUID().toString()), null));
        String failed = save(repository, 7, "FAILED");
        assertThrows(IllegalArgumentException.class, () -> evidence.export(List.of(first, failed), null));
    }

    @Test void rejectsOversizedBundle() throws Exception {
        BacktestCompareService compare = mock(BacktestCompareService.class);
        BacktestResearchService research = mock(BacktestResearchService.class);
        ObjectNode report = mapper.createObjectNode();
        report.putArray("runs");
        report.putArray("warnings");
        when(compare.compare(any())).thenReturn(report);
        ObjectNode candidates = mapper.createObjectNode();
        candidates.put("schema_version", "1");
        candidates.putObject("thresholds");
        var items = candidates.putArray("candidates");
        byte[] noise = new byte[350_000];
        new Random(1).nextBytes(noise);
        items.addObject().put("noise", HexFormat.of().formatHex(noise));
        when(research.research(any(), any())).thenReturn(candidates);
        assertThrows(IllegalArgumentException.class,
                () -> new BacktestEvidenceService(compare, research).export(List.of("a", "b"), null));
    }
}
