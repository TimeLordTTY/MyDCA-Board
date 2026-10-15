package com.timelordtty.dca.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelordtty.dca.controller.*;
import com.timelordtty.dca.dto.*;
import com.timelordtty.dca.exception.GlobalExceptionHandler;
import com.timelordtty.dca.model.*;
import com.timelordtty.dca.model.Order;
import com.timelordtty.dca.security.JwtTokenProvider;
import com.timelordtty.dca.service.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 本机专属MySQL的真实业务测试：无Mockito、H2或生产应用配置。 */
@EnabledIfSystemProperty(named="isolated.mysql", matches="true")
@SpringBootTest(classes=IsolatedMysqlBusinessTest.TestApplication.class, properties={
        "spring.config.name=b-synthetic-test", "spring.profiles.active=b-synthetic-test",
        "jwt.secret=synthetic-only-20261014-key-with-at-least-256-bits", "jwt.expiration=60000",
        "logging.level.root=WARN", "mybatis.mapper-locations=classpath:mapper/*.xml",
        "mybatis.configuration.map-underscore-to-camel-case=true",
        "spring.datasource.hikari.maximum-pool-size=6", "spring.datasource.hikari.minimum-idle=0"})
@AutoConfigureMockMvc
class IsolatedMysqlBusinessTest {
    static final String KEY="synthetic-only-20261014-key-with-at-least-256-bits";
    static final String DB="mydca_fixture_20261014";
    static final String SYNTHETIC_PASSWORD="fictional-password-only";
    @Configuration
    @EnableAutoConfiguration(exclude=UserDetailsServiceAutoConfiguration.class)
    @MapperScan("com.timelordtty.dca.mapper")
    @ComponentScan(basePackages={"com.timelordtty.dca.service","com.timelordtty.dca.security"})
    @Import({SecurityConfig.class,AuthController.class,UserController.class,FamilyController.class,
            DraftLedgerEntryController.class,GlobalExceptionHandler.class})
    static class TestApplication {
        @Bean FaultProbe faultProbe(){return new FaultProbe();}
    }

    @DynamicPropertySource
    static void isolatedDataSource(DynamicPropertyRegistry registry) throws Exception {
        Path runner=Path.of("src/test/fixtures/b_mysql_fixture.py");
        Process gate=new ProcessBuilder("python",runner.toString(),"gate").redirectErrorStream(true).start();
        String result=new String(gate.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0,gate.waitFor(),"BLOCKED_TEST_ENV: "+result);
        JsonNode c=new ObjectMapper().readTree(Path.of("D:/AicoreRescue/release-readiness-20261014/synthetic-mysql80/fixture-credentials.json").toFile());
        registry.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:33387/"+DB+"?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        registry.add("spring.datasource.username",()->c.path("user").asText());
        registry.add("spring.datasource.password",()->c.path("password").asText());
        registry.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
    }

