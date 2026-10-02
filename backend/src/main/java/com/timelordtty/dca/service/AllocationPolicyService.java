package com.timelordtty.dca.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelordtty.dca.dto.AllocationPolicyDTO.*;
import com.timelordtty.dca.dto.FinanceRadarDTO;
import com.timelordtty.dca.mapper.AllocationPolicyMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
public class AllocationPolicyService {
    private final AllocationPolicyMapper store;
    private final UserService users;
    private final FamilyService families;
    private final FinanceRadarService radar;
    private final HoldingService holdings;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    public AllocationPolicyService(AllocationPolicyMapper store, UserService users, FamilyService families,
                                   FinanceRadarService radar, HoldingService holdings) {
        this.store=store; this.users=users; this.families=families; this.radar=radar; this.holdings=holdings;
    }
    private void authorize(Config c) {
        var u=users.getCurrentUser();
        if ("FAMILY".equals(c.scope())) {
            if (u.getFamilyId()==null) throw new IllegalArgumentException("当前用户没有家庭作用域");
            families.assertAdmin(u.getId(),u.getFamilyId());
        } else if (!"PERSONAL".equals(c.scope())) throw new IllegalArgumentException("配置作用域无效");
    }
    private static boolean ratio(BigDecimal n) {
        return n!=null && n.signum()>=0 && n.compareTo(BigDecimal.ONE)<=0 && n.scale()<=12;
    }
    private static boolean threshold(BigDecimal n) {
        return n!=null && n.signum()>=0 && n.precision()<=24 && n.scale()<=12;
    }
    private void validate(Config c) {
        if(c==null) throw new IllegalArgumentException("必须显式配置策略");
        authorize(c);
        if((c.productId()==null)==(c.assetType()==null) || (c.productId()!=null && c.productId()<=0)
                || (c.assetType()!=null && (c.assetType().isBlank() || c.assetType().length()>60)))
            throw new IllegalArgumentException("必须选择一个产品或资产类别");
        if(!ratio(c.target()) || !ratio(c.lowerBound()) || !ratio(c.upperBound())
                || c.lowerBound().compareTo(c.target())>0 || c.target().compareTo(c.upperBound())>0)
            throw new IllegalArgumentException("须显式配置0至1的下限、目标和上限，且下限≤目标≤上限");
        if(c.enabled()==null) throw new IllegalArgumentException("必须显式设置启用状态");
        if(c.note()!=null && c.note().length()>2000) throw new IllegalArgumentException("备注过长");
        if(c.returnThreshold()!=null && !threshold(c.returnThreshold())) throw new IllegalArgumentException("收益观察阈值无效");
        if(c.takeProfitThresholds()!=null) {
            if(c.takeProfitThresholds().size()>20) throw new IllegalArgumentException("分段阈值最多20个");
            BigDecimal previous=null;
            for(var n:c.takeProfitThresholds()) {
                if(!threshold(n) || (previous!=null && n.compareTo(previous)<=0))
                    throw new IllegalArgumentException("分段止盈阈值须非负且严格递增");
                previous=n;
            }
        }
        if("CASH".equals(c.assetType()) && hasReturnWatch(c)) throw new IllegalArgumentException("现金类别不支持收益观察阈值");
    }
    public Policy create(Config c) throws IOException {
        validate(c); var u=users.getCurrentUser();
        var p=new Policy(UUID.randomUUID().toString(),c,Instant.now(),RiskWatchService.DISCLAIMER);
        if(store.insert(p.id(),u.getId(),u.getFamilyId(),json.writeValueAsString(p))!=1) throw new IllegalStateException("配置保存失败");
        return p;
    }
    public Policy detail(String id) throws IOException {
        var u=users.getCurrentUser(); var payload=store.detail(id,u.getId(),u.getFamilyId());
        if(payload==null) throw new IllegalArgumentException("配置不存在或无权访问");
        var p=json.readValue(payload,Policy.class); authorize(p.config()); return p;
    }
    public Policy edit(String id, Config c) throws IOException {
        var previous=detail(id); validate(c); var u=users.getCurrentUser();
        var p=new Policy(id,c,previous.createdAt(),RiskWatchService.DISCLAIMER);
        if(store.update(id,u.getId(),u.getFamilyId(),json.writeValueAsString(p))!=1) throw new IllegalStateException("配置更新失败");
        return p;
    }
    public List<Policy> list(int page,int size) throws IOException {
        if(page<0 || size<1 || size>50 || (long)page*size>100000) throw new IllegalArgumentException("分页参数超出范围");
        var u=users.getCurrentUser(); var result=new ArrayList<Policy>();
        for(var payload:store.list(u.getId(),u.getFamilyId(),page*size,size)) {
            var p=json.readValue(payload,Policy.class); authorize(p.config()); result.add(p);
        }
        return result;
    }
    /** No observation history or financial mutations; evaluation is strictly read-only. */
    @Transactional(readOnly=true)
    public Evaluation evaluate(String id) throws IOException {
        var p=detail(id); var c=p.config();
        if(!Boolean.TRUE.equals(c.enabled())) return result(p,Status.UNKNOWN,"策略已停用，未执行观察",null,null,List.of());
        var u=users.getCurrentUser(); boolean family="FAMILY".equals(c.scope());
        return observe(p,radar.getRadar(u.getId(),u.getFamilyId(),c.scope()),
                holdings.calculateHoldings(family?null:u.getId(),family?u.getFamilyId():null));
    }
    private static boolean hasReturnWatch(Config c) {
        return c.returnThreshold()!=null || (c.takeProfitThresholds()!=null && !c.takeProfitThresholds().isEmpty());
    }
    private static Evaluation result(Policy p,Status s,String reason,BigDecimal allocation,BigDecimal rate,List<BigDecimal> reached) {
        return new Evaluation(p.id(),s,reason,allocation,rate,List.copyOf(reached),Instant.now(),RiskWatchService.DISCLAIMER);
    }
    static Evaluation observe(Policy p,FinanceRadarDTO facts,List<HoldingService.HoldingInfo> positions) {
        var c=p.config();
        if(!Boolean.TRUE.equals(c.enabled())) return result(p,Status.UNKNOWN,"策略已停用，未执行观察",null,null,List.of());
        if(facts==null || facts.assets()==null || positions==null || facts.markets()==null || facts.date()==null
                || !"OK".equals(facts.assets().status()) || facts.assets().totalAssets()==null
                || facts.assets().totalAssets().signum()<=0 || facts.assets().positionValue()==null)
            return result(p,Status.UNKNOWN,"组合数据不足，配置占比未知",null,null,List.of());
        BigDecimal selected=BigDecimal.ZERO, total=BigDecimal.ZERO, cost=BigDecimal.ZERO;
        boolean costKnown=true, found=false;
        for(var h:positions) {
            if(h==null || h.getTotalShares()==null || h.getTotalShares().signum()<0)
                return result(p,Status.UNKNOWN,"持仓份额未知",null,null,List.of());
            if(h.getTotalShares().signum()==0) continue;
            var market=facts.markets().stream().filter(m->Objects.equals(m.productId(),h.getProductId())).findFirst().orElse(null);
            if(h.getProductId()==null || h.getMarketValue()==null || h.getMarketValue().signum()<0
                    || h.getAssetType()==null || h.getAssetType().isBlank() || market==null || !"OK".equals(market.status())
                    || market.priceDate()==null || market.valuationDate()==null
                    || market.priceDate().isAfter(facts.date()) || market.valuationDate().isAfter(facts.date()))
                return result(p,Status.UNKNOWN,"行情或资产分类缺失，配置占比未知",null,null,List.of());
            total=total.add(h.getMarketValue());
            if(c.productId()!=null ? c.productId().equals(h.getProductId()) : c.assetType().equals(h.getAssetType())) {
                found=true; selected=selected.add(h.getMarketValue());
                if(h.getTotalCost()==null || h.getTotalCost().signum()<=0) costKnown=false;
                else cost=cost.add(h.getTotalCost());
            }
        }
        if(total.compareTo(facts.assets().positionValue())!=0)
            return result(p,Status.UNKNOWN,"持仓与组合汇总不一致，配置占比未知",null,null,List.of());
        if("CASH".equals(c.assetType())) {
            if(facts.assets().cashBalance()==null || facts.assets().cashBalance().signum()<0)
                return result(p,Status.UNKNOWN,"现金余额未知",null,null,List.of());
            selected=facts.assets().cashBalance();
        }
        BigDecimal denominator=facts.assets().totalAssets();
        if(selected.compareTo(denominator)>0) return result(p,Status.UNKNOWN,"资产汇总不一致",null,null,List.of());
        BigDecimal allocation=selected.divide(denominator,12,RoundingMode.HALF_UP);
        BigDecimal rate=found && costKnown && cost.signum()>0 ? selected.subtract(cost).divide(cost,12,RoundingMode.HALF_UP):null;
        if((found && !costKnown) || (hasReturnWatch(c) && rate==null))
            return result(p,Status.UNKNOWN,"缺失持仓或成本，收益观察值未知",allocation,null,List.of());
        var reached=c.takeProfitThresholds()==null || rate==null ? List.<BigDecimal>of():
                c.takeProfitThresholds().stream().filter(t->rate.compareTo(t)>=0).toList();
        if(!reached.isEmpty() || (c.returnThreshold()!=null && rate.compareTo(c.returnThreshold())>=0))
            return result(p,Status.TAKE_PROFIT_WATCH,"收益已达到用户配置的观察阈值，仅供观察",allocation,rate,reached);
        // Compare unrounded amounts so rounding cannot hide a deviation at a boundary.
        Status status=selected.compareTo(denominator.multiply(c.lowerBound()))<0 ? Status.BELOW_BAND:
                selected.compareTo(denominator.multiply(c.upperBound()))>0 ? Status.ABOVE_BAND:Status.IN_RANGE;
        String reason=switch(status) {
            case BELOW_BAND -> "当前占比低于用户配置下限";
            case ABOVE_BAND -> "当前占比高于用户配置上限";
            default -> "当前占比处于用户配置区间内（含边界）";
        };
        return result(p,status,reason,allocation,rate,reached);
    }
}
