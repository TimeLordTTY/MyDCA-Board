package com.timelordtty.dca.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import java.util.Map;

/** Owner-scoped plan snapshot to the existing isolated simulator; no business writes. */
@Service
public class ResearchPlanBacktestService {
    public record Run(String dataset) {}
    private final ResearchPlanService plans;
    private final BacktestLabService lab;
    private final ObjectMapper json = new ObjectMapper();
    public ResearchPlanBacktestService(ResearchPlanService plans, BacktestLabService lab) {
        this.plans = plans; this.lab = lab;
    }
    public JsonNode run(String id, Run request) throws Exception {
        var plan = plans.detail(id);
        if (plan == null) throw new IllegalArgumentException("研究方案不存在或无权访问");
        if ("ARCHIVED".equals(plan.path("status").asText()))
            throw new IllegalArgumentException("已归档研究方案不可运行");
        // Only dataset selection comes from the request. Strategy/version and draft come from the saved plan.
        var draft = plan.path("paramsDraft");
        if (!draft.isObject()) throw new IllegalArgumentException("研究参数草稿必须为 JSON 对象");
        Map<String, Object> params = json.convertValue(draft, new TypeReference<Map<String, Object>>() {});
        return lab.run(request.dataset(), plan.path("strategy").asText(),
                plan.path("strategyVersion").asText(), params, id);
    }
}
