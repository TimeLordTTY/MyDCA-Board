package com.timelordtty.dca.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelordtty.dca.dto.MonthlyBudgetDTO.*;
import com.timelordtty.dca.mapper.MonthlyBudgetMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Writes only budget metadata. Comparison never persists financial changes. */
@Service
public class MonthlyBudgetService {
    private final MonthlyBudgetMapper store;
    private final UserService users;
    private final FamilyService families;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    public MonthlyBudgetService(MonthlyBudgetMapper store, UserService users, FamilyService families) {
        this.store=store; this.users=users; this.families=families;
    }
    private void authorize(Config c) {
        var u=users.getCurrentUser();
        if ("FAMILY".equals(c.scope())) {
            if(u.getFamilyId()==null) throw new IllegalArgumentException("当前用户没有家庭作用域");
            families.assertAdmin(u.getId(),u.getFamilyId());
        } else if(!"PERSONAL".equals(c.scope())) throw new IllegalArgumentException("预算作用域无效");
    }
    private void validate(Config c) {
        if(c==null) throw new IllegalArgumentException("预算配置不能为空");
        authorize(c);
        if(c.name()==null || c.name().isBlank() || c.name().length()>200)
            throw new IllegalArgumentException("预算名称须为1至200字符");
        try {
            var month=java.time.YearMonth.parse(c.month());
            if (!month.toString().equals(c.month()) || month.getYear()<1000 || month.getYear()>9998)
                throw new IllegalArgumentException();
        } catch(Exception e) { throw new IllegalArgumentException("月份须为1000至9998年的YYYY-MM"); }
        if(c.items()==null || c.items().isEmpty() || c.items().size()>100)
            throw new IllegalArgumentException("预算须包含1至100项");
        Set<String> categories=new HashSet<>();
        for(var item:c.items()) {
            if(item==null || item.name()==null || item.name().isBlank() || item.name().length()>200 || item.kind()==null)
                throw new IllegalArgumentException("预算项名称或类别无效");
            var n=item.planned();
            if(n==null || n.signum()<0 || n.scale()>2 || n.precision()-n.scale()>18)
                throw new IllegalArgumentException("计划金额须非负，最多18位整数和2位小数");
            if(item.kind()==Kind.RESERVE) {
                if(item.categoryId()!=null) throw new IllegalArgumentException("预留项不绑定流水分类");
            } else {
                if(item.categoryId()==null || item.categoryId()<=0)
                    throw new IllegalArgumentException("收入和支出须显式绑定既有分类ID");
                String key=(item.kind()==Kind.INCOME?"INCOME":"EXPENSE")+item.categoryId();
                if(!categories.add(key)) throw new IllegalArgumentException("同一流水分类不能重复预算");
            }
        }
        try { Currency.getInstance(c.currency()); }
        catch(Exception e) { throw new IllegalArgumentException("预算币种须为有效ISO币种"); }
    }
    public Budget create(Config c) throws IOException {
        validate(c); var u=users.getCurrentUser();
        var g=new Budget(UUID.randomUUID().toString(),c,Instant.now());
        if(store.insert(g.id(),u.getId(),u.getFamilyId(),json.writeValueAsString(g))!=1)
            throw new IllegalStateException("预算保存失败");
        return g;
    }
    public Budget detail(String id) throws IOException {
        var u=users.getCurrentUser(); var payload=store.detail(id,u.getId(),u.getFamilyId());
        if(payload==null) throw new IllegalArgumentException("预算不存在或无权访问");
        var g=json.readValue(payload,Budget.class); authorize(g.config()); return g;
    }
    public Budget edit(String id, Config c) throws IOException {
        var previous=detail(id); validate(c); var u=users.getCurrentUser();
        var g=new Budget(id,c,previous.createdAt());
        if(store.update(id,u.getId(),u.getFamilyId(),json.writeValueAsString(g))!=1)
            throw new IllegalStateException("预算更新失败");
        return g;
    }
    public List<Budget> list(int page,int size) throws IOException {
        if(page<0 || size<1 || size>50 || (long)page*size>100000) throw new IllegalArgumentException("分页参数超出范围");
        var u=users.getCurrentUser(); var result=new ArrayList<Budget>();
        for(var payload:store.list(u.getId(),u.getFamilyId(),page*size,size)) {
            var g=json.readValue(payload,Budget.class); authorize(g.config()); result.add(g);
        }
        return result;
    }
    @Transactional(readOnly=true)
    public Comparison comparison(String id) throws IOException {
        var budget=detail(id); var u=users.getCurrentUser();
        var month=java.time.YearMonth.parse(budget.config().month());
        // PERSONAL remains owner-only; FAMILY also requires admin and the same current family.
        Long family="FAMILY".equals(budget.config().scope())?u.getFamilyId():null;
        return observe(budget,store.facts(u.getId(),family,month.atDay(1),month.plusMonths(1).atDay(1)));
    }
    static Comparison observe(Budget budget, List<com.timelordtty.dca.dto.MonthlyBudgetFact> facts) {
        var c=budget.config(); var month=java.time.YearMonth.parse(c.month());
        BigDecimal income=BigDecimal.ZERO, expense=BigDecimal.ZERO, reserve=BigDecimal.ZERO;
        Map<String,BigDecimal> known=new HashMap<>(); Set<String> incomplete=new HashSet<>();
        Set<String> keys=new HashSet<>();
        for(var item:c.items()) {
            switch(item.kind()) {
                case INCOME -> income=income.add(item.planned());
                case RESERVE -> reserve=reserve.add(item.planned());
                default -> expense=expense.add(item.planned());
            }
            if(item.kind()!=Kind.RESERVE) keys.add(key(item));
        }
        boolean globalMissing=facts==null; int unmatched=0; int valid=0;
        if(facts!=null) for(var f:facts) {
            if(f==null || f.getTradeDate()==null || !java.time.YearMonth.from(f.getTradeDate()).equals(month)) {
                globalMissing=true; continue;
            }
            String k=f.getAccountType()+String.valueOf(f.getCategoryId());
            if(!keys.contains(k)) { unmatched++; globalMissing=true; continue; }
            if(f.getAmount()==null || f.getAmount().signum()<0 || !Objects.equals(c.currency(),f.getCurrency())
                    || !("DEBIT".equals(f.getPostingType()) || "CREDIT".equals(f.getPostingType()))) {
                incomplete.add(k); continue;
            }
            boolean positive="INCOME".equals(f.getAccountType())?"CREDIT".equals(f.getPostingType()):"DEBIT".equals(f.getPostingType());
            known.merge(k,positive?f.getAmount():f.getAmount().negate(),BigDecimal::add); valid++;
        }
        var items=new ArrayList<ItemComparison>();
        BigDecimal actualIncome=BigDecimal.ZERO, actualExpense=BigDecimal.ZERO;
        boolean complete=!globalMissing && incomplete.isEmpty();
        for(var item:c.items()) {
            if(item.kind()==Kind.RESERVE) {
                items.add(new ItemComparison(item,Quality.UNKNOWN,null,null,null,null)); continue;
            }
            String k=key(item); BigDecimal amount=known.getOrDefault(k,BigDecimal.ZERO);
            boolean missing=globalMissing || incomplete.contains(k);
            var q=missing?(known.containsKey(k)?Quality.PARTIAL:Quality.UNKNOWN):Quality.OK;
            BigDecimal actual=missing?null:amount;
            var remaining=actual==null?null:item.planned().subtract(actual);
            items.add(new ItemComparison(item,q,actual,known.containsKey(k)||!missing?amount:null,remaining,
                    item.kind()==Kind.INCOME || actual==null?null:remaining.signum()<0));
            if(item.kind()==Kind.INCOME) actualIncome=actualIncome.add(amount); else actualExpense=actualExpense.add(amount);
        }
        BigDecimal plannedSurplus=income.subtract(expense).subtract(reserve);
        BigDecimal remaining=complete?expense.subtract(actualExpense):null;
        var warnings=new ArrayList<String>();
        if(plannedSurplus.signum()<0) warnings.add("计划结余为负");
        if(!complete) warnings.add("流水缺失、分类未绑定或币种不匹配；未知金额不按零处理");
        if(items.stream().anyMatch(i->Boolean.TRUE.equals(i.overspent()))) warnings.add("预算项已超支");
        return new Comparison(budget.id(),complete?Quality.OK:valid>0?Quality.PARTIAL:Quality.UNKNOWN,
                income,expense,reserve,plannedSurplus,complete?actualIncome:null,complete?actualExpense:null,
                complete?actualIncome.subtract(actualExpense):null,remaining,remaining==null?null:remaining.signum()<0,
                unmatched,List.copyOf(items),List.copyOf(warnings));
    }
    private static String key(Item item) {
        return (item.kind()==Kind.INCOME?"INCOME":"EXPENSE")+item.categoryId();
    }
}
