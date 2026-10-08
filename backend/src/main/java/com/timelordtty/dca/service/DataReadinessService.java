package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.DataReadinessDTO;
import com.timelordtty.dca.dto.DataReadinessDTO.*;
import com.timelordtty.dca.dto.FinanceRadarDTO;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Bounded, authorized reads only. No schema probes, external fetches or persisted evaluations. */
@Service
public class DataReadinessService {
    private final UserService users;
    private final FamilyService families;
    private final GoalTrackingService goals;
    private final MonthlyBudgetService budgets;
    private final RiskWatchService risks;
    private final AllocationPolicyService policies;
    private final ResearchPlanService research;
    private final FinanceRadarService radar;

    public DataReadinessService(UserService users, FamilyService families, GoalTrackingService goals,
            MonthlyBudgetService budgets, RiskWatchService risks, AllocationPolicyService policies,
            ResearchPlanService research, FinanceRadarService radar) {
        this.users=users; this.families=families; this.goals=goals; this.budgets=budgets;
        this.risks=risks; this.policies=policies; this.research=research; this.radar=radar;
    }

    // Independent read transactions keep one unavailable source from rolling back the whole report.
    @Transactional(readOnly=true, propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public DataReadinessDTO diagnose(String scope, String month) {
        if (!Set.of("PERSONAL", "FAMILY").contains(scope)) throw new IllegalArgumentException();
        YearMonth period;
        try {
            period=YearMonth.parse(month);
            if (!period.toString().equals(month) || period.getYear()<1000 || period.getYear()>9998)
                throw new IllegalArgumentException();
        } catch (RuntimeException e) { throw new IllegalArgumentException(); }
        var owner=users.getCurrentUser();
        if ("FAMILY".equals(scope)) {
            if (owner.getFamilyId()==null) throw new AccessDeniedException("无家庭权限");
            try { families.assertAdmin(owner.getId(),owner.getFamilyId()); }
            catch (RuntimeException e) { throw new AccessDeniedException("无家庭权限"); }
        }
        var result=new ArrayList<Evidence>();
        FinanceRadarDTO facts=null;
        try { facts=radar.getRadar(owner.getId(),owner.getFamilyId(),scope); }
        catch (RuntimeException e) { /* A failed read is not evidence of an empty portfolio. */ }
        final FinanceRadarDTO observed=facts;
        result.add(assetEvidence(observed,scope));
        result.add(marketEvidence(observed));
        result.add(evidence("EXCHANGE_RATES",State.UNKNOWN,"现有汇总仅支持人民币，未确认汇率及其时间",
                "现有人民币资产口径",null,"人工核对非人民币资产与汇率来源"));
        result.add(read("GOALS","目标只读进度",()-> {
            var page=goals.list(0,50);
            var selected=page.stream().filter(g->scope.equals(g.config().scope())).toList();
            if(selected.isEmpty()) return missing("GOALS","目标只读进度");
            boolean complete=observed!=null && observed.date()!=null && observed.date().equals(LocalDate.now()) && selected.stream().allMatch(g->GoalTrackingService.observe(g,observed).quality()
                    ==com.timelordtty.dca.dto.GoalTrackingDTO.Quality.OK);
            return evidence("GOALS",complete && page.size()<50?State.READY:State.PARTIAL,
                    complete?"已观察目标进度；最多检查首50项，满页时覆盖未知":"目标进度含未知或部分资产，不能判定达标",
                    "目标只读进度",observed==null?null:String.valueOf(observed.date()),"人工核对目标币种、资产范围与分页覆盖");
        }));
        result.add(read("BUDGETS","月度预算只读对比",()-> {
            var page=budgets.list(0,50);
            var selected=page.stream().filter(b->scope.equals(b.config().scope()) && month.equals(b.config().month())).toList();
            if(selected.isEmpty()) return missing("BUDGETS","月度预算只读对比");
            boolean complete=true;
            for(var b:selected) if(budgets.comparison(b.id()).quality()!=com.timelordtty.dca.dto.MonthlyBudgetDTO.Quality.OK) complete=false;
            return evidence("BUDGETS",complete && page.size()<50?State.READY:State.PARTIAL,
                    complete?"所选月份预算对比可读；不证明全部现金流已覆盖":"所选月份预算对比含未知或部分流水",
                    "月度预算只读对比",month,"人工核对流水分类、币种、月份和未纳入预算的收支");
        }));
        result.add(read("RISK_RULES","风险规则与既有历史",()-> {
            var selected=risks.list(0,50).stream().filter(r->scope.equals(r.config().scope())).toList();
            if(selected.isEmpty()) return missing("RISK_RULES","风险规则与既有历史");
            String oldest=null;
            boolean missing=false;
            for(var rule:selected) {
                var history=risks.history(rule.id(),0,1);
                if(history.isEmpty() || history.get(0).sourceDataTimestamp()==null) { missing=true; continue; }
                var time=LocalDate.parse(history.get(0).sourceDataTimestamp()).toString();
                if(oldest==null || time.compareTo(oldest)<0) oldest=time;
                if("UNKNOWN".equals(history.get(0).status())) missing=true;
            }
            // evaluate persists history: deliberately never invoke it from this GET.
            return evidence("RISK_RULES",State.PARTIAL,missing?"规则可读；历史证据缺失或未知":"规则与历史可读；历史快照不证明当前风险数据完整",
                    "风险规则与既有历史",oldest,"人工查看规则历史与来源时间；需要时主动评估");
        }));
        result.add(read("ALLOCATION","配置只读观察",()-> {
            var page=policies.list(0,50);
            var selected=page.stream().filter(p->scope.equals(p.config().scope())).toList();
            if(selected.isEmpty()) return missing("ALLOCATION","配置只读观察");
            boolean complete=true;
            for(var p:selected) if(policies.evaluate(p.id()).status()==com.timelordtty.dca.dto.AllocationPolicyDTO.Status.UNKNOWN) complete=false;
            return evidence("ALLOCATION",complete && page.size()<50?State.READY:State.PARTIAL,
                    complete?"配置观察可读；不代表配置达标":"配置观察含未知报价、成本或停用规则",
                    "配置只读观察",observed==null?null:String.valueOf(observed.date()),"人工核对成本、报价与规则范围");
        }));
        result.add(read("RESEARCH","既有研究证据校验",()-> {
            var plans=research.list(0,50);
            if(plans.isEmpty()) return missing("RESEARCH","既有研究证据校验");
            String oldest=null; boolean warnings=false;
            for(var plan:plans) {
                if(!plan.path("warnings").isEmpty() || plan.path("evidenceSnapshot").path("runs").isEmpty()) warnings=true;
                var rawTime=plan.path("createdAt").asText(null);
                var time=rawTime==null?null:Instant.parse(rawTime).toString();
                if(time!=null && (oldest==null || time.compareTo(oldest)<0)) oldest=time;
            }
            return evidence("RESEARCH",State.PARTIAL,warnings?"研究来源缺失、变更或证据不完整":"研究历史可读；历史证据不证明当前时效或家庭完整覆盖",
                    "既有研究证据校验（时间为方案创建时间）",oldest,"人工查看来源回测、数据集校验警告与历史期间");
        }));
        result.add(evidence("FORECAST_INPUTS",State.UNKNOWN,"未提供连续月份、目标预算选择及现金流覆盖声明，无法确认预测输入完整",
                "现有目标现金流契约",month,"人工逐月选择同作用域同币种预算并明确覆盖声明"));
        result.add(evidence("SCHEMA",State.UNKNOWN,"目标、预算、风险规则及历史、配置规则、研究存储前提未获部署确认；只读查询不证明全部迁移已部署",
                "授权业务读取",null,"由部署管理员在获授权环境人工核对迁移记录与前提"));
        return new DataReadinessDTO(scope,month,Instant.now(),List.copyOf(result));
    }

    static Evidence assetEvidence(FinanceRadarDTO facts,String scope) {
        var a=facts==null?null:facts.assets();
        State state=a==null?State.UNAVAILABLE:"OK".equals(a.status()) && a.investmentCost()!=null?State.READY:State.PARTIAL;
        if(state==State.READY && (facts.date()==null || !facts.date().equals(LocalDate.now()))) state=State.PARTIAL;
        if(state==State.READY && a.cashBalance()!=null && a.cashBalance().signum()==0
                && a.positionValue()!=null && a.positionValue().signum()==0
                && (facts.markets()==null || facts.markets().isEmpty())) state=State.UNKNOWN;
        if("FAMILY".equals(scope) && state==State.READY) state=State.PARTIAL;
        return evidence("ASSETS",state,a==null?"资产读取不可用，不能按零处理":
                "FAMILY".equals(scope)?"家庭资产只读汇总；订单和结算家庭归属不完整":
                state==State.READY?"人民币资产汇总可读；空持仓不证明来源完整":"资产报价、成本或币种存在未知",
                "现有财富雷达",facts==null || facts.date()==null?null:facts.date().toString(),"人工核对账户与持仓来源及未纳入资产");
    }

    static Evidence marketEvidence(FinanceRadarDTO facts) {
        if(facts==null) return evidence("MARKET",State.UNAVAILABLE,"行情读取不可用","现有财富雷达",null,"稍后重试并人工核对行情");
        if(facts.markets()==null || facts.markets().isEmpty()) return missing("MARKET","持仓行情与净值");
        boolean complete=true; LocalDate oldest=null;
        for(var m:facts.markets()) {
            if(!"OK".equals(m.status()) || !"OK".equals(m.indicatorStatus()) || m.priceDate()==null || m.valuationDate()==null || m.indicatorDate()==null) complete=false;
            for(var date:Arrays.asList(m.priceDate(),m.valuationDate(),m.indicatorDate())) if(date!=null) {
                if(date.isAfter(LocalDate.now()) || date.isBefore(LocalDate.now().minusDays(3))) complete=false;
                if(oldest==null || date.isBefore(oldest)) oldest=date;
            }
        }
        return evidence("MARKET",complete?State.READY:State.PARTIAL,complete?"持仓行情、估值与指标在3个日历日窗口内":"行情、估值或指标缺失、过旧或时间异常",
                "持仓行情、净值与指标",oldest==null?null:oldest.toString(),"人工核对交易日与各产品来源时间；不触发外部刷新");
    }
    private interface Read { Evidence get() throws Exception; }
    private static Evidence read(String area,String source,Read action) {
        try { return action.get(); }
        catch (Exception e) { return evidence(area,State.UNAVAILABLE,"授权读取未成功，不能据此判断无数据或迁移缺失",source,null,"核对访问权限与服务可用性；部署问题由管理员检查"); }
    }
    private static Evidence missing(String area,String source) {
        return evidence(area,State.UNKNOWN,"当前可见首50项没有对应数据，完整覆盖未知",source,null,"人工核对作用域、月份及后续分页；必要时配置数据");
    }
    private static Evidence evidence(String area,State state,String reason,String source,String time,String next) {
        return new Evidence(area,state,reason,source,time,next);
    }
}
