package com.timelordtty.dca.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Isolated historical simulation. No database, order, or settlement dependency. */
@Service
public class BacktestLabService {
    private static final Set<String> STRATEGIES = Set.of("pure_sip:1", "ma_enhanced:1", "profit_recycle:1", "profit_recycle:2");
    private static final Set<String> COMMON = Set.of("contribution", "interval_days");
    private static final int MAX_CACHE = 100;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path root;
    private final Path script;
    private final String python;
    private final Map<String, JsonNode> cache = new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, JsonNode> eldest) { return size() > MAX_CACHE; }
    };

    public BacktestLabService() {
        this(Path.of(System.getProperty("backtest.data-root", "../data/backtest")),
                Path.of(System.getProperty("backtest.script", "../scripts/backtest/run_backtest.py")),
                System.getProperty("backtest.python", "python"));
    }

    BacktestLabService(Path root, Path script, String python) {
        this.root = root.toAbsolutePath().normalize();
        this.script = script.toAbsolutePath().normalize();
        this.python = python;
    }

    public List<String> datasets() throws IOException {
        if (!Files.isDirectory(root)) return List.of();
        try (var files = Files.list(root)) {
            return files.filter(p -> p.getFileName().toString().matches("[A-Za-z0-9_-]{1,80}\\.csv"))
                    .filter(Files::isRegularFile).filter(p -> !Files.isSymbolicLink(p))
                    .map(p -> p.getFileName().toString()).sorted().limit(100).toList();
        }
    }

    public synchronized JsonNode run(String data, String strategy, String version, Map<String, Object> params) throws Exception {
        if (data == null || !datasets().contains(data)) throw new IllegalArgumentException("历史数据不存在或不在允许目录内");
        if (!STRATEGIES.contains(strategy + ":" + version)) throw new IllegalArgumentException("策略或版本不存在");
        if (params == null) params = Map.of();
        Set<String> allowed = new java.util.HashSet<>(COMMON);
        if ("ma_enhanced".equals(strategy)) allowed.addAll(Set.of("ma_window", "dip_multiplier"));
        if ("profit_recycle".equals(strategy)) allowed.addAll(Set.of("profit_threshold", "sell_fraction"));
        if (!allowed.containsAll(params.keySet())) throw new IllegalArgumentException("包含不允许的策略参数");
        for (var entry : params.entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) throw new IllegalArgumentException("策略参数必须是有限数字");
            double n = number.doubleValue();
            if (n <= 0 || n > 1_000_000) throw new IllegalArgumentException("策略参数超出允许范围");
            if (Set.of("interval_days", "ma_window").contains(entry.getKey()) && (n != Math.rint(n) || n > 365)) throw new IllegalArgumentException("间隔和均线窗口必须是 1 至 365 的整数");
            if ("ma_window".equals(entry.getKey()) && n < 2) throw new IllegalArgumentException("均线窗口至少为 2");
            if ("dip_multiplier".equals(entry.getKey()) && n < 1) throw new IllegalArgumentException("低点倍数至少为 1");
            if ("sell_fraction".equals(entry.getKey()) && n > 1) throw new IllegalArgumentException("卖出比例不能超过 1");
        }
        Path file = root.resolve(data);
        if (Files.size(file) > 20_000_000) throw new IllegalArgumentException("历史数据超过 20 MB");
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length > 20_000_000) throw new IllegalArgumentException("历史数据超过 20 MB");
        String inputHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        String key = owner() + ":" + data + ":" + inputHash + ":" + strategy + ":" + version + ":" + mapper.writeValueAsString(new java.util.TreeMap<>(params));
        JsonNode previous = cache.get(key);
        if (previous != null) return previous;
        if (!Files.isRegularFile(script)) throw new IllegalStateException("回测程序不可用");
        Process process = new ProcessBuilder(python, script.toString(), "--data-root", root.toString(), "--data", data,
                "--strategy", strategy, "--version", version, "--params-stdin")
                .redirectErrorStream(true).start();
        try (var input = process.getOutputStream()) {
            input.write(mapper.writeValueAsBytes(params));
        }
        if (!process.waitFor(Duration.ofSeconds(20).toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("回测超时，请缩小数据范围");
        }
        byte[] output = process.getInputStream().readNBytes(2_000_001);
        if (output.length > 2_000_000) throw new IllegalStateException("回测结果过大");
        if (process.exitValue() != 0) throw new IllegalArgumentException("回测输入无效，请检查历史数据与策略参数");
        JsonNode result = mapper.readTree(output);
        String runId = result.path("run_id").asText();
        if (runId.isBlank() || !inputHash.equals(result.path("provenance").path("data_sha256").asText()))
            throw new IllegalStateException("回测结果校验失败");
        cache.put(key + ":" + runId, result);
        cache.put(key, result);
        return result;
    }

    public synchronized List<JsonNode> recent() {
        String prefix = owner() + ":";
        List<JsonNode> values = new ArrayList<>(cache.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix) && entry.getKey().endsWith(":" + entry.getValue().path("run_id").asText()))
                .map(Map.Entry::getValue).distinct().toList());
        return values.subList(Math.max(0, values.size() - 10), values.size());
    }

    private String owner() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "unit-test" : authentication.getName();
    }
}
