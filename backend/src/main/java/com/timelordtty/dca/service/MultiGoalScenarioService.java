package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.MultiGoalScenarioDTO.*;
import com.timelordtty.dca.dto.GoalForecastDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class MultiGoalScenarioService {
    private final GoalTrackingService goals;
    private final GoalForecastService forecasts;
    public MultiGoalScenarioService(GoalTrackingService goals, GoalForecastService forecasts) {
        this.goals=goals; this.forecasts=forecasts;
    }
    @Transactional(readOnly=true)
    public Result compare(Request request) throws IOException {
        if(request==null || request.scenarios()==null || request.scenarios().size()!=2)
            throw new IllegalArgumentException("须显式提供两个情景");
        for(var input:request.scenarios()) validate(input);
        var left=request.scenarios().get(0); var right=request.scenarios().get(1);
        if(!ids(left).equals(ids(right))) throw new IllegalArgumentException("两个情景须选择相同目标");
        var started=Instant.now();
        var results=new ArrayList<Scenario>();
        var reads=new GoalForecastService.Reads();
        for(var input:request.scenarios()) results.add(evaluate(input,reads));
        var changes=new ArrayList<Change>();
        change(changes,"startMonth",left.cashflow().startMonth(),right.cashflow().startMonth());
        change(changes,"endMonth",left.cashflow().endMonth(),right.cashflow().endMonth());
        change(changes,"annualRate",left.cashflow().annualRate(),right.cashflow().annualRate());
        change(changes,"mode",left.cashflow().mode(),right.cashflow().mode());
        change(changes,"months",left.cashflow().months(),right.cashflow().months());
        change(changes,"monthlyExtraSavings",left.cashflow().monthlyExtraSavings(),right.cashflow().monthlyExtraSavings());
        for(var allocation:left.allocations()) change(changes,"monthlyAmount:"+allocation.goalId(),
                allocation.monthlyAmount(),right.allocations().stream().filter(a->a.goalId().equals(allocation.goalId())).findFirst().orElseThrow().monthlyAmount());
        return new Result(started,Instant.now(),"授权目标进度及月度预算读取；统计日见各目标asOfDate，实际流水证据见actualReading；非实时保证",List.copyOf(results),List.copyOf(changes));
    }
    private static Set<String> ids(Input input) {
        var ids=new HashSet<String>();
        for(var a:input.allocations()) ids.add(a.goalId());
        return ids;
    }
    private static void validate(Input input) {
        if(input==null || input.name()==null || input.name().isBlank() || input.name().length()>100 || input.cashflow()==null
                || input.allocations()==null || input.allocations().size()<2 || input.allocations().size()>8)
            throw new IllegalArgumentException("情景须包含名称、现金流假设及2至8个目标");
        var ids=new HashSet<String>();
        for(var a:input.allocations()) {
            if(a==null || a.goalId()==null || a.goalId().isBlank() || !ids.add(a.goalId()))
                throw new IllegalArgumentException("目标须非空且不得重复");
            var n=a.monthlyAmount();
            if(n!=null && (n.signum()<0 || n.scale()>2 || n.precision()-n.scale()>18))
                throw new IllegalArgumentException("月度额度须非负，最多18位整数及2位小数");
        }
    }
    private Scenario evaluate(Input input, GoalForecastService.Reads reads) throws IOException {
        var metadata=new ArrayList<com.timelordtty.dca.dto.GoalTrackingDTO.Goal>();
        // Authorize every goal before reading cash flow. Different scopes cannot share one pool.
        for(var a:input.allocations()) {
            if(!reads.goals.containsKey(a.goalId())) reads.goals.put(a.goalId(),goals.detail(a.goalId()));
            metadata.add(reads.goals.get(a.goalId()));
        }
        var first=metadata.get(0).config();
        for(var goal:metadata) if(!first.scope().equals(goal.config().scope()) || !first.currency().equals(goal.config().currency()))
            throw new IllegalArgumentException("共享预算目标须具有相同作用域及币种");
        var evidence=forecasts.forecastAllocated(input.allocations().get(0).goalId(),input.cashflow(),null,reads);
        BigDecimal total=input.allocations().stream().map(Allocation::monthlyAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
        boolean unspecified=input.allocations().stream().anyMatch(a->a.monthlyAmount()==null);
        var months=new ArrayList<BudgetMonth>();
        for(var row:evidence.baseline().months()) {
            var upper=row.surplusUpperBound();
            months.add(new BudgetMonth(row.month(),upper,total,unspecified?"UNSPECIFIED":"SPECIFIED",upper==null?null:total.compareTo(upper)>0));
        }
        var output=new ArrayList<GoalResult>();
        for(int g=0;g<input.allocations().size();g++) {
            var a=input.allocations().get(g);
            var caps=new ArrayList<BigDecimal>();
            for(var row:months) caps.add(row.overLimit()==null || row.overLimit()?null:a.monthlyAmount());
            var forecast=forecasts.forecastAllocated(a.goalId(),input.cashflow(),caps,reads);
            var projection=forecast.mathematicalScenario()==null?forecast.baseline():forecast.mathematicalScenario();
            var target=metadata.get(g).config().targetDate();
            String deadline=projection.achievedMonth()==null?"UNKNOWN_OR_NOT_REACHED":
                    YearMonth.parse(projection.achievedMonth()).isAfter(YearMonth.from(target))?"AFTER_TARGET_MONTH":"BY_TARGET_MONTH";
            output.add(new GoalResult(a.goalId(),target,a.monthlyAmount()==null?"UNSPECIFIED":"SPECIFIED",forecast,deadline));
        }
        return new Scenario(input.name(),input,List.copyOf(months),List.copyOf(output),List.of(
                "只读假设，不执行方案；超限月份不生成可行的目标进度；未填写额度不自动分配",
                "目标起始资产可能重叠，不能相加为独立资金；只有新增月度额度参与共享预算守恒校验",
                "UNKNOWN/PARTIAL及统计日绑定各目标结果；同请求复用相同来源快照；跨来源读取非原子实时承诺",
                "日期仅比较目标月份，月末投入；收益为数学假设，缺省仅提供零收益基线"));
    }
    private static void change(List<Change> changes,String field,Object before,Object after) {
        if(!Objects.equals(before,after)) changes.add(new Change(field,before,after));
    }
}
