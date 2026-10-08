package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.GoalForecastDTO.*;
import com.timelordtty.dca.dto.GoalTrackingDTO.*;
import com.timelordtty.dca.dto.MonthlyBudgetDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Reuses authorized goal/budget reads; never accesses a financial writer. */
@Service
public class GoalForecastService {
    private final GoalTrackingService goals;
    private final MonthlyBudgetService budgets;
    public GoalForecastService(GoalTrackingService goals, MonthlyBudgetService budgets) {
        this.goals=goals; this.budgets=budgets;
    }
    private static YearMonth month(String value) {
        try {
            var m=YearMonth.parse(value);
            if(!m.toString().equals(value) || m.getYear()<1000 || m.getYear()>9998) throw new IllegalArgumentException();
            return m;
        } catch(Exception e) { throw new IllegalArgumentException("月份须为1000至9998年的YYYY-MM"); }
    }
    private static void money(BigDecimal n) {
        if(n!=null && (n.signum()<0 || n.scale()>2 || n.precision()-n.scale()>18))
            throw new IllegalArgumentException("额外储蓄须非负，最多18位整数和2位小数");
    }
    @Transactional(readOnly=true)
    public Forecast forecast(String id, Request request) throws IOException {
        return forecastAllocated(id, request, null);
    }
    Forecast forecastAllocated(String id, Request request, List<BigDecimal> allocations) throws IOException {
        return forecastAllocated(id, request, allocations, new Reads());
    }
    /** Request-local evidence cache: both scenarios consume the same authorized snapshots. */
    static final class Reads {
        final Map<String, Goal> goals = new HashMap<>();
        final Map<String, Progress> progress = new HashMap<>();
        final Map<String, MonthlyBudgetDTO.Budget> budgets = new HashMap<>();
        final Map<String, MonthlyBudgetDTO.Comparison> actual = new HashMap<>();
    }
    private interface Read<T> { T get() throws IOException; }
    private static <T> T cached(Map<String,T> cache, String id, Read<T> read) throws IOException {
        if(!cache.containsKey(id)) cache.put(id, read.get());
        return cache.get(id);
    }
    Forecast forecastAllocated(String id, Request request, List<BigDecimal> allocations, Reads reads) throws IOException {
        if(request==null || request.mode()==null) throw new IllegalArgumentException("须显式选择预测模式");
        var start=month(request.startMonth()); var end=month(request.endMonth());
        long count=ChronoUnit.MONTHS.between(start,end)+1;
        if(count<1 || count>60) throw new IllegalArgumentException("预测范围须为1至60个月");
        money(request.monthlyExtraSavings());
        var rate=request.annualRate();
        if(rate!=null && (rate.compareTo(BigDecimal.ONE.negate())<0 || rate.compareTo(BigDecimal.ONE)>0 || rate.scale()>8))
            throw new IllegalArgumentException("年化假设须为-1至1的比例，最多8位小数");
        if(request.months()==null || request.months().size()!=count) throw new IllegalArgumentException("须逐月显式提供预算选择和覆盖声明");
        var goal=cached(reads.goals,id,()->goals.detail(id)); var progress=cached(reads.progress,id,()->goals.progress(id));
        var asOf=YearMonth.from(progress.asOfDate());
        if(start.isBefore(asOf) || (start.equals(asOf) && request.mode()==Mode.PLANNED))
            throw new IllegalArgumentException("计划模式从统计月之后开始；当月须使用已发生加未发生计划模式");
        var selected=new ArrayList<MonthlyBudgetDTO.Budget>();
        var readings=new ArrayList<MonthlyBudgetDTO.Comparison>();
        for(int i=0;i<count;i++) {
            var input=request.months().get(i);
            if(input==null || !start.plusMonths(i).equals(month(input.month()))) throw new IllegalArgumentException("月份须连续且按范围排序");
            var budget=input.budgetId()==null?null:cached(reads.budgets,input.budgetId(),()->budgets.detail(input.budgetId()));
            if(budget!=null && (!budget.config().month().equals(input.month()) || !budget.config().scope().equals(goal.config().scope())))
                throw new IllegalArgumentException("预算月份或作用域与目标不一致");
            selected.add(budget);
            readings.add(budget!=null && request.mode()==Mode.ACTUAL_PLUS_REMAINING?cached(reads.actual,budget.id(),()->budgets.comparison(budget.id())):null);
        }
        return new Forecast(id,goal.config().currency(),progress.asOfDate(),progress,request,
                simulate(goal,progress,request,selected,readings,BigDecimal.ZERO,allocations),
                rate==null?null:simulate(goal,progress,request,selected,readings,rate,allocations));
    }
    private static BigDecimal amount(BigDecimal n) { return n.setScale(2,RoundingMode.DOWN); }
    private static Scenario simulate(Goal goal, Progress progress, Request request,
                                     List<MonthlyBudgetDTO.Budget> selected,
                                     List<MonthlyBudgetDTO.Comparison> readings, BigDecimal rate, List<BigDecimal> allocations) {
        BigDecimal cumulative=progress.quality()==Quality.OK?progress.currentValue():null;
        Quality overall=progress.quality();
        boolean uncoveredGap=month(request.startMonth()).isAfter(YearMonth.from(progress.asOfDate()).plusMonths(1));
        if(uncoveredGap) { cumulative=null; overall=Quality.UNKNOWN; }
        String achieved=Boolean.TRUE.equals(progress.completed())?YearMonth.from(progress.asOfDate()).toString():null;
        var rows=new ArrayList<Month>();
        for(int i=0;i<selected.size();i++) {
            var input=request.months().get(i); var budget=selected.get(i); var actual=readings.get(i);
            BigDecimal income=null,expense=null,reserve=null,modeledIncome=null,modeledExpense=null,upper=null,contribution=null,growth=null;
            Quality quality=Quality.OK; String reason="显式预算与覆盖声明下的数学上限";
            if(budget==null) { quality=Quality.UNKNOWN; reason="现金流未覆盖：未选择预算"; }
            else {
                income=BigDecimal.ZERO; expense=BigDecimal.ZERO; reserve=BigDecimal.ZERO;
                for(var item:budget.config().items()) switch(item.kind()) {
                    case INCOME -> income=income.add(item.planned());
                    case RESERVE -> reserve=reserve.add(item.planned());
                    default -> expense=expense.add(item.planned());
                }
                if(!budget.config().currency().equals(goal.config().currency())) { quality=Quality.UNKNOWN; reason="币种不匹配，未进行汇率换算"; }
                else if(!input.cashflowCovered()) { quality=Quality.PARTIAL; reason="预算覆盖未确认，不能推断自由现金"; }
                else if(request.mode()==Mode.ACTUAL_PLUS_REMAINING && actual==null) {
                    quality=Quality.UNKNOWN; reason="实际流水读数缺失";
                } else if(actual!=null && actual.quality()!=MonthlyBudgetDTO.Quality.OK) {
                    quality=Quality.valueOf(actual.quality().name()); reason="实际流水不完整";
                } else {
                    modeledIncome=income; modeledExpense=expense;
                    if(actual!=null) {
                        modeledIncome=actual.actualIncome(); modeledExpense=actual.actualExpenses();
                        for(var item:actual.items()) {
                            if(item.item().kind()==MonthlyBudgetDTO.Kind.RESERVE) continue;
                            var remaining=item.remaining().max(BigDecimal.ZERO);
                            if(item.item().kind()==MonthlyBudgetDTO.Kind.INCOME) modeledIncome=modeledIncome.add(remaining);
                            else modeledExpense=modeledExpense.add(remaining);
                        }
                    }
                    var available=modeledIncome.subtract(modeledExpense).subtract(reserve);
                    // Current progress already includes posted cash flows. Only the remaining cash flow may be added.
                    if(actual!=null && input.month().equals(YearMonth.from(progress.asOfDate()).toString()))
                        available=available.subtract(actual.actualSurplus());
                    upper=amount(available.add(request.monthlyExtraSavings()==null?BigDecimal.ZERO:request.monthlyExtraSavings()).max(BigDecimal.ZERO));
                }
            }
            if(goal.config().measure()==Measure.POSITION_VALUE) { quality=Quality.UNKNOWN; reason="持仓目标未指定投入账户，不假设自动买入"; }
            if(allocations!=null && allocations.get(i)==null && quality==Quality.OK) { quality=Quality.UNKNOWN; reason="未填写额度或共享预算不可行/未知；详见情景月度校验"; }
            BigDecimal starting=cumulative;
            if(cumulative!=null && quality==Quality.OK) {
                growth=amount(cumulative.multiply(rate).divide(BigDecimal.valueOf(12),16,RoundingMode.HALF_UP));
                contribution=amount(goal.config().targetValue().subtract(cumulative.add(growth)).max(BigDecimal.ZERO).min(allocations==null?upper:allocations.get(i).min(upper)));
                cumulative=cumulative.add(growth).add(contribution);
                if(achieved==null && cumulative.compareTo(goal.config().targetValue())>=0) achieved=input.month();
            } else {
                cumulative=null;
                if(quality==Quality.OK) { quality=overall==Quality.OK?Quality.UNKNOWN:overall; reason=uncoveredGap?"统计月与预测起始月之间现金流未覆盖":"起始进度或前序月份未知"; }
            }
            if(quality!=Quality.OK) overall=overall==Quality.PARTIAL || quality==Quality.PARTIAL?Quality.PARTIAL:Quality.UNKNOWN;
            rows.add(new Month(input.month(),input.budgetId(),quality,reason,starting,income,expense,reserve,actual,
                    modeledIncome,modeledExpense,upper,contribution,growth,cumulative,
                    cumulative==null?null:goal.config().targetValue().subtract(cumulative).max(BigDecimal.ZERO)));
        }
        return new Scenario(rate,"纯数学假设情景；年化比例/12按月简单计息，月末投入；当月亦按整月计息；金额2位小数向零舍入；计划模式起始值为统计日资产，统计月剩余变化未建模；仅模拟新增投入，不模拟提款；额外储蓄为显式外部自由现金，不含预留或不可动用资金；无交易、税费或市场预测",
                overall,achieved!=null?"ACHIEVED":overall==Quality.OK?"UNREACHABLE_WITHIN_RANGE":"UNKNOWN",achieved,List.copyOf(rows));
    }
}
