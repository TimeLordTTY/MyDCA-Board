package com.timelordtty.dca.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** A bounded, reproducible archive of allowlisted, owner-scoped research results. */
@Service
public class BacktestEvidenceService {
    static final int MAX_BUNDLE_BYTES = 256_000;
    private static final long TIMEOUT_NANOS = 10_000_000_000L;
    private final BacktestCompareService compare;
    private final BacktestResearchService research;
    private final ObjectMapper mapper = new ObjectMapper();

    public BacktestEvidenceService(BacktestCompareService compare, BacktestResearchService research) {
        this.compare = compare;
        this.research = research;
    }

    public record Bundle(byte[] bytes, String sha256) {}

    public Bundle export(List<String> ids, BacktestResearchService.Thresholds thresholds) throws IOException {
        long start = System.nanoTime();
        List<String> orderedIds = ids == null ? null : ids.stream().sorted().toList();
        // Both services resolve every run against the current user and family before returning safe fields.
        ObjectNode comparison = compare.compare(orderedIds);
        checkTimeout(start);
        ObjectNode candidates = research.research(orderedIds, thresholds);
        checkTimeout(start);
        comparison.remove(List.of("generated_at", "markdown"));
        byte[] compareBytes = json(comparison);
        byte[] researchBytes = json(candidates);

        ObjectNode manifest = mapper.createObjectNode();
        manifest.put("schema_version", "1");
        manifest.set("run_ids", mapper.valueToTree(orderedIds));
        manifest.set("runs", comparison.path("runs").deepCopy());
        manifest.set("comparison_warnings", comparison.path("warnings").deepCopy());
        manifest.put("candidate_rule_version", BacktestResearchService.RULE_VERSION);
        manifest.set("candidate_thresholds", candidates.path("thresholds").deepCopy());
        manifest.set("candidates", candidates.path("candidates").deepCopy());
        ObjectNode hashes = manifest.putObject("content_sha256");
        hashes.put("compare.json", sha256(compareBytes));
        hashes.put("research.json", sha256(researchBytes));
        String summary = summary(comparison, candidates);
        byte[] summaryBytes = summary.getBytes(StandardCharsets.UTF_8);
        hashes.put("summary.md", sha256(summaryBytes));
        byte[] manifestBytes = json(manifest);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            add(zip, "manifest.json", manifestBytes);
            add(zip, "summary.md", summaryBytes);
            add(zip, "compare.json", compareBytes);
            add(zip, "research.json", researchBytes);
        }
        byte[] bytes = output.toByteArray();
        if (bytes.length > MAX_BUNDLE_BYTES) throw new IllegalArgumentException("研究证据包超过 256 KB 上限");
        checkTimeout(start);
        return new Bundle(bytes, sha256(bytes));
    }

    private byte[] json(JsonNode value) throws IOException {
        return (mapper.writeValueAsString(value) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private String summary(JsonNode comparison, JsonNode research) {
        StringBuilder text = new StringBuilder("# 策略研究证据摘要\n\n")
                .append("这些 run 因主人在策略实验室中共同选择而纳入同一次比较；使用相同的研究门禁筛查候选。\n\n")
                .append("## 比较范围\n\n");
        for (JsonNode run : comparison.path("runs"))
            text.append("- ").append(run.path("run_id").asText()).append("：")
                    .append(run.path("strategy").asText()).append(" v")
                    .append(run.path("strategy_version").asText()).append("，数据集 SHA-256：")
                    .append(run.path("dataset_hash").asText()).append("\n");
        text.append("\n## 证据不足点与警告\n\n");
        boolean warning = false;
        for (JsonNode item : comparison.path("warnings")) {
            text.append("- ").append(item.asText()).append("\n");
            warning = true;
        }
        for (JsonNode candidate : research.path("candidates")) {
            for (JsonNode item : candidate.path("warnings")) {
                text.append("- ").append(item.asText()).append("\n");
                warning = true;
            }
            if (!"CONSISTENT".equals(candidate.path("evidence_status").asText())) {
                for (JsonNode item : candidate.path("reasons")) text.append("- ").append(item.asText()).append("\n");
                warning = true;
            }
        }
        if (!warning) text.append("- 当前门禁未发现不足；仍需独立数据和样本外验证。\n");
        return text.append("\n## 不能推出的结论\n\n")
                .append("本证据包不能证明未来收益、策略优劣或适合真实账户。历史回测不代表未来表现；研究候选不是买卖或执行建议。\n")
                .toString();
    }

    private static void add(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private static void checkTimeout(long start) {
        if (System.nanoTime() - start > TIMEOUT_NANOS)
            throw new IllegalStateException("研究证据包生成超时");
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 不可用", error);
        }
    }
}
