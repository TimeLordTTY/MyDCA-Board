package com.timelordtty.dca.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.timelordtty.dca.dto.BacktestRunDTO;
import com.timelordtty.dca.dto.AuthResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    private final Duration timeout;
    private final BacktestRunRepository history;
    private final UserService users;
    private final Map<String, JsonNode> cache = new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, JsonNode> eldest) { return size() > MAX_CACHE; }
    };

    public BacktestLabService(BacktestRunRepository history, UserService users) {
        this(Path.of(System.getProperty("backtest.data-root", "../data/backtest")),
                Path.of(System.getProperty("backtest.script", "../scripts/backtest/run_backtest.py")),
                System.getProperty("backtest.python", "python"), history, users);
    }

    BacktestLabService(Path root, Path script, String python, BacktestRunRepository history, UserService users) {
        this(root, script, python, history, users, Duration.ofSeconds(20));
    }

    BacktestLabService(Path root, Path script, String python, BacktestRunRepository history, UserService users,
            Duration timeout) {
        this.root = root.toAbsolutePath().normalize();
        this.script = script.toAbsolutePath().normalize();
        this.python = python;
        this.history = history;
        this.users = users;
        this.timeout = timeout;
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
        AuthResponse.UserInfo owner = users.getCurrentUser();
        String historyId = UUID.randomUUID().toString();
        Instant started = Instant.now();
        String safeData = data != null && data.matches("[A-Za-z0-9_-]{1,80}\\.csv")
                && Files.isRegularFile(root.resolve(data)) && !Files.isSymbolicLink(root.resolve(data)) ? data : null;
        String safeStrategy = STRATEGIES.contains(strategy + ":" + version) ? strategy : null;
        String safeVersion = safeStrategy == null ? null : version;
        String inputHash = null;
        String canonicalParams = null;
        String paramsHash = null;
        boolean writingHistory = false;
        try {
            if (params == null) params = Map.of();
            validate(data, strategy, version, params);
            canonicalParams = mapper.writeValueAsString(new java.util.TreeMap<>(params));
            paramsHash = sha256(canonicalParams.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Path file = root.resolve(data);
            if (Files.size(file) > 20_000_000) throw new IllegalArgumentException("历史数据超过 20 MB");
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length > 20_000_000) throw new IllegalArgumentException("历史数据超过 20 MB");
            inputHash = sha256(bytes);
            String key = owner.getId() + ":" + owner.getFamilyId() + ":" + data + ":" + inputHash + ":" + strategy + ":" + version + ":" + canonicalParams;
            JsonNode previous = cache.get(key);
            boolean cacheHit = previous != null;
            JsonNode base = cacheHit ? previous : safeResult(execute(data, strategy, version, params, inputHash),
                    inputHash, strategy, version);
            if (mapper.writeValueAsBytes(base).length > 100_000) throw new IllegalStateException("回测结果超过保存上限");
            String effectiveParams = mapper.writeValueAsString(base.path("strategy").path("params"));
            ObjectNode result = base.deepCopy();
            result.put("history_run_id", historyId);
            result.put("cache_hit", cacheHit);
            writingHistory = true;
            history.save(new BacktestRunDTO(historyId, owner.getId(), owner.getFamilyId(), data, inputHash,
                    strategy, version, effectiveParams, sha256(effectiveParams.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    base.path("provenance").path("engine_version").asText(), started, Instant.now(),
                    "SUCCESS", cacheHit, null, base.path("metrics"), result));
            if (!cacheHit) cache.put(key, base);
            return result;
        } catch (Exception error) {
            if (writingHistory) throw error;
            String status = error instanceof IllegalArgumentException ? "INVALID"
                    : error instanceof java.util.concurrent.TimeoutException ? "TIMEOUT" : "FAILED";
            history.save(new BacktestRunDTO(historyId, owner.getId(), owner.getFamilyId(), safeData, inputHash,
                    safeStrategy, safeVersion, canonicalParams, paramsHash, null, started, Instant.now(),
                    status, false, status, null, null));
            throw error;
        }
    }

    private void validate(String data, String strategy, String version, Map<String, Object> params) throws IOException {
        if (data == null || !datasets().contains(data)) throw new IllegalArgumentException("历史数据不存在或不在允许目录内");
        if (!STRATEGIES.contains(strategy + ":" + version)) throw new IllegalArgumentException("策略或版本不存在");
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
    }

    private JsonNode execute(String data, String strategy, String version, Map<String, Object> params, String inputHash) throws Exception {
        if (!Files.isRegularFile(script)) throw new IllegalStateException("回测程序不可用");
        Process process = new ProcessBuilder(python, script.toString(), "--data-root", root.toString(), "--data", data,
                "--strategy", strategy, "--version", version, "--params-stdin")
                .redirectErrorStream(true).start();
        try (var input = process.getOutputStream()) {
            input.write(mapper.writeValueAsBytes(params));
        }
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new java.util.concurrent.TimeoutException("回测超时，请缩小数据范围");
        }
        byte[] output = process.getInputStream().readNBytes(2_000_001);
        if (output.length > 2_000_000) throw new IllegalStateException("回测结果过大");
        if (process.exitValue() != 0) throw new IllegalArgumentException("回测输入无效，请检查历史数据与策略参数");
        JsonNode result = mapper.readTree(output);
        String runId = result.path("run_id").asText();
        if (runId.isBlank() || !inputHash.equals(result.path("provenance").path("data_sha256").asText()))
            throw new IllegalStateException("回测结果校验失败");
        return result;
    }

    /** Persist only the engine's known numeric and identifier fields, never arbitrary subprocess text. */
    private JsonNode safeResult(JsonNode raw, String dataHash, String strategy, String version) {
        String runId = raw.path("run_id").asText();
        JsonNode provenance = raw.path("provenance");
        String engineVersion = provenance.path("engine_version").asText();
        String start = provenance.path("start").asText();
        String end = provenance.path("end").asText();
        if (!runId.matches("[0-9a-f]{24}") || !dataHash.equals(provenance.path("data_sha256").asText())
                || !engineVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
                || !start.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || !end.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                || !strategy.equals(provenance.path("strategy").asText())
                || !version.equals(provenance.path("strategy_version").asText()))
            throw new IllegalStateException("回测结果校验失败");
        ObjectNode clean = mapper.createObjectNode();
        clean.put("schema_version", "1");
        clean.put("run_id", runId);
        ObjectNode source = clean.putObject("provenance");
        source.put("engine_version", engineVersion);
        source.put("data_sha256", dataHash);
        source.put("start", start);
        source.put("end", end);
        source.put("strategy", strategy);
        source.put("strategy_version", version);
        ObjectNode effective = numericObject(provenance.path("params"),
                "contribution", "interval_days", "ma_window", "dip_multiplier", "profit_threshold", "sell_fraction");
        source.set("params", effective);
        ObjectNode range = clean.putObject("data_range");
        range.put("start", start);
        range.put("end", end);
        range.set("rows", numeric(raw.path("data_range").path("rows")));
        ObjectNode strategyNode = clean.putObject("strategy");
        strategyNode.put("name", strategy);
        strategyNode.put("version", version);
        strategyNode.set("params", effective.deepCopy());
        clean.set("metrics", numericObject(raw.path("metrics"), "invested", "final_assets",
                "total_return", "annualized_return", "max_drawdown", "trade_count", "cash", "holdings"));
        clean.set("events", numericObject(raw.path("events"), "buys", "sells"));
        JsonNode baseline = raw.path("baseline");
        if (!baseline.path("run_id").asText().matches("[0-9a-f]{24}"))
            throw new IllegalStateException("回测基准结果校验失败");
        ObjectNode safeBaseline = clean.putObject("baseline");
        safeBaseline.put("strategy", "pure_sip");
        safeBaseline.put("run_id", baseline.path("run_id").asText());
        for (String key : List.of("final_assets_delta", "annualized_return_delta", "max_drawdown_delta"))
            safeBaseline.set(key, numeric(baseline.path(key)));
        ArrayNode warnings = clean.putArray("warnings");
        warnings.add("Historical simulation only; no live trading instruction.");
        if (clean.path("metrics").path("annualized_return").isNull())
            warnings.add("Annualized return unavailable for a single date.");
        return clean;
    }

    private ObjectNode numericObject(JsonNode raw, String... fields) {
        if (!raw.isObject()) throw new IllegalStateException("回测结果校验失败");
        ObjectNode safe = mapper.createObjectNode();
        for (String field : fields) if (raw.has(field)) safe.set(field, numeric(raw.path(field)));
        return safe;
    }

    private JsonNode numeric(JsonNode node) {
        if (!node.isNumber() && !node.isNull()) throw new IllegalStateException("回测结果校验失败");
        return node.deepCopy();
    }

    public List<BacktestRunDTO> history(int page, int size) throws IOException {
        var owner = users.getCurrentUser();
        return history.list(owner.getId(), owner.getFamilyId(), page, size).stream()
                .map(run -> new BacktestRunDTO(run.historyRunId(), run.ownerUserId(), run.ownerFamilyId(),
                        run.dataset(), run.datasetHash(), run.strategy(), run.strategyVersion(),
                        run.canonicalParams(), run.paramsHash(), run.engineVersion(), run.startedAt(),
                        run.finishedAt(), run.status(), run.cacheHit(), run.failureCode(), run.metrics(), null))
                .toList();
    }

    public BacktestRunDTO detail(String id) throws IOException {
        var owner = users.getCurrentUser();
        return history.find(id, owner.getId(), owner.getFamilyId());
    }

    public List<JsonNode> recent() throws IOException {
        var owner = users.getCurrentUser();
        List<JsonNode> results = new java.util.ArrayList<>(history.recentSuccessful(owner.getId(), owner.getFamilyId())
                .stream().map(BacktestRunDTO::result).toList());
        Collections.reverse(results);
        return results;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