    /** 在真实写入后注入异常；在真实订单锁前后用 latch 固定交错，绝不替换 SQL。 */
    @Intercepts({@Signature(type=Executor.class,method="update",args={MappedStatement.class,Object.class}),
            @Signature(type=Executor.class,method="query",args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class})})
    static class FaultProbe implements Interceptor {
        final AtomicReference<String> failAfter=new AtomicReference<>();
        volatile String winner;
        volatile CountDownLatch lockHeld;
        volatile CountDownLatch followerAttempted;
        volatile boolean actualWriteObserved;
        public Object intercept(Invocation call) throws Throwable {
            String id=((MappedStatement)call.getArgs()[0]).getId();
            String thread=Thread.currentThread().getName();
            boolean locking=winner!=null && thread.startsWith("b-race") && id.endsWith("OrderMapper.lockByOrderId");
            if(locking && !thread.equals(winner)) {
                assertTrue(lockHeld.await(15,TimeUnit.SECONDS),"winner did not acquire real order lock");
                followerAttempted.countDown();
            }
            Object value=call.proceed();
            if(locking && thread.equals(winner)) {
                lockHeld.countDown();
                assertTrue(followerAttempted.await(15,TimeUnit.SECONDS),"follower did not attempt real order lock");
            }
            String expected=failAfter.get();
            if (id.equals(expected) && failAfter.compareAndSet(expected,null)) {
                actualWriteObserved=true;
                throw new IllegalStateException("B_SYNTHETIC_FAILURE_AFTER_REAL_WRITE");
            }
            return value;
        }
        void raceWinner(String name){winner=name;lockHeld=new CountDownLatch(1);followerAttempted=new CountDownLatch(1);}
        void reset(){failAfter.set(null);winner=null;lockHeld=null;followerAttempted=null;actualWriteObserved=false;}
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource datasource;
    @Autowired PasswordEncoder encoder;
    @Autowired AuthService auth;
    @Autowired FamilyService families;
    @Autowired DraftLedgerEntryService drafts;
    @Autowired OrderService orders;
    @Autowired SettlementService settlements;
    @Autowired JwtTokenProvider tokens;
    @Autowired FaultProbe fault;
    @Autowired PlatformTransactionManager transactionManager;
    final ObjectMapper json=new ObjectMapper();

    @BeforeEach
    void seedOnlyFictionalBusinessData() throws Exception {
        fault.reset();SecurityContextHolder.clearContext();
        assertEquals(DB,jdbc.queryForObject("SELECT DATABASE()",String.class));
        // 同一专属连接关闭/恢复会话FK检查，仅清空本task虚构记录。
        try(Connection c=datasource.getConnection();var st=c.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS=0");
            try {
                List<String> tables=jdbc.queryForList("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()",String.class);
                for(String table:tables){assertTrue(table.matches("[a-z_]+"));st.executeUpdate("DELETE FROM `"+table+"`");}
            } finally {st.execute("SET FOREIGN_KEY_CHECKS=1");}
        }
        for(int i=1;i<=4;i++) jdbc.update("INSERT INTO users(id,username,password_hash,is_active) VALUES(?,?,?,1)",1000+i,"fictional-"+i,encoder.encode(SYNTHETIC_PASSWORD));
        jdbc.update("INSERT INTO families(id,family_code,family_name,admin_user_id) VALUES(2001,'B-FAM-1','虚构家庭一',1001),(2002,'B-FAM-2','虚构家庭二',1003)");
        jdbc.update("UPDATE users SET family_id=CASE WHEN id IN(1001,1002) THEN 2001 ELSE 2002 END");
        jdbc.update("INSERT INTO user_family_roles(user_id,family_id,role) VALUES(1001,2001,'ADMIN'),(1002,2001,'MEMBER'),(1003,2002,'ADMIN'),(1004,2002,'MEMBER')");
        jdbc.update("INSERT INTO product_master(id,product_code,product_name,asset_type) VALUES(3001,'B-FICTIONAL','虚构测试基金','FUND')");
        jdbc.update("INSERT INTO accounts(id,account_code,account_name,account_kind,account_type,owner_type,owner_user_id,owner_family_id,fund_usage,balance,initial_balance) VALUES(4001,'B-ACC-1','虚构现金一','REAL','CASH','PERSONAL',1001,NULL,'SPENDABLE',1000,1000),(4002,'B-ACC-2','虚构现金二','REAL','CASH','PERSONAL',1003,NULL,'SPENDABLE',1000,1000),(4003,'B-ACC-3','虚构投资现金','REAL','CASH','PERSONAL',1001,NULL,'INVESTABLE',1000,1000),(4004,'B-ACC-4','虚构家庭账户','REAL','CASH','FAMILY',NULL,2001,'SPENDABLE',1000,1000)");
    }
    @AfterEach void clearIdentity(){fault.reset();SecurityContextHolder.clearContext();}
    String login(int user) throws Exception {
        String body=mvc.perform(post("/api/v2/auth/login").contentType("application/json").content("{\"username\":\"fictional-"+user+"\",\"password\":\""+SYNTHETIC_PASSWORD+"\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("token").asText();
    }
    void identity(int user){
        String token=tokens.generateToken("fictional-"+user);
        assertTrue(tokens.validateToken(token));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(tokens.getUsernameFromToken(token),null,List.of()));
    }
    long count(String table){assertTrue(table.matches("[a-z_]+"));return jdbc.queryForObject("SELECT COUNT(*) FROM `"+table+"`",Long.class);}
    BigDecimal cash(){return jdbc.queryForObject("SELECT balance FROM accounts WHERE id=4001",BigDecimal.class);}
    DraftLedgerEntryDTO draft(long account,Long family,String ref) {
        CreateDraftRequest request=new CreateDraftRequest();request.setSourceType("b-synthetic");request.setSourceRef(ref);
        request.setParsedPayloadJson("{\"txnType\":\"EXPENSE\",\"accountId\":"+account+",\"amount\":10}");
        return drafts.createDraft(1001L,family,request);
    }
    Order investment(){identity(1);return orders.createInvestmentDraftOrder(1001L,2001L,3001L,"SUBSCRIPTION",new BigDecimal("100"),4003L,null,null,"虚构合成订单");}
    SettlementPreviewDTO preview(String id){return settlements.previewSettlement(id,LocalDate.of(2026,10,14),LocalDate.of(2026,10,14),BigDecimal.ONE,new BigDecimal("100"),null,BigDecimal.ZERO);}
    SettlementConfirm confirm(String id,String token){return settlements.confirmSettlement(id,LocalDate.of(2026,10,14),LocalDate.of(2026,10,14),BigDecimal.ONE,new BigDecimal("100"),null,BigDecimal.ZERO,token);}

    @Test void realLoginLogoutExpiryAndBadPassword() throws Exception {
        String token=login(1);
        assertNotNull(jdbc.queryForObject("SELECT last_login_at FROM users WHERE id=1001",java.sql.Timestamp.class));
        mvc.perform(get("/api/v2/users/me").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("username").value("fictional-1"));
        mvc.perform(post("/api/v2/auth/login").contentType("application/json").content("{\"username\":\"fictional-1\",\"password\":\"wrong-fictional\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v2/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(get("/api/v2/users/me")).andExpect(status().isUnauthorized());
        // 当前产品契约为客户端删除token，无服务端撤销；明确验证该边界。
        mvc.perform(get("/api/v2/users/me").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        String expired=Jwts.builder().subject("fictional-1").expiration(new Date(0)).signWith(Keys.hmacShaKeyFor(KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact();
        mvc.perform(get("/api/v2/users/me").header("Authorization","Bearer "+expired)).andExpect(status().isUnauthorized());
    }

    @Test void realFamilyRoleSwitchAndCrossFamilyIsolation() throws Exception {
        String admin=login(1),member=login(2),other=login(3);
        String rolePath="/api/v2/families/members/1002/role";
        mvc.perform(put(rolePath).header("Authorization","Bearer "+member).contentType("application/json").content("{\"role\":\"ADMIN\"}")).andExpect(status().isBadRequest());
        mvc.perform(put(rolePath).header("Authorization","Bearer "+admin).contentType("application/json").content("{\"role\":\"ADMIN\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/v2/families/members/1001/role").header("Authorization","Bearer "+member).contentType("application/json").content("{\"role\":\"MEMBER\"}")).andExpect(status().isOk());
        mvc.perform(put(rolePath).header("Authorization","Bearer "+admin).contentType("application/json").content("{\"role\":\"MEMBER\"}")).andExpect(status().isBadRequest());
        mvc.perform(put(rolePath).header("Authorization","Bearer "+other).contentType("application/json").content("{\"role\":\"MEMBER\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v2/families/members").header("Authorization","Bearer "+other)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        assertEquals("ADMIN",jdbc.queryForObject("SELECT role FROM user_family_roles WHERE user_id=1002 AND family_id=2001",String.class));
    }

    @Test void realFilterDraftOwnerAndFamilyObjectIsolation() throws Exception {
        long personal=draft(4001,null,"personal").getId(),shared=draft(4004,2001L,"shared").getId();
        mvc.perform(get("/api/v2/drafts/"+personal).header("Authorization","Bearer "+login(2))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v2/drafts/"+shared).header("Authorization","Bearer "+login(2))).andExpect(status().isOk());
        mvc.perform(get("/api/v2/drafts/"+shared).header("Authorization","Bearer "+login(3))).andExpect(status().isBadRequest());
        long foreignAccount=draft(4002,null,"foreign-account").getId();
        mvc.perform(post("/api/v2/drafts/"+foreignAccount+"/confirm").header("Authorization","Bearer "+login(1))).andExpect(status().isBadRequest());
        assertEquals(0,count("ledger_txn"));assertEquals(0,count("ledger_posting"));
    }

    @Test void realDatabaseUniqueKeysChecksAndForeignKeys() {
        draft(4001,null,"same-source");
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->jdbc.update("INSERT INTO draft_ledger_entry(owner_user_id,source_type,source_ref) VALUES(1001,'b-synthetic','same-source')"));
        draft(4004,2001L,"same-family-source");
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->jdbc.update("INSERT INTO draft_ledger_entry(owner_user_id,owner_family_id,source_type,source_ref) VALUES(1002,2001,'b-synthetic','same-family-source')"));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("INSERT INTO draft_ledger_entry(owner_user_id,status) VALUES(1001,'INVALID')"));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("INSERT INTO accounts(account_code,account_name,account_type,parent_account_id) VALUES('B-BAD-FK','虚构非法父账户','CASH',999999)"));
        assertEquals(2,count("draft_ledger_entry"));
    }

    @Test void draftConfirmUsesRealTransactionAndRollbackAfterActualWrite() {
        identity(1);
        assertTrue(AopUtils.isAopProxy(drafts));
        long id=draft(4001,null,"rollback").getId();
        long audits=count("draft_lifecycle_event");
        fault.failAfter.set("com.timelordtty.dca.mapper.DraftLedgerEntryMapper.markConfirmed");
        RuntimeException failure=assertThrows(RuntimeException.class,()->drafts.confirmDraft(1001L,null,id));
        assertTrue(fault.actualWriteObserved,"fault must follow real MySQL write: "+failure.getMessage());
        Throwable cause=failure;
        while(cause.getCause()!=null)cause=cause.getCause();
        assertEquals("B_SYNTHETIC_FAILURE_AFTER_REAL_WRITE",cause.getMessage());
        assertEquals("DRAFT",drafts.getDraft(1001L,null,id).getStatus());
        assertNull(drafts.getDraft(1001L,null,id).getConfirmTxnId());
        assertEquals(0,count("ledger_txn"));assertEquals(0,count("ledger_posting"));assertEquals(0,count("settlement_confirm"));
        // REQUIRES_NEW的尝试/失败审计应独立保留，业务confirmed事件必须回滚。
        assertEquals(audits+2,count("draft_lifecycle_event"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM draft_lifecycle_event WHERE event_type='confirmed'",Long.class));
        assertEquals(0,new BigDecimal("1000").compareTo(cash()));
        // 真实回滚后同一草稿可以再次确认，且重放不重复落账。
        String txn=drafts.confirmDraft(1001L,null,id).getConfirmTxnId();
        assertNotNull(txn);assertEquals(txn,drafts.confirmDraft(1001L,null,id).getConfirmTxnId());
        assertEquals(1,count("ledger_txn"));assertEquals(2,count("ledger_posting"));assertEquals(0,new BigDecimal("990").compareTo(cash()));
    }

    @Test void realOrderOwnerDenialAndLegalCancelRestoresPayment() {
        Order order=investment();assertEquals(1,count("ledger_txn"));
        identity(3);assertThrows(RuntimeException.class,()->orders.cancelOrder(order.getOrderId()));
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM orders WHERE order_id=?",String.class,order.getOrderId()));
        identity(1);orders.cancelOrder(order.getOrderId());
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM orders WHERE order_id=?",String.class,order.getOrderId()));
        assertEquals(0,count("ledger_txn"));assertEquals(0,count("ledger_posting"));assertEquals(0,count("settlement_confirm"));
        assertEquals(0,new BigDecimal("1000").compareTo(jdbc.queryForObject("SELECT balance FROM accounts WHERE id=4003",BigDecimal.class)));
    }

    @Test void realSettlementRollbackAfterActualWriteAndLegalConfirm() {
        assertTrue(AopUtils.isAopProxy(settlements));Order order=investment();
        SettlementPreviewDTO p=preview(order.getOrderId());assertTrue(p.isConfirmSupported(),p.getBlockingReasons().toString());
        Map<String,Object> before=snapshot();
        fault.failAfter.set("com.timelordtty.dca.mapper.SettlementConfirmMapper.insert");
        assertThrows(RuntimeException.class,()->confirm(order.getOrderId(),p.getFreshPreviewToken()));
        assertTrue(fault.actualWriteObserved);assertEquals(before,snapshot());
        SettlementConfirm result=confirm(order.getOrderId(),preview(order.getOrderId()).getFreshPreviewToken());
        assertNotNull(result.getId());assertEquals(1,count("settlement_confirm"));assertEquals(2,count("ledger_txn"));
        assertEquals("CONFIRMED",jdbc.queryForObject("SELECT status FROM orders WHERE order_id=?",String.class,order.getOrderId()));
        assertEquals(result.getId(),confirm(order.getOrderId(),p.getFreshPreviewToken()).getId());
    }

    Map<String,Object> snapshot(){
        Map<String,Object> result=new LinkedHashMap<>();
        for(String table:List.of("ledger_txn","ledger_posting","settlement_confirm","orders","order_funding_line","accounts","draft_ledger_entry"))
            result.put(table,jdbc.queryForList("SELECT * FROM `"+table+"` ORDER BY id"));
        return result;
    }

    @Test void concurrentConfirmCancelMustLeaveOneConsistentTerminalOutcome() throws Exception {
        Order order=investment();SettlementPreviewDTO p=preview(order.getOrderId());assertTrue(p.isConfirmSupported(),p.getBlockingReasons().toString());
        fault.raceWinner("b-race-confirm");
        List<String> outcomes=race(()->{Thread.currentThread().setName("b-race-confirm");confirm(order.getOrderId(),p.getFreshPreviewToken());return "confirmed";},()->{Thread.currentThread().setName("b-race-cancel");return prefetchedTransaction(order.getOrderId(),()->{orders.cancelOrder(order.getOrderId());return "cancelled";});});
        String status=jdbc.queryForObject("SELECT status FROM orders WHERE order_id=?",String.class,order.getOrderId());
        long sc=count("settlement_confirm"),tx=count("ledger_txn"),postings=count("ledger_posting");
        System.out.println("B_REAL_CONFIRM_CANCEL_OUTCOMES="+outcomes+" FINAL="+status+" SETTLEMENT="+sc+" LEDGER="+tx+" POSTINGS="+postings);
        assertTrue(outcomes.contains("confirmed")||outcomes.contains("cancelled"),"no legal operation completed: "+outcomes);
        assertFalse(outcomes.stream().allMatch(x->x.equals("confirmed")||x.equals("cancelled")),"two conflicting operations succeeded: "+outcomes+"; final="+status+" settlement="+sc+" ledger="+tx+" postings="+postings);
        assertEquals("confirmed",outcomes.get(0));
        assertTrue(outcomes.get(1).contains("只能取消PENDING"),outcomes.toString());
        assertEquals("CONFIRMED",status);
        if("CONFIRMED".equals(status)){assertEquals(1,sc);assertEquals(2,tx);assertEquals(4,postings);}
        else {assertEquals("CANCELLED",status);assertEquals(0,sc);assertEquals(0,tx);assertEquals(0,postings);}
        assertConfirmedFundingAndCash(order.getOrderId());
    }

    void assertConfirmedFundingAndCash(String id) {
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM order_funding_line WHERE order_id=?",Long.class,id));
        assertEquals(0,new BigDecimal("900").compareTo(jdbc.queryForObject("SELECT balance FROM accounts WHERE id=4003",BigDecimal.class)));
        assertEquals(0,BigDecimal.ZERO.compareTo(jdbc.queryForObject("SELECT reserved_amount FROM accounts WHERE id=4003",BigDecimal.class)));
        // 无关联现实账户的基金持仓由 POSITION 分录推导，initial_shares 不是这条路径的份额字段。
        // 同时核对唯一持仓分录、份额、成本及对应虚拟账户余额，防止重复结算或只写账未更新账户。
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ledger_posting p JOIN ledger_txn t ON t.txn_id=p.txn_id JOIN accounts a ON a.id=p.account_id WHERE t.order_id=? AND p.account_type='POSITION' AND p.posting_type='DEBIT' AND p.shares=100 AND p.amount=100 AND a.account_kind='VIRTUAL' AND a.virtual_subtype='POSITION' AND a.balance=100 AND a.initial_shares IS NULL",Long.class,id));
        assertEquals(0,count("draft_ledger_entry"));
    }

    @Test void cancelFirstMustRejectConfirmWithoutSettlementOrRepeatedRefund() throws Exception {
        Order order=investment();String token=preview(order.getOrderId()).getFreshPreviewToken();
        fault.raceWinner("b-race-cancel");
        List<String> outcomes=race(()->{Thread.currentThread().setName("b-race-cancel");orders.cancelOrder(order.getOrderId());return "cancelled";},()->{Thread.currentThread().setName("b-race-confirm");return prefetchedTransaction(order.getOrderId(),()->{confirm(order.getOrderId(),token);return "confirmed";});});
        assertEquals("cancelled",outcomes.get(0));assertTrue(outcomes.get(1).contains("只有 PENDING"),outcomes.toString());
        assertCancelled(order.getOrderId());
        Map<String,Object> after=snapshot();
        assertThrows(RuntimeException.class,()->confirm(order.getOrderId(),token));
        assertThrows(RuntimeException.class,()->orders.cancelOrder(order.getOrderId()));
        assertEquals(after,snapshot());
        System.out.println("B_REAL_CANCEL_CONFIRM_OUTCOMES="+outcomes+" FINAL=CANCELLED SETTLEMENT=0 LEDGER=0 POSTINGS=0");
    }

    void assertCancelled(String id) {
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM orders WHERE order_id=?",String.class,id));
        for(String table:List.of("settlement_confirm","ledger_txn","ledger_posting","order_funding_line","draft_ledger_entry"))assertEquals(0,count(table),table);
        assertEquals(0,new BigDecimal("1000").compareTo(jdbc.queryForObject("SELECT balance FROM accounts WHERE id=4003",BigDecimal.class)));
        assertEquals(0,BigDecimal.ZERO.compareTo(jdbc.queryForObject("SELECT reserved_amount FROM accounts WHERE id=4003",BigDecimal.class)));
    }

    @Test void doubleConfirmReturnsSameSettlementAndOnlyOneLedgerSet() throws Exception {
        Order order=investment();String token=preview(order.getOrderId()).getFreshPreviewToken();
        fault.raceWinner("b-race-confirm-first");
        List<String> outcomes=race(()->{Thread.currentThread().setName("b-race-confirm-first");return "settlement:"+confirm(order.getOrderId(),token).getId();},()->{Thread.currentThread().setName("b-race-confirm-second");return prefetchedTransaction(order.getOrderId(),()->"settlement:"+confirm(order.getOrderId(),token).getId());});
        assertTrue(outcomes.get(0).startsWith("settlement:"),outcomes.toString());assertEquals(outcomes.get(0),outcomes.get(1));
        assertEquals(1,count("settlement_confirm"));assertEquals(2,count("ledger_txn"));assertEquals(4,count("ledger_posting"));
        assertConfirmedFundingAndCash(order.getOrderId());
        Map<String,Object> after=snapshot();
        assertEquals(outcomes.get(0),"settlement:"+confirm(order.getOrderId(),token).getId());assertEquals(after,snapshot());
        System.out.println("B_REAL_DOUBLE_CONFIRM="+outcomes+" SETTLEMENT=1 LEDGER=2 POSTINGS=4");
    }

    @Test void doubleCancelRejectsSecondAndRestoresPaymentExactlyOnce() throws Exception {
        Order order=investment();fault.raceWinner("b-race-cancel-first");
        List<String> outcomes=race(()->{Thread.currentThread().setName("b-race-cancel-first");orders.cancelOrder(order.getOrderId());return "cancelled";},()->{Thread.currentThread().setName("b-race-cancel-second");return prefetchedTransaction(order.getOrderId(),()->{orders.cancelOrder(order.getOrderId());return "cancelled";});});
        assertEquals("cancelled",outcomes.get(0));assertTrue(outcomes.get(1).contains("只能取消PENDING"),outcomes.toString());
        assertCancelled(order.getOrderId());
        System.out.println("B_REAL_DOUBLE_CANCEL="+outcomes+" FINAL=CANCELLED SETTLEMENT=0 LEDGER=0 POSTINGS=0");
    }

    @Test void cancelFailureAfterRealStatusWriteRollsBackAllRefundSideEffects() {
        Order order=investment();Map<String,Object> before=snapshot();
        fault.failAfter.set("com.timelordtty.dca.mapper.OrderMapper.update");
        assertThrows(RuntimeException.class,()->orders.cancelOrder(order.getOrderId()));
        assertTrue(fault.actualWriteObserved);assertEquals(before,snapshot());
        orders.cancelOrder(order.getOrderId());assertCancelled(order.getOrderId());
    }

    @Test void confirmLateFailureRollsBackSettlementLedgerAccountsAndFunding() {
        Order order=investment();String token=preview(order.getOrderId()).getFreshPreviewToken();
        Map<String,Object> before=snapshot();fault.failAfter.set("com.timelordtty.dca.mapper.OrderMapper.update");
        assertThrows(RuntimeException.class,()->confirm(order.getOrderId(),token));
        assertTrue(fault.actualWriteObserved);assertEquals(before,snapshot());
        assertNotNull(confirm(order.getOrderId(),token).getId());assertConfirmedFundingAndCash(order.getOrderId());
    }

    @Test void stalePreviewAndJoinedRepeatableReadAreRejectedBeforeWrites() {
        Order order=investment();String token=preview(order.getOrderId()).getFreshPreviewToken();
        jdbc.update("UPDATE accounts SET balance=balance+1 WHERE id=4003");
        Map<String,Object> before=snapshot();
        assertThrows(RuntimeException.class,()->confirm(order.getOrderId(),token));assertEquals(before,snapshot());
        TransactionTemplate rr=new TransactionTemplate(transactionManager);rr.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        rr.executeWithoutResult(status->{
            identity(1);preview(order.getOrderId()); // deliberately establishes an old RR snapshot/cache
            assertThrows(IllegalStateException.class,()->orders.cancelOrder(order.getOrderId()));
            status.setRollbackOnly();
        });
        rr.executeWithoutResult(status->{
            identity(1);preview(order.getOrderId());
            assertThrows(IllegalStateException.class,()->confirm(order.getOrderId(),token));
            status.setRollbackOnly();
        });
        assertEquals(before,snapshot());
    }

    @Test void joinedReadCommittedClearsCachedPreviewAndKeepsOuterRollbackAtomic() {
        Order order=investment();TransactionTemplate rc=new TransactionTemplate(transactionManager);
        rc.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        Map<String,Object> before=snapshot();
        rc.executeWithoutResult(status->{
            identity(1);String old=preview(order.getOrderId()).getFreshPreviewToken();
            TransactionTemplate external=new TransactionTemplate(transactionManager);
            external.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            external.executeWithoutResult(other->jdbc.update("UPDATE accounts SET balance=balance+1 WHERE id=4003"));
            assertThrows(RuntimeException.class,()->confirm(order.getOrderId(),old));
            status.setRollbackOnly();
        });
        jdbc.update("UPDATE accounts SET balance=balance-1 WHERE id=4003");
        assertEquals(before,snapshot());
        rc.executeWithoutResult(status->{
            identity(1);String fresh=preview(order.getOrderId()).getFreshPreviewToken();
            assertNotNull(confirm(order.getOrderId(),fresh));status.setRollbackOnly();
        });
        assertEquals(before,snapshot());
    }

    @Test void legacyNullSourceStillReservesPendingSellSharesWithoutCountingTarget() {
        Order order=investment();
        jdbc.update("UPDATE orders SET order_type='REDEMPTION',shares=10 WHERE order_id=?",order.getOrderId());
        jdbc.update("UPDATE order_funding_line SET amount=NULL,shares=10,line_type=NULL WHERE order_id=?",order.getOrderId());
        jdbc.update("INSERT INTO order_funding_line(order_id,line_no,account_id,shares,line_type) VALUES(?,2,4003,100,'TARGET')",order.getOrderId());
        assertEquals(0,new BigDecimal("10").compareTo(orders.sumPendingSellSharesByAccount(3001L,1001L,4003L)));
        assertEquals(0,BigDecimal.ZERO.compareTo(orders.sumPendingSellSharesByAccount(3001L,1003L,4003L)));
        jdbc.update("UPDATE orders SET status='CANCELLED' WHERE order_id=?",order.getOrderId());
        assertEquals(0,BigDecimal.ZERO.compareTo(orders.sumPendingSellSharesByAccount(3001L,1001L,4003L)));
    }

    @Test void realBusinessDumpRestoreIntoIndependentTaskSchema() throws Exception {
        identity(1);
        long id=draft(4001L,null,"backup-business").getId();
        drafts.confirmDraft(1001L,null,id);
        Order order=investment();SettlementPreviewDTO p=preview(order.getOrderId());
        assertTrue(p.isConfirmSupported(),p.getBlockingReasons().toString());
        confirm(order.getOrderId(),p.getFreshPreviewToken());
        assertEquals(3,count("ledger_txn"));assertEquals(6,count("ledger_posting"));assertEquals(1,count("settlement_confirm"));
        Process process=new ProcessBuilder("python","src/test/fixtures/b_mysql_fixture.py","restore").redirectErrorStream(true).start();
        String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0,process.waitFor(),output);
        assertTrue(output.contains("REAL_MYSQL_SYNTHETIC_FULL_DUMP_RESTORE_PASSED"));
    }

    String prefetchedTransaction(String id,Supplier<String> operation) {
        TransactionTemplate rc=new TransactionTemplate(transactionManager);
        rc.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return rc.execute(status->{
            // 身份、订单、空 settlement、资金行及账户均在锁前进入真实 MyBatis 缓存。
            assertTrue(preview(id).isConfirmSupported());
            return operation.get();
        });
    }

    List<String> race(Supplier<String> a,Supplier<String> b) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2,r->new Thread(r,"b-race-"+UUID.randomUUID()));
        try {
            List<Future<String>> futures=new ArrayList<>();
            for(Supplier<String> operation:List.of(a,b))futures.add(pool.submit(()->{identity(1);try{return operation.get();}catch(RuntimeException e){return "rejected:"+e.getClass().getSimpleName()+":"+e.getMessage();}finally{SecurityContextHolder.clearContext();}}));
            List<String> results=new ArrayList<>();for(Future<String> future:futures)results.add(future.get(45,TimeUnit.SECONDS));return results;
        }finally {pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }
}
