package com.timelordtty.dca.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.timelordtty.dca.dto.BacktestRunDTO;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Builds a report from saved, owner-scoped results without running the simulator. */
@Service
public class BacktestCompareService {
    private static final String DISCLAIMER = "历史回测不代表未来表现";
    private static final List<String> METRICS = List.of("total_return", "annualized_return", "max_drawdown",
            "invested", "final_assets", "cash", "trade_count");
    private static final List<String> BASELINE = List.of("final_assets_delta", "annualized_return_delta", "max_drawdown_delta");
    private static final Set<String> PARAMS = Set.of("contribution", "interval_days", "ma_window",
            "dip_multiplier", "profit_threshold", "sell_fraction");
    private final BacktestRunRepository history;
    private final UserService users;
    private final ObjectMapper mapper = new ObjectMapper();

    public BacktestCompareService(BacktestRunRepository history, UserService users) {
        this.history = history;
        this.users = users;
    }

    public ObjectNode compare(List<String> ids) throws IOException {
        return compare(ids, Instant.now());
    }

    ObjectNode compare(List<String> ids, Instant generatedAt) throws IOException {
        if (ids == null || ids.size() < 2 || ids.size() > 5 || new HashSet<>(ids).size() != ids.size())
            throw new IllegalArgumentException("请选择 2 至 5 个不同的回测记录");
        var owner = users.getCurrentUser();
        ObjectNode report = mapper.createObjectNode();
        report.put("schema_version", "1");
        report.put("generated_at", generatedAt.toString());
        report.put("disclaimer", DISCLAIMER);
        ArrayNode runs = report.putArray("runs");
        for (String id : ids) {
            BacktestRunDTO run = history.find(id, owner.getId(), owner.getFamilyId());
            if (run == null) throw new IllegalArgumentException("回测记录不存在或无权访问");
            if (!"SUCCESS".equals(run.status()) || run.result() == null)
                throw new IllegalArgumentException("失败的回测记录不能参与对比");
            runs.add(safeRun(run));
        }
        ArrayNode warnings = report.putArray("warnings");
        for (String field : List.of("dataset_hash", "strategy_version")) {
            String first = runs.get(0).path(field).asText();
            for (JsonNode run : runs) if (!first.equals(run.path(field).asText())) {
                warnings.add(field.equals("dataset_hash") ? "数据集 hash 不同，结果不可直接比较" : "策略版本不同，结果不可直接比较");
                break;
            }
        }
        String firstRange = runs.get(0).path("data_range").toString();
        for (JsonNode run : runs) if (!firstRange.equals(run.path("data_range").toString())) {
            warnings.add("数据日期区间不同，结果不可直接比较");
            break;
        }
        report.put("markdown", markdown(report));
        return report;
    }

    private ObjectNode safeRun(BacktestRunDTO run) {
        JsonNode source = run.result();
        JsonNode range = source.path("data_range");
        if (!run.historyRunId().matches("[0-9a-f-]{36}")
                || run.datasetHash() == null || !run.datasetHash().matches("[0-9a-f]{64}")
                || run.strategy() == null || !run.strategy().matches("[a-z_]{1,40}")
                || run.strategyVersion() == null || !run.strategyVersion().matches("[0-9]{1,8}")
                || run.engineVersion() == null || !run.engineVersion().matches("[0-9]+\\.[0-9]+\\.[0-9]+")
                || !range.path("start").asText().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                || !range.path("end").asText().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
            throw new IllegalArgumentException("回测历史记录格式无效");
        ObjectNode safe = mapper.createObjectNode();
        safe.put("run_id", run.historyRunId());
        safe.put("dataset_hash", run.datasetHash());
        safe.put("strategy", run.strategy());
        safe.put("strategy_version", run.strategyVersion());
        safe.put("engine_version", run.engineVersion());
        ObjectNode params = safe.putObject("canonical_params");
        JsonNode rawParams = source.path("strategy").path("params");
        if (!rawParams.isObject()) throw new IllegalArgumentException("回测历史记录格式无效");
        rawParams.properties().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
            if (!PARAMS.contains(entry.getKey()) || !entry.getValue().isNumber())
                throw new IllegalArgumentException("回测历史记录格式无效");
            params.set(entry.getKey(), entry.getValue());
        });
        ObjectNode safeRange = safe.putObject("data_range");
        safeRange.put("start", range.path("start").asText());
        safeRange.put("end", range.path("end").asText());
        copyNumbers(safe.putObject("metrics"), source.path("metrics"), METRICS);
        copyNumbers(safe.putObject("baseline_delta"), source.path("baseline"), BASELINE);
        return safe;
    }

    private void copyNumbers(ObjectNode target, JsonNode source, List<String> fields) {
        if (!source.isObject()) throw new IllegalArgumentException("回测历史记录格式无效");
        for (String field : fields) {
            JsonNode value = source.path(field);
            if (!value.isNumber() && !value.isNull()) throw new IllegalArgumentException("回测历史记录格式无效");
            target.set(field, value.deepCopy());
        }
    }

    private String markdown(ObjectNode report) {
        StringBuilder out = new StringBuilder("# 回测实验对比报告\n\n生成时间：")
                .append(report.path("generated_at").asText()).append("\n\n")
                .append(DISCLAIMER).append("。\n\n");
        for (JsonNode warning : report.path("warnings")) out.append("- ⚠ ").append(warning.asText()).append("\n");
        if (!report.path("warnings").isEmpty()) out.append("\n");
        out.append("| 指标 | ");
        for (JsonNode run : report.path("runs")) out.append(run.path("run_id").asText()).append(" | ");
        out.append("\n| --- | ");
        for (JsonNode ignored : report.path("runs")) out.append("--- | ");
        out.append("\n");
        for (String field : List.of("total_return", "annualized_return", "max_drawdown", "invested",
                "final_assets", "cash", "trade_count", "final_assets_delta", "annualized_return_delta",
                "max_drawdown_delta")) {
            out.append("| ").append(field).append(" | ");
            for (JsonNode run : report.path("runs")) {
                JsonNode value = run.path(METRICS.contains(field) ? "metrics" : "baseline_delta").path(field);
                out.append(value.isNull() ? "—" : value.asText()).append(" | ");
            }
            out.append("\n");
        }
        out.append("\n");
        for (JsonNode run : report.path("runs")) {
            out.append("## ").append(run.path("run_id").asText()).append("\n\n")
                    .append("- dataset hash: ").append(run.path("dataset_hash").asText()).append("\n")
                    .append("- strategy/version: ").append(run.path("strategy").asText()).append("/")
                    .append(run.path("strategy_version").asText()).append("\n")
                    .append("- canonical params: `").append(run.path("canonical_params").toString()).append("`\n")
                    .append("- engine version: ").append(run.path("engine_version").asText()).append("\n")
                    .append("- data range: ").append(run.path("data_range").path("start").asText()).append(" 至 ")
                    .append(run.path("data_range").path("end").asText()).append("\n\n");
        }
        return out.toString();
    }
}
