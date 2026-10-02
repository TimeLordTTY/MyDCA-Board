package com.timelordtty.dca.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelordtty.dca.dto.GoalTrackingDTO.*;
import com.timelordtty.dca.dto.FinanceRadarDTO;
import com.timelordtty.dca.mapper.GoalTrackingMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Writes only goal metadata. Progress never persists a snapshot or financial changes. */
@Service
public class GoalTrackingService {
    private final GoalTrackingMapper store;
    private final UserService users;
    private final FamilyService families;
    private final FinanceRadarService radar;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    public GoalTrackingService(GoalTrackingMapper store, UserService users, FamilyService families, FinanceRadarService radar) {
        this.store=store; this.users=users; this.families=families; this.radar=radar;
    }
    private void authorize(Config c) {
        var u=users.getCurrentUser();
        if ("FAMILY".equals(c.scope())) {
            if(u.getFamilyId()==null) throw new IllegalArgumentException("当前用户没有家庭作用域");
            families.assertAdmin(u.getId(),u.getFamilyId());
        } else if(!"PERSONAL".equals(c.scope())) throw new IllegalArgumentException("目标作用域无效");
    }
    private void validate(Config c) {
        if(c==null) throw new IllegalArgumentException("目标配置不能为空");
        authorize(c);
        if(c.name()==null || c.name().isBlank() || c.name().length()>200)
            throw new IllegalArgumentException("目标名称须为1至200字符");
        if(c.targetValue()==null || c.targetValue().signum()<=0 || c.targetValue().scale()>2
                || c.targetValue().precision()-c.targetValue().scale()>18)
            throw new IllegalArgumentException("目标值须为正数，最多18位整数和2位小数");
        if(c.targetDate()==null || c.targetDate().getYear()<1000 || c.targetDate().getYear()>9999)
            throw new IllegalArgumentException("目标日期无效");
        try { Currency.getInstance(c.currency()); }
        catch(Exception e) { throw new IllegalArgumentException("目标币种须为有效ISO币种"); }
        if(c.measure()==null || c.state()==null) throw new IllegalArgumentException("须明确统计范围和状态");
        if(c.note()!=null && c.note().length()>2000) throw new IllegalArgumentException("备注过长");
    }
    public Goal create(Config c) throws IOException {
        validate(c); var u=users.getCurrentUser();
        var g=new Goal(UUID.randomUUID().toString(),c,Instant.now());
        if(store.insert(g.id(),u.getId(),u.getFamilyId(),json.writeValueAsString(g))!=1)
            throw new IllegalStateException("目标保存失败");
        return g;
    }
    public Goal detail(String id) throws IOException {
        var u=users.getCurrentUser(); var payload=store.detail(id,u.getId(),u.getFamilyId());
        if(payload==null) throw new IllegalArgumentException("目标不存在或无权访问");
        var g=json.readValue(payload,Goal.class); authorize(g.config()); return g;
    }
    public Goal edit(String id, Config c) throws IOException {
        var previous=detail(id); validate(c); var u=users.getCurrentUser();
        var g=new Goal(id,c,previous.createdAt());
        if(store.update(id,u.getId(),u.getFamilyId(),json.writeValueAsString(g))!=1)
            throw new IllegalStateException("目标更新失败");
        return g;
    }
    public List<Goal> list(int page,int size) throws IOException {
        if(page<0 || size<1 || size>50 || (long)page*size>100000) throw new IllegalArgumentException("分页参数超出范围");
        var u=users.getCurrentUser(); var result=new ArrayList<Goal>();
        for(var payload:store.list(u.getId(),u.getFamilyId(),page*size,size)) {
            var g=json.readValue(payload,Goal.class); authorize(g.config()); result.add(g);
        }
        return result;
    }
    @Transactional(readOnly=true)
    public Progress progress(String id) throws IOException {
        var g=detail(id); var u=users.getCurrentUser();
        return observe(g,radar.getRadar(u.getId(),u.getFamilyId(),g.config().scope()));
    }
    static Progress observe(Goal g, FinanceRadarDTO facts) {
        var c=g.config(); LocalDate today=facts!=null && facts.date()!=null?facts.date():LocalDate.now();
        BigDecimal value=null, known=null;
        Quality quality=Quality.UNKNOWN;
        String reason="数据不足，进度未知";
        // Existing radar amounts are CNY only. Never relabel or implicitly convert them.
        if(!"CNY".equals(c.currency())) reason="现有资产汇总仅支持人民币，未进行汇率换算";
        else if(facts!=null && facts.date()!=null && Objects.equals(c.scope(),facts.scope()) && facts.assets()!=null) {
            var a=facts.assets();
            switch(c.measure()) {
                case CASH -> value=a.cashBalance();
                case POSITION_VALUE -> value=a.positionValue();
                case TOTAL_ASSETS -> {
                    if("OK".equals(a.status()) && a.cashBalance()!=null && a.positionValue()!=null
                            && a.totalAssets()!=null && a.cashBalance().add(a.positionValue()).compareTo(a.totalAssets())==0)
                        value=a.totalAssets();
                    else if((a.cashBalance()==null)!=(a.positionValue()==null)) {
                        known=(a.cashBalance()==null?BigDecimal.ZERO:a.cashBalance())
                                .add(a.positionValue()==null?BigDecimal.ZERO:a.positionValue());
                        quality=Quality.PARTIAL; reason="仅部分资产金额已知，无法确定完成率";
                    }
                }
            }
            if(value!=null) { known=value; quality=Quality.OK; reason="只读资产进度"; }
        }
        BigDecimal rate=value==null?null:value.divide(c.targetValue(),12,RoundingMode.HALF_UP);
        return new Progress(g.id(),quality,reason,value,known,rate,
                value==null?null:value.compareTo(c.targetValue())>=0,today,
                ChronoUnit.DAYS.between(today,c.targetDate()),today.isAfter(c.targetDate()));
    }
}
