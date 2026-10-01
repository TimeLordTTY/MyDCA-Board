package com.timelordtty.dca.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelordtty.dca.dto.RiskWatchDTO.*;
import com.timelordtty.dca.dto.FinanceRadarDTO;
import com.timelordtty.dca.mapper.RiskWatchMapper;
import com.timelordtty.dca.model.IndicatorDaily;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Reads financial facts; writes only explicit observation rules and snapshots. */
@Service
public class RiskWatchService {
    public static final String DISCLAIMER = "仅供观察，不构成交易建议";
    private final RiskWatchMapper store;
    private final UserService users;
    private final FamilyService families;
    private final FinanceRadarService radar;
    private final HoldingService holdings;
    private final IndicatorService indicators;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    public RiskWatchService(RiskWatchMapper store, UserService users, FamilyService families,
                            FinanceRadarService radar, HoldingService holdings, IndicatorService indicators) {
        this.store=store; this.users=users; this.families=families;
        this.radar=radar; this.holdings=holdings; this.indicators=indicators;
    }
    private void authorize(Config c) {
        var user=users.getCurrentUser();
        if ("FAMILY".equals(c.scope())) {
            if (user.getFamilyId()==null) throw new IllegalArgumentException("当前用户没有家庭作用域");
            families.assertAdmin(user.getId(),user.getFamilyId());
        } else if (!"PERSONAL".equals(c.scope())) throw new IllegalArgumentException("观察作用域无效");
    }
    private void validate(Config c) {
        if(c==null || c.type()==null || c.severity()==null) throw new IllegalArgumentException("观察规则字段不完整");
        authorize(c);
        if(c.note()!=null && c.note().length()>2000) throw new IllegalArgumentException("观察备注过长");
        if(c.type()!=Type.NOTE && c.threshold()==null) throw new IllegalArgumentException("必须配置阈值");
        if(Set.of(Type.RETURN,Type.DRAWDOWN,Type.STALE,Type.CONCENTRATION).contains(c.type())
                && (c.productId()==null || c.productId()<=0)) throw new IllegalArgumentException("必须选择标的");
        if(c.type()==Type.RETURN && c.direction()==null) throw new IllegalArgumentException("必须选择阈值方向");
        if(c.type()!=Type.RETURN && c.threshold()!=null && c.threshold().signum()<0) throw new IllegalArgumentException("阈值不得为负数");
        if(Set.of(Type.DRAWDOWN,Type.CONCENTRATION,Type.ALLOCATION_DEVIATION).contains(c.type())
                && c.threshold().compareTo(BigDecimal.ONE)>0) throw new IllegalArgumentException("比例阈值不得超过1");
        if(c.type()==Type.STALE && (c.threshold().scale()>0 || c.threshold().compareTo(BigDecimal.valueOf(3650))>0))
            throw new IllegalArgumentException("陈旧阈值须为0至3650整数天");
        if(c.type()==Type.ALLOCATION_DEVIATION && (c.assetType()==null || c.assetType().isBlank() || c.assetType().length()>60
                || c.target()==null || c.target().signum()<0 || c.target().compareTo(BigDecimal.ONE)>0))
            throw new IllegalArgumentException("必须配置资产类别和0至1目标占比");
    }
    public Rule create(Config config) throws IOException {
        validate(config); var u=users.getCurrentUser();
        var rule=new Rule(UUID.randomUUID().toString(),config,Instant.now(),DISCLAIMER);
        if(store.insertRule(rule.id(),u.getId(),u.getFamilyId(),json.writeValueAsString(rule))!=1)
            throw new IllegalStateException("观察规则保存失败");
        return rule;
    }
    public Rule detail(String id) throws IOException {
        var u=users.getCurrentUser(); String payload=store.rule(id,u.getId(),u.getFamilyId());
        if(payload==null) throw new IllegalArgumentException("观察规则不存在或无权访问");
        var rule=json.readValue(payload,Rule.class); authorize(rule.config()); return rule;
    }
    public Rule edit(String id,Config config) throws IOException {
        var previous=detail(id); validate(config); var u=users.getCurrentUser();
        var rule=new Rule(id,config,previous.createdAt(),DISCLAIMER);
        if(store.updateRule(id,u.getId(),u.getFamilyId(),json.writeValueAsString(rule))!=1)
            throw new IllegalStateException("观察规则更新失败");
        return rule;
    }
    private int offset(int page,int size) {
        if(page<0 || size<1 || size>50 || (long)page*size>100000) throw new IllegalArgumentException("分页参数超出范围");
        return page*size;
    }
    public List<Rule> list(int page,int size) throws IOException {
        var u=users.getCurrentUser(); var result=new ArrayList<Rule>();
        for(String payload:store.rules(u.getId(),u.getFamilyId(),offset(page,size),size)) {
            var rule=json.readValue(payload,Rule.class); authorize(rule.config()); result.add(rule);
        }
        return result;
    }
    public List<Snapshot> history(String id,int page,int size) throws IOException {
        detail(id); var u=users.getCurrentUser(); var result=new ArrayList<Snapshot>();
        for(String payload:store.history(id,u.getId(),u.getFamilyId(),offset(page,size),size)) result.add(json.readValue(payload,Snapshot.class));
        return result;
    }
    public Snapshot evaluate(String id) throws IOException {
        Rule rule=detail(id); Config c=rule.config(); var u=users.getCurrentUser();
        boolean family="FAMILY".equals(c.scope());
        var facts=radar.getRadar(u.getId(),u.getFamilyId(),c.scope());
        var positions=new ArrayList<>(holdings.calculateHoldings(family?null:u.getId(),family?u.getFamilyId():null));
        // Sorting serialized rows makes input hashing independent of mapper row order.
        positions.sort(Comparator.comparing(h -> json.valueToTree(h).toString()));
        IndicatorDaily indicator=c.type()==Type.DRAWDOWN ? indicators.getLatestIndicator(c.productId(),20):null;
        var input=json.createObjectNode(); input.put("version",1); input.set("rule",json.valueToTree(rule));
        input.put("user",u.getId()); input.put("family",u.getFamilyId());
        input.set("assets",json.valueToTree(facts.assets())); input.put("date",facts.date().toString());
        input.set("markets",json.valueToTree(facts.markets().stream().sorted(Comparator.comparing(FinanceRadarDTO.MarketFact::productId)).toList()));
        input.set("holdings",json.valueToTree(positions)); input.set("indicator",json.valueToTree(indicator));
        String hash=ResearchPlanService.digest(json.writeValueAsBytes(input));
        String snapshotId=ResearchPlanService.digest((id+":"+hash).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String existing=store.snapshot(snapshotId,u.getId(),u.getFamilyId());
        if(existing!=null) return json.readValue(existing,Snapshot.class);
        BigDecimal observed=observe(c,facts,positions,indicator);
        boolean muted=c.muted(); boolean known=c.type()==Type.NOTE || observed!=null;
        boolean matched=!muted && known && (c.type()==Type.NOTE || (c.type()==Type.RETURN && c.direction()==Direction.BELOW
                ? observed.compareTo(c.threshold())<=0 : observed.compareTo(c.threshold())>=0));
        String status=muted?"MUTED":known?"OK":"UNKNOWN";
        String reason=muted?"观察规则已静默":!known?"行情、净值、成本或组合数据不足，观察值未知":matched?"观察条件已达到配置阈值":"观察条件未达到配置阈值";
        if(c.type()==Type.NOTE && !muted) reason="自定义观察备注："+Objects.toString(c.note(),"");
        var snapshot=new Snapshot(snapshotId,id,facts.date().toString(),hash,status,matched,
                matched?c.severity():Severity.INFO,reason,observed,c.threshold(),Instant.now(),DISCLAIMER);
        try {
            if(store.insertSnapshot(snapshotId,id,u.getId(),u.getFamilyId(),json.writeValueAsString(snapshot))!=1)
                throw new IllegalStateException("观察快照保存失败");
        } catch(DuplicateKeyException conflict) {
            existing=store.snapshot(snapshotId,u.getId(),u.getFamilyId());
            if(existing==null) throw conflict;
            return json.readValue(existing,Snapshot.class);
        }
        return snapshot;
    }
    static BigDecimal observe(Config c,FinanceRadarDTO facts,List<HoldingService.HoldingInfo> positions,IndicatorDaily indicator) {
        if(c.type()==Type.NOTE) return null;
        var market=facts.markets().stream().filter(m->Objects.equals(m.productId(),c.productId())).findFirst().orElse(null);
        if(c.type()==Type.STALE) {
            if(market==null || market.priceDate()==null || market.valuationDate()==null
                    || market.priceDate().isAfter(facts.date()) || market.valuationDate().isAfter(facts.date())) return null;
            return BigDecimal.valueOf(Math.max(ChronoUnit.DAYS.between(market.priceDate(),facts.date()),
                    ChronoUnit.DAYS.between(market.valuationDate(),facts.date())));
        }
        if(c.type()==Type.DRAWDOWN) return market!=null && "OK".equals(market.status()) && indicator!=null
                && indicator.getTradeDate()!=null && !indicator.getTradeDate().isAfter(facts.date())
                && !indicator.getTradeDate().isBefore(facts.date().minusDays(3)) && indicator.getDrawdownFromPeak()!=null
                ? indicator.getDrawdownFromPeak().abs():null;
        BigDecimal selected=BigDecimal.ZERO, cost=BigDecimal.ZERO, positionTotal=BigDecimal.ZERO; boolean found=false;
        for(var p:positions) {
            if(p.getTotalShares()==null) return null;
            if(p.getTotalShares().signum()<=0) continue;
            if(p.getMarketValue()==null || p.getMarketValue().signum()<0) return null;
            positionTotal=positionTotal.add(p.getMarketValue());
            boolean selectedRow=c.type()==Type.ALLOCATION_DEVIATION?Objects.equals(c.assetType(),p.getAssetType()):Objects.equals(c.productId(),p.getProductId());
            if(c.type()==Type.ALLOCATION_DEVIATION && p.getAssetType()==null) return null;
            if(selectedRow) {
                found=true; if(p.getMarketValue()==null || p.getMarketValue().signum()<0) return null;
                selected=selected.add(p.getMarketValue());
                if(c.type()==Type.RETURN) { if(p.getTotalCost()==null) return null; cost=cost.add(p.getTotalCost()); }
            }
        }
        if(c.type()==Type.RETURN) return !found || cost.signum()<=0 || market==null || !"OK".equals(market.status())?null:
                selected.subtract(cost).divide(cost,12,RoundingMode.HALF_UP);
        // A partial portfolio cannot establish concentration or allocation percentages.
        BigDecimal total=facts.assets().totalAssets();
        if(!"OK".equals(facts.assets().status()) || total==null || total.signum()<=0
                || facts.assets().positionValue()==null || positionTotal.compareTo(facts.assets().positionValue())!=0) return null;
        if(c.type()==Type.ALLOCATION_DEVIATION && "CASH".equals(c.assetType())) {
            if(facts.assets().cashBalance()==null || facts.assets().cashBalance().signum()<0) return null;
            selected=facts.assets().cashBalance();
        }
        if(c.type()==Type.CONCENTRATION && !found) return null;
        BigDecimal ratio=selected.divide(total,12,RoundingMode.HALF_UP);
        return c.type()==Type.ALLOCATION_DEVIATION?ratio.subtract(c.target()).abs():ratio;
    }
}
