package com.timelordtty.dca.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.timelordtty.dca.mapper.ResearchPlanMapper;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Explicit research mutations only; reading warnings never updates persisted evidence. */
@Service
public class ResearchPlanService {
    public record Create(String name, String description, List<String> runIds,
            BacktestResearchService.Thresholds thresholds, String candidateId) {}
    public record Edit(String name, String description, JsonNode paramsDraft, String status) {}
    private final ResearchPlanMapper plans;
    private final BacktestResearchService research;
    private final BacktestRunRepository history;
    private final BacktestCompareService compare;
    private final UserService users;
    private final Path datasets;
    private final ObjectMapper json = new ObjectMapper();

    @org.springframework.beans.factory.annotation.Autowired
    public ResearchPlanService(ResearchPlanMapper plans, BacktestResearchService research,
            BacktestRunRepository history, BacktestCompareService compare, UserService users) {
        this(plans, research, history, compare, users,
                Path.of(System.getProperty("backtest.data-root", "../data/backtest")));
    }
    ResearchPlanService(ResearchPlanMapper plans, BacktestResearchService research,
            BacktestRunRepository history, BacktestCompareService compare, UserService users, Path datasets) {
        this.plans = plans; this.research = research; this.history = history;
        this.compare = compare; this.users = users; this.datasets = datasets.toAbsolutePath().normalize();
    }
    public static String candidateId(JsonNode candidate) {
        return digest((candidate.path("strategy").asText() + "/" + candidate.path("strategy_version").asText()
                + "/" + candidate.path("canonical_params")).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    static String digest(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public ObjectNode create(Create request) throws IOException {
        text(request.name(), 120, true); text(request.description(), 4000, false);
        var owner = users.getCurrentUser();
        JsonNode report = research.research(request.runIds(), request.thresholds());
        JsonNode candidate = null;
        for (JsonNode item : report.path("candidates"))
            if (candidateId(item).equals(request.candidateId())) candidate = item;
        if (candidate == null) throw new IllegalArgumentException("研究候选不存在，请重新选择");
        ObjectNode plan = json.createObjectNode();
        String id = UUID.randomUUID().toString();
        plan.put("id", id); plan.put("ownerUserId", owner.getId());
        if (owner.getFamilyId() == null) plan.putNull("ownerFamilyId"); else plan.put("ownerFamilyId", owner.getFamilyId());
        plan.put("name", request.name()); plan.put("description", request.description()); plan.put("status", "DRAFT");
        plan.put("sourceCandidateId", request.candidateId());
        plan.set("sourceRunIds", candidate.path("run_ids").deepCopy());
        plan.set("strategy", candidate.path("strategy").deepCopy());
        plan.set("strategyVersion", candidate.path("strategy_version").deepCopy());
        plan.set("canonicalParamsSnapshot", candidate.path("canonical_params").deepCopy());
        plan.set("paramsDraft", candidate.path("canonical_params").deepCopy());
        plan.set("datasetHashes", candidate.path("dataset_hashes").deepCopy());
        ObjectNode bundle = plan.putObject("evidenceSnapshot");
        bundle.set("candidate", candidate.deepCopy()); bundle.set("thresholds", report.path("thresholds").deepCopy());
        bundle.put("ruleVersion", BacktestResearchService.RULE_VERSION);
        var runs = bundle.putArray("runs");
        for (JsonNode runId : candidate.path("run_ids")) {
            var run = history.find(runId.asText(), owner.getId(), owner.getFamilyId());
            if (run == null || !"SUCCESS".equals(run.status())) throw new IllegalArgumentException("来源回测已失效，请重新选择");
            runs.add(compare.safeRun(run));
        }
        plan.put("evidenceBundleRef", "sha256:" + digest(json.writeValueAsBytes(bundle)));
        String now = Instant.now().toString(); plan.put("createdAt", now); plan.put("updatedAt", now);
        plan.put("disclaimer", "历史研究不代表未来表现；研究方案不是投资建议，也不会生成交易");
        if (json.writeValueAsBytes(plan).length > 256_000) throw new IllegalArgumentException("研究方案证据超过大小上限");
        ObjectNode response = warnings(plan.deepCopy());
        if (plans.insert(id, owner.getId(), owner.getFamilyId(), plan.toString()) != 1)
            throw new IllegalStateException("研究方案保存失败");
        return response;
    }
    public ObjectNode detail(String id) throws IOException {
        var owner = users.getCurrentUser();
        String payload = plans.find(validId(id), owner.getId(), owner.getFamilyId());
        return payload == null ? null : warnings((ObjectNode) json.readTree(payload));
    }
    public List<ObjectNode> list(int page, int size) throws IOException {
        if (page < 0 || size < 1 || size > 50 || (long) page * size > 100000) throw new IllegalArgumentException("分页参数超出范围");
        var owner = users.getCurrentUser(); var result = new ArrayList<ObjectNode>();
        for (String value : plans.list(owner.getId(), owner.getFamilyId(), page * size, size))
            result.add(warnings((ObjectNode) json.readTree(value)));
        return result;
    }
    public ObjectNode edit(String id, Edit request) throws IOException {
        var owner = users.getCurrentUser();
        String previous = plans.find(validId(id), owner.getId(), owner.getFamilyId());
        if (previous == null) throw new IllegalArgumentException("研究方案不存在或无权访问");
        ObjectNode plan = (ObjectNode) json.readTree(previous);
        if ("ARCHIVED".equals(plan.path("status").asText())) throw new IllegalArgumentException("已归档研究方案不可修改");
        if (request.name() != null) { text(request.name(), 120, true); plan.put("name", request.name()); }
        if (request.description() != null) { text(request.description(), 4000, false); plan.put("description", request.description()); }
        if (request.paramsDraft() != null) {
            if (!request.paramsDraft().isObject() || request.paramsDraft().size() > 50 || json.writeValueAsBytes(request.paramsDraft()).length > 8000)
                throw new IllegalArgumentException("研究参数草稿必须为有界 JSON 对象");
            plan.set("paramsDraft", request.paramsDraft().deepCopy());
        }
        if (request.status() != null) {
            if (!Set.of("DRAFT", "ACTIVE", "ARCHIVED").contains(request.status())) throw new IllegalArgumentException("无效的研究状态");
            plan.put("status", request.status());
        }
        plan.put("updatedAt", Instant.now().toString());
        ObjectNode response = warnings(plan.deepCopy());
        if (plans.update(id, owner.getId(), owner.getFamilyId(), plan.toString(), previous) != 1)
            throw new IllegalStateException("研究方案已变更，请刷新后重试");
        return response;
    }
    private ObjectNode warnings(ObjectNode plan) throws IOException {
        var owner = users.getCurrentUser(); var warnings = plan.putArray("warnings");
        for (JsonNode saved : plan.path("evidenceSnapshot").path("runs")) {
            var run = history.find(saved.path("run_id").asText(), owner.getId(), owner.getFamilyId());
            if (run == null || !"SUCCESS".equals(run.status()) || run.result() == null) {
                warnings.add("来源回测已失效；保留的历史证据快照仍可查看"); continue;
            }
            if (!saved.equals(compare.safeRun(run))) warnings.add("来源回测证据已变更；方案保留创建时快照");
            Path file = datasets.resolve(run.dataset()).normalize();
            if (!file.startsWith(datasets) || !Files.isRegularFile(file) || Files.isSymbolicLink(file)
                    || !file.toRealPath().startsWith(datasets.toRealPath())) {
                warnings.add("来源数据集已失效或不可用"); continue;
            }
            // Stream hashing keeps read-only checks bounded in memory even for large CSV files.
            try (var input = Files.newInputStream(file)) {
                MessageDigest hash = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) hash.update(buffer, 0, count);
                if (!HexFormat.of().formatHex(hash.digest()).equals(saved.path("dataset_hash").asText()))
                    warnings.add("来源数据集已变更，hash 与历史证据不一致");
            } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        }
        return plan;
    }
    private static String validId(String id) {
        if (id == null || !id.matches("[0-9a-f-]{36}")) throw new IllegalArgumentException("无效的研究方案 ID"); return id;
    }
    private static void text(String value, int limit, boolean required) {
        if ((required && (value == null || value.isBlank())) || (value != null && value.length() > limit))
            throw new IllegalArgumentException("研究名称或备注长度无效");
    }
}
