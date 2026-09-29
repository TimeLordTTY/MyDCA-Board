package com.timelordtty.dca.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Deterministic evidence gates over saved, owner-scoped runs. No execution services are involved. */
@Service
public class BacktestResearchService {
    public record Thresholds(Integer minSampleDays, Double maxDrawdown,
            Double minBaselineAnnualizedDelta, Integer minTradeCount) {}

    private final BacktestRunRepository history;
    private final BacktestCompareService compare;
    private final UserService users;
    private final ObjectMapper mapper = new ObjectMapper();

    public BacktestResearchService(BacktestRunRepository history, BacktestCompareService compare, UserService users) {
        this.history = history;
        this.compare = compare;
        this.users = users;
    }

    public ObjectNode research(List<String> ids, Thresholds input) throws IOException {
        if (ids == null || ids.isEmpty() || ids.size() > 50 || new HashSet<>(ids).size() != ids.size())
            throw new IllegalArgumentException("请选择 1 至 50 个不同的回测记录");
        Thresholds gates = input == null ? new Thresholds(180, 0.30, 0.0, 3) : input;
        if (gates.minSampleDays() == null || gates.minSampleDays() < 1 || gates.minSampleDays() > 3650
                || gates.maxDrawdown() == null || !Double.isFinite(gates.maxDrawdown())
                || gates.maxDrawdown() < 0 || gates.maxDrawdown() > 1
                || gates.minBaselineAnnualizedDelta() == null || !Double.isFinite(gates.minBaselineAnnualizedDelta())
                || gates.minBaselineAnnualizedDelta() < -1 || gates.minBaselineAnnualizedDelta() > 1
                || gates.minTradeCount() == null || gates.minTradeCount() < 0 || gates.minTradeCount() > 100000)
            throw new IllegalArgumentException("研究阈值超出允许范围");

        var owner = users.getCurrentUser();
        Map<String, List<ObjectNode>> groups = new TreeMap<>();
        ObjectNode report = mapper.createObjectNode();
        report.put("schema_version", "1");
        report.put("disclaimer", "历史结果不代表未来表现；研究候选不是买卖或执行建议");
        ObjectNode applied = report.putObject("thresholds");
        applied.put("min_sample_days", gates.minSampleDays());
        applied.put("max_drawdown", gates.maxDrawdown());
        applied.put("min_baseline_annualized_delta", gates.minBaselineAnnualizedDelta());
        applied.put("min_trade_count", gates.minTradeCount());
        ArrayNode excluded = report.putArray("excluded_runs");
        for (String id : ids.stream().sorted().toList()) {
            var run = history.find(id, owner.getId(), owner.getFamilyId());
            if (run == null) throw new IllegalArgumentException("回测记录不存在或无权访问");
            if (!"SUCCESS".equals(run.status()) || run.result() == null) {
                ObjectNode item = excluded.addObject();
                item.put("run_id", id);
                item.put("status", run.status());
                item.put("reason", "非成功回测不能作为研究证据");
                continue;
            }
            ObjectNode safe = compare.safeRun(run);
            String key = safe.path("strategy").asText() + "/" + safe.path("strategy_version").asText()
                    + "/" + safe.path("canonical_params").toString();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(safe);
        }
        ArrayNode candidates = report.putArray("candidates");
        for (List<ObjectNode> runs : groups.values()) {
            ObjectNode candidate = candidates.addObject();
            JsonNode first = runs.get(0);
            candidate.put("strategy", first.path("strategy").asText());
            candidate.put("strategy_version", first.path("strategy_version").asText());
            candidate.set("canonical_params", first.path("canonical_params").deepCopy());
            ArrayNode runIds = candidate.putArray("run_ids");
            ArrayNode hashes = candidate.putArray("dataset_hashes");
            ArrayNode evidence = candidate.putArray("evidence");
            ArrayNode reasons = candidate.putArray("reasons");
            ArrayNode warnings = candidate.putArray("warnings");
            List<String> uniqueHashes = new ArrayList<>();
            boolean insufficient = false;
            boolean rejected = false;
            String firstRange = first.path("data_range").toString();
            for (ObjectNode run : runs) {
                String hash = run.path("dataset_hash").asText();
                runIds.add(run.path("run_id").asText());
                if (!uniqueHashes.contains(hash)) uniqueHashes.add(hash);
                ObjectNode item = evidence.addObject();
                item.put("run_id", run.path("run_id").asText());
                item.set("data_range", run.path("data_range").deepCopy());
                item.set("metrics", run.path("metrics").deepCopy());
                item.set("baseline_delta", run.path("baseline_delta").deepCopy());
                long days;
                try {
                    days = ChronoUnit.DAYS.between(LocalDate.parse(run.path("data_range").path("start").asText()),
                            LocalDate.parse(run.path("data_range").path("end").asText()));
                } catch (RuntimeException error) {
                    throw new IllegalArgumentException("回测历史记录格式无效");
                }
                item.put("sample_days", days);
                if (days < gates.minSampleDays()) {
                    reasons.add(item.path("run_id").asText() + "：样本区间不足 " + gates.minSampleDays() + " 天");
                    insufficient = true;
                }
                JsonNode metrics = run.path("metrics");
                JsonNode drawdown = metrics.path("max_drawdown");
                JsonNode trades = metrics.path("trade_count");
                JsonNode delta = run.path("baseline_delta").path("annualized_return_delta");
                if (!drawdown.isNumber() || !trades.isNumber() || !delta.isNumber()) {
                    reasons.add(item.path("run_id").asText() + "：关键指标或基准差异缺失");
                    insufficient = true;
                    continue;
                }
                if (-drawdown.asDouble() > gates.maxDrawdown()) {
                    reasons.add(item.path("run_id").asText() + "：最大回撤超过阈值");
                    rejected = true;
                }
                if (delta.asDouble() < gates.minBaselineAnnualizedDelta()) {
                    reasons.add(item.path("run_id").asText() + "：相对基准年化差异低于阈值");
                    rejected = true;
                }
                if (trades.asInt() < gates.minTradeCount()) {
                    reasons.add(item.path("run_id").asText() + "：交易次数低于阈值");
                    insufficient = true;
                }
                if (!firstRange.equals(run.path("data_range").toString())) {
                    insufficient = true;
                    warnings.add("数据日期区间不同，证据不可合并判断");
                }
            }
            uniqueHashes.stream().sorted().forEach(hashes::add);
            if (uniqueHashes.size() > 1) {
                insufficient = true;
                warnings.add("数据集 hash 不同，证据不可合并判断");
            }
            candidate.put("evidence_status", insufficient ? "INSUFFICIENT" : "CONSISTENT");
            candidate.put("status", insufficient ? "EVIDENCE_INSUFFICIENT" : rejected ? "DOES_NOT_MEET_CRITERIA" : "WORTH_FURTHER_RESEARCH");
            if (reasons.isEmpty() && warnings.isEmpty()) reasons.add("所有已选历史记录均满足当前阈值");
        }
        return report;
    }
}
