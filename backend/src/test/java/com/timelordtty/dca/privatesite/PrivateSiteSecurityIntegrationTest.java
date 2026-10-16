package com.timelordtty.dca.privatesite;

import com.fasterxml.jackson.databind.JsonNode;
import com.timelordtty.dca.config.SecurityConfig;
import com.timelordtty.dca.controller.AccountController;
import com.timelordtty.dca.controller.UserController;
import com.timelordtty.dca.mapper.UserMapper;
import com.timelordtty.dca.model.User;
import com.timelordtty.dca.security.JwtAuthenticationFilter;
import com.timelordtty.dca.security.JwtTokenProvider;
import com.timelordtty.dca.service.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static com.timelordtty.dca.privatesite.PrivateSiteSessionsTest.user;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual new and legacy Spring Security chains, JWT provider/filter, controllers and UserService; no Boot/SQL. */
class PrivateSiteSecurityIntegrationTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, PrivateSiteSecurity.class,
            PrivateSiteController.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
            UserService.class, UserController.class, AccountController.class})
    static class App {
        @Bean UserMapper users() { return mock(UserMapper.class); }
        @Bean AccountService accounts() { return mock(AccountService.class); }
        @Bean MmfSharesService shares() { return mock(MmfSharesService.class); }
        @Bean FamilyService family() { return mock(FamilyService.class); }
        @Bean PrivateSiteSessionsTest.MutableClock clock() {
            var clock = new PrivateSiteSessionsTest.MutableClock();
            clock.now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
            return clock;
        }
        @Bean PrivateSiteSessions sessions(PrivateSiteProperties p, UserMapper users,
                                         PrivateSiteSessionsTest.MutableClock clock) {
            return new PrivateSiteSessions(p, users, clock);
        }
        @Bean PrivateSitePublication publication(PrivateSiteProperties p) {
            return spy(new PrivateSitePublication(p));
        }
    }

    @TempDir Path root;
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    PrivateSiteProperties properties;
    JwtTokenProvider tokens;
    UserMapper users;
    User owner;
    String key;
    String release;
    PublicationFixture fixture;
    PrivateSiteSessionsTest.MutableClock clock;

    @BeforeEach void setup() throws Exception {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext()); context.register(App.class); context.refresh();
        properties = context.getBean(PrivateSiteProperties.class);
        clock = context.getBean(PrivateSiteSessionsTest.MutableClock.class);
        properties.setEnabled(true); properties.setOwnerUserId(7001L); properties.setProtocolVersion(1);
        properties.setPublicationRoot(root.toString());
        users = context.getBean(UserMapper.class); owner = user(7001, "synthetic-owner", true);
        User other = user(7002, "synthetic-other", true);
        when(users.selectByUsername(owner.getUsername())).thenReturn(owner); when(users.selectById(7001L)).thenReturn(owner);
        when(users.selectByUsername(other.getUsername())).thenReturn(other); when(users.selectById(7002L)).thenReturn(other);
        when(context.getBean(AccountService.class).getAccountTree(7001L, null)).thenReturn(List.of());
        tokens = context.getBean(JwtTokenProvider.class);
        key = java.util.Base64.getEncoder().encodeToString(Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256).getEncoded());
        ReflectionTestUtils.setField(tokens, "secret", key); ReflectionTestUtils.setField(tokens, "expiration", 60000L);
        fixture = new PublicationFixture(root, "1".repeat(64)); release = fixture.publish(null);
        // TempDir is writable by this test process. Mock only OS ACL checks; all path/byte/schema checks stay real.
        var publication = context.getBean(PrivateSitePublication.class);
        doNothing().when(publication).checkReadOnlyRoot(any()); doNothing().when(publication).checkReadOnlyRelease(any());
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }
    @AfterEach void close() {
        if (context == null) return;
        try {
            assertTrue(context.getBeansOfType(javax.sql.DataSource.class).isEmpty());
            for (var invocation : mockingDetails(users).getInvocations())
                assertTrue(List.of("selectById", "selectByUsername").contains(invocation.getMethod().getName()),
                        "Private-site requests must only read synthetic users");
            for (var invocation : mockingDetails(context.getBean(AccountService.class)).getInvocations())
                assertEquals("getAccountTree", invocation.getMethod().getName());
            verifyNoInteractions(context.getBean(MmfSharesService.class), context.getBean(FamilyService.class));
        } finally { context.close(); }
    }

    MockHttpServletRequestBuilder proxy(MockHttpServletRequestBuilder request) {
        return request.header("Host", "www.timelordtty.cn").header("X-Forwarded-Proto", "https")
                .with(r -> { r.setRemoteAddr("127.0.0.1"); return r; });
    }
    MockHttpServletRequestBuilder session(MockHttpServletRequestBuilder request) {
        return proxy(request).header("Origin", PrivateSiteSecurity.ORIGIN).header("X-Private-Site-Request", "1");
    }
    MvcResult exchange(String token) throws Exception {
        return mvc.perform(session(post(PrivateSiteSecurity.BASE + "/session/exchange")).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
    }
    Cookie cookie(MvcResult result) {
        String header = result.getResponse().getHeader("Set-Cookie");
        return new Cookie(PrivateSiteController.COOKIE, header.substring(header.indexOf('=') + 1, header.indexOf(';')));
    }
    JsonNode body(MvcResult result) throws Exception { return PublicationFixture.JSON.readTree(result.getResponse().getContentAsByteArray()); }
    String path(String file) { return PrivateSiteSecurity.BASE + "/explore/r/" + release + "/" + file; }

    void assertSession(Cookie cookie, Instant expiry) throws Exception {
        mvc.perform(session(get(PrivateSiteSecurity.BASE + "/session")).cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresAt").value(expiry.toString()));
    }

    void assertFileMethods(Cookie cookie, String file, String internal, int expected) throws Exception {
        for (String method : List.of("GET", "HEAD")) for (boolean range : List.of(false, true)) {
            var request = proxy(request(org.springframework.http.HttpMethod.valueOf(method), path(file))).cookie(cookie);
            if (range) request.header("Range", "bytes=0-9");
            var result = mvc.perform(request).andExpect(status().is(expected)).andExpect(content().string(""))
                    .andExpect(header().string("Cache-Control", "private, no-store"));
            if (expected == 200) result.andExpect(header().string("X-Accel-Redirect", internal));
            else result.andExpect(header().doesNotExist("X-Accel-Redirect"))
                    .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        }
    }

    @Test void ownerReadsPastFifteenMinutesUntilExactDayLimitIncludingRetainedSnapshotMethods() throws Exception {
        var start = clock.now; var expiry = start.plusSeconds(86400);
        var exchanged = exchange(signed(owner.getUsername(), key, start.plusSeconds(172800)));
        var cookie = cookie(exchanged);
        // Keep the original host-only browser-session cookie; only the server deadline changes.
        assertFalse(exchanged.getResponse().getHeader("Set-Cookie").contains("Max-Age="));
        assertEquals(expiry.toString(), body(exchanged).path("expiresAt").asText());
        var retained = new PublicationFixture(root, "2".repeat(64)); String second = retained.publish(release);
        var current = new PublicationFixture(root, "3".repeat(64)); String third = current.publish(second);
        for (long elapsed : new long[] {900, 901, 960, 43200, 86399, 86400}) {
            clock.now = start.plusSeconds(elapsed);
            int expected = elapsed < 86400 ? 200 : 401;
            if (expected == 200) assertSession(cookie, expiry);
            else mvc.perform(session(get(PrivateSiteSecurity.BASE + "/session")).cookie(cookie))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
            assertFileMethods(cookie, "app.js", "/_private_ie/" + release + "/ui/app.js", expected);
            assertFileMethods(cookie, "data/manifest.json", "/_private_ie/" + third + "/data/manifest.json", expected);
            assertFileMethods(cookie, "data/" + current.snapshotName,
                    "/_private_ie/" + third + "/data/" + current.snapshotName, expected);
            assertFileMethods(cookie, "data/" + retained.snapshotName,
                    "/_private_ie/" + second + "/data/" + retained.snapshotName, expected);
        }
    }

    @Test void verifiedShorterJwtDeadlineStillExpiresStateAndAllFileMethods() throws Exception {
        var start = clock.now; var expiry = start.plusSeconds(3600);
        var exchanged = exchange(signed(owner.getUsername(), key, expiry)); var cookie = cookie(exchanged);
        assertEquals(expiry.toString(), body(exchanged).path("expiresAt").asText());
        for (long elapsed : new long[] {901, 960, 3599}) {
            clock.now = start.plusSeconds(elapsed); assertSession(cookie, expiry);
            assertFileMethods(cookie, "data/" + fixture.snapshotName,
                    "/_private_ie/" + release + "/data/" + fixture.snapshotName, 200);
        }
        clock.now = expiry;
        mvc.perform(session(get(PrivateSiteSecurity.BASE + "/session")).cookie(cookie)).andExpect(status().isUnauthorized());
        for (String file : List.of("app.js", "data/manifest.json", "data/" + fixture.snapshotName))
            assertFileMethods(cookie, file, null, 401);
    }

    @Test void longerLivedSessionsStillRevokeImmediatelyAfterSixteenMinutes() throws Exception {
        for (String reason : List.of("logout", "disabled-owner", "owner-acl", "account-switch", "mixed-bearer")) {
            var exchanged = exchange(signed(owner.getUsername(), key, clock.now.plusSeconds(172800)));
            var cookie = cookie(exchanged); clock.now = clock.now.plusSeconds(960);
            assertSession(cookie, Instant.parse(body(exchanged).path("expiresAt").asText()));
            switch (reason) {
                case "logout" -> mvc.perform(session(post(PrivateSiteSecurity.BASE + "/session/logout")).cookie(cookie)
                                .header("X-CSRF-Token", body(exchanged).path("csrfToken").asText()))
                        .andExpect(status().isNoContent());
                case "disabled-owner" -> {
                    owner.setIsActive(false);
                    mvc.perform(session(get(PrivateSiteSecurity.BASE + "/session")).cookie(cookie)).andExpect(status().isUnauthorized());
                    owner.setIsActive(true);
                }
                case "owner-acl" -> {
                    properties.setOwnerUserId(7002L);
                    mvc.perform(session(get(PrivateSiteSecurity.BASE + "/session")).cookie(cookie)).andExpect(status().isForbidden());
                    properties.setOwnerUserId(7001L);
                }
                case "account-switch" -> mvc.perform(session(post(PrivateSiteSecurity.BASE + "/session/exchange")).cookie(cookie)
                                .header("Authorization", "Bearer " + signed("synthetic-other", key, clock.now.plusSeconds(172800))))
                        .andExpect(status().isForbidden());
                case "mixed-bearer" -> mvc.perform(session(get(PrivateSiteSecurity.BASE + "/session")).cookie(cookie)
                                .header("Authorization", "Bearer " + signed("synthetic-other", key, clock.now.plusSeconds(172800))))
                        .andExpect(status().isUnauthorized());
            }
            mvc.perform(session(get(PrivateSiteSecurity.BASE + "/session")).cookie(cookie)).andExpect(status().isUnauthorized());
            assertFileMethods(cookie, "data/" + fixture.snapshotName, null, 401);
        }
    }

    @Test void exchangeHasHostCookieMinimalStateNoJwtOrCookieIdAndLogoutRevokes() throws Exception {
        var token = tokens.generateToken(owner.getUsername()); var result = exchange(token); var cookie = cookie(result);
        String set = result.getResponse().getHeader("Set-Cookie");
        for (String attribute : List.of("Secure", "HttpOnly", "SameSite=Lax", "Path=/")) assertTrue(set.contains(attribute));
        assertFalse(set.contains("Domain="));
        assertEquals(3, body(result).size()); assertFalse(result.getResponse().getContentAsString().contains(token));
        assertFalse(result.getResponse().getContentAsString().contains(cookie.getValue()));
        mvc.perform(session(get(PrivateSiteSecurity.BASE + "/session")).cookie(cookie)).andExpect(status().isOk());
        mvc.perform(session(post(PrivateSiteSecurity.BASE + "/session/logout")).cookie(cookie))
                .andExpect(status().isForbidden());
        mvc.perform(session(post(PrivateSiteSecurity.BASE + "/session/logout")).cookie(cookie)
                .header("X-CSRF-Token", body(result).path("csrfToken").asText()))
                .andExpect(status().isNoContent()).andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(session(get(PrivateSiteSecurity.BASE + "/session")).cookie(cookie)).andExpect(status().isUnauthorized());
        assertNotEquals(cookie.getValue(), cookie(exchange(token)).getValue());
    }

    @Test void actualBearerValidationOwnerAclAndAccountSwitchRejectInvalidIdentities() throws Exception {
        String endpoint = PrivateSiteSecurity.BASE + "/session/exchange";
        mvc.perform(session(post(endpoint))).andExpect(status().isUnauthorized());
        for (String invalid : List.of("invalid", signed("synthetic-owner", key, Instant.now().minusSeconds(5)),
                signed("synthetic-owner", "a-different-random-fixture-key-at-least-256-bits", Instant.now().plusSeconds(60)))) {
            mvc.perform(session(post(endpoint)).header("Authorization", "Bearer " + invalid)).andExpect(status().isUnauthorized());
        }
        String noExpiry = Jwts.builder().subject(owner.getUsername()).signWith(Keys.hmacShaKeyFor(key.getBytes())).compact();
        mvc.perform(session(post(endpoint)).header("Authorization", "Bearer " + noExpiry)).andExpect(status().isUnauthorized());
        var old = cookie(exchange(tokens.generateToken(owner.getUsername())));
        mvc.perform(session(post(endpoint)).cookie(old).header("Authorization", "Bearer " + tokens.generateToken("synthetic-other")))
                .andExpect(status().isForbidden());
        mvc.perform(session(get(PrivateSiteSecurity.BASE + "/session")).cookie(old)).andExpect(status().isUnauthorized());
        owner.setIsActive(false);
        mvc.perform(session(post(endpoint)).header("Authorization", "Bearer " + tokens.generateToken(owner.getUsername())))
                .andExpect(status().isUnauthorized());
        mvc.perform(session(post(endpoint)).header("Authorization", "Bearer " + tokens.generateToken("missing-user")))
                .andExpect(status().isUnauthorized());
    }

    static String signed(String username, String key, Instant expiry) {
        return Jwts.builder().subject(username).expiration(Date.from(expiry)).signWith(Keys.hmacShaKeyFor(key.getBytes())).compact();
    }

    @Test void boundaryRequiresExactProxyHostHttpsOriginAndNonSimpleHeader() throws Exception {
        String endpoint = PrivateSiteSecurity.BASE + "/session/exchange";
        String token = tokens.generateToken(owner.getUsername());
        mvc.perform(post(endpoint).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(session(post(endpoint)).with(r -> { r.setRemoteAddr("192.0.2.40"); return r; }))
                .andExpect(status().isForbidden());
        for (String badHost : List.of("127.0.0.1", "timelordtty.cn", "www.timelordtty.cn.evil.invalid", "www.timelordtty.cn:8766")) {
            mvc.perform(session(post(endpoint)).with(r -> { r.removeHeader("Host"); r.addHeader("Host", badHost); return r; }))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(session(post(endpoint)).with(r -> { r.removeHeader("X-Forwarded-Proto"); r.addHeader("X-Forwarded-Proto", "http"); return r; }))
                .andExpect(status().isForbidden());
        for (String header : List.of("X-User", "X-Authenticated-User", "X-Accel-Redirect", "Forwarded", "X-Forwarded-Host")) {
            mvc.perform(session(post(endpoint)).header(header, "forged")).andExpect(status().isForbidden());
        }
        mvc.perform(proxy(post(endpoint))).andExpect(status().isForbidden());
        mvc.perform(proxy(post(endpoint)).header("Origin", "https://evil.invalid").header("X-Private-Site-Request", "1"))
                .andExpect(status().isForbidden());
        mvc.perform(session(post(endpoint)).header("Sec-Fetch-Site", "same-site")).andExpect(status().isForbidden());
        mvc.perform(session(post(endpoint)).header("Origin", "https://evil.invalid")).andExpect(status().isForbidden());
        mvc.perform(session(post(endpoint)).header("X-Private-Site-Request", "1")).andExpect(status().isForbidden());
        mvc.perform(session(options(endpoint)).header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        properties.setEnabled(false);
        mvc.perform(session(post(endpoint))).andExpect(status().isServiceUnavailable());
        properties.setEnabled(true); properties.setOwnerUserId(null);
        mvc.perform(session(post(endpoint))).andExpect(status().isServiceUnavailable());
    }

    @Test void stateBootstrapUsesSameOriginRefererAndRevokesDisabledUsers() throws Exception {
        var cookie = cookie(exchange(tokens.generateToken(owner.getUsername())));
        String endpoint = PrivateSiteSecurity.BASE + "/session";
        mvc.perform(proxy(get(endpoint)).cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(proxy(get(endpoint)).cookie(cookie).header("X-Private-Site-Request", "1")
                .header("Referer", PrivateSiteSecurity.ORIGIN + "/wealth-hub/portal").header("Sec-Fetch-Site", "same-origin"))
                .andExpect(status().isOk());
        mvc.perform(proxy(get(endpoint)).cookie(cookie).header("X-Private-Site-Request", "1")
                .header("Referer", PrivateSiteSecurity.ORIGIN + ".evil.invalid/").header("Sec-Fetch-Site", "same-origin"))
                .andExpect(status().isForbidden());
        mvc.perform(session(get(endpoint)).cookie(cookie, cookie)).andExpect(status().isUnauthorized());
        owner.setIsActive(false);
        mvc.perform(session(get(endpoint)).cookie(cookie)).andExpect(status().isUnauthorized());
        owner.setIsActive(true);
        mvc.perform(session(get(endpoint)).cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test void financialAndAndroidBearerAuthenticationRemainIndependentOfPrivateCookie() throws Exception {
        var cookie = cookie(exchange(tokens.generateToken(owner.getUsername())));
        // Actual existing financial read controller and original SecurityConfig; no business writes.
        mvc.perform(get("/api/v2/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v2/accounts").cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v2/accounts").header("Authorization", "Bearer invalid").cookie(cookie))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v2/accounts").header("Authorization", "Bearer " + tokens.generateToken(owner.getUsername())))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/v2/users/me").header("Authorization", "Bearer " + tokens.generateToken("synthetic-other")).cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(7002));
        verify(context.getBean(AccountService.class)).getAccountTree(7001L, null);
        assertTrue(context.getBeansOfType(PrivateSiteSecurity.BoundaryFilter.class).isEmpty());
        verify(users, never()).insert(any()); verify(users, never()).update(any());
        verify(users, never()).updateLastLoginAt(any(), any());
    }

    @Test void everyUiManifestSnapshotGetHeadAndRangeIsAuthorizedWithoutReturningBytes() throws Exception {
        var cookie = cookie(exchange(tokens.generateToken(owner.getUsername())));
        for (String file : List.of("index.html", "style.css", "forest-model.js", "app.js", "data/manifest.json", "data/" + fixture.snapshotName)) {
            for (String method : List.of("GET", "HEAD")) {
                for (boolean range : List.of(false, true)) {
                    var anonymous = proxy(request(org.springframework.http.HttpMethod.valueOf(method), path(file)));
                    if (range) anonymous.header("Range", "bytes=0-9");
                    mvc.perform(anonymous).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("X-Accel-Redirect"));
                    mvc.perform(proxy(request(org.springframework.http.HttpMethod.valueOf(method), path(file)))
                            .header("Authorization", "Bearer " + tokens.generateToken("synthetic-other")))
                            .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("X-Accel-Redirect"));
                    var authorized = proxy(request(org.springframework.http.HttpMethod.valueOf(method), path(file))).cookie(cookie);
                    if (range) authorized.header("Range", "bytes=0-9");
                    mvc.perform(authorized).andExpect(status().isOk()).andExpect(content().string(""))
                            .andExpect(header().string("Cache-Control", "private, no-store"))
                            .andExpect(header().string("X-Accel-Redirect", "/_private_ie/" + release + "/" + (file.startsWith("data/") ? file : "ui/" + file)));
                }
            }
        }
        mvc.perform(proxy(get(PrivateSiteSecurity.BASE + "/explore")).cookie(cookie)).andExpect(status().isFound())
                .andExpect(header().string("Location", "/explore/r/" + release + "/index.html"));
        mvc.perform(proxy(get(PrivateSiteSecurity.BASE + "/explore/index.html")).cookie(cookie)).andExpect(status().isFound())
                .andExpect(header().string("Location", "/explore/r/" + release + "/index.html"));
        mvc.perform(proxy(post(path("app.js"))).cookie(cookie)).andExpect(status().isMethodNotAllowed());
        for (String bad : List.of("staging/log", "backup/index.html", "queue/job", ".git/config", "release.json", "ui-build.json", "unknown.json"))
            mvc.perform(proxy(get(path(bad))).cookie(cookie)).andExpect(status().isNotFound()).andExpect(header().doesNotExist("X-Accel-Redirect"));
        for (String bad : List.of("/r/" + "b".repeat(64) + "/index.html", "/r/not-a-hash/app.js"))
            assertTrue(mvc.perform(proxy(get(PrivateSiteSecurity.BASE + "/explore" + bad)).cookie(cookie)).andReturn().getResponse().getStatus() >= 400);
        owner.setIsActive(false);
        mvc.perform(proxy(get(path("app.js"))).cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test void pinnedUiReadsCurrentCompatibleManifestAndImmutableRetainedSnapshots() throws Exception {
        var cookie = cookie(exchange(tokens.generateToken(owner.getUsername())));
        var next = new PublicationFixture(root, "2".repeat(64)); String second = next.publish(release);
        mvc.perform(proxy(get(path("data/manifest.json"))).cookie(cookie)).andExpect(status().isOk())
                .andExpect(header().string("X-Accel-Redirect", "/_private_ie/" + second + "/data/manifest.json"));
        mvc.perform(proxy(get(path("data/" + next.snapshotName))).cookie(cookie)).andExpect(status().isOk())
                .andExpect(header().string("X-Accel-Redirect", "/_private_ie/" + second + "/data/" + next.snapshotName));
        var third = new PublicationFixture(root, "3".repeat(64)); third.publish(second);
        mvc.perform(proxy(get(path("data/" + next.snapshotName))).cookie(cookie)).andExpect(status().isOk());
        mvc.perform(proxy(get(path("app.js"))).cookie(cookie)).andExpect(status().isOk())
                .andExpect(header().string("X-Accel-Redirect", "/_private_ie/" + release + "/ui/app.js"));
        var changed = new PublicationFixture(root, "4".repeat(64));
        changed.uiBytes.put("app.js", "changed-ui".getBytes());
        @SuppressWarnings("unchecked") var checks = (java.util.Map<String, Object>) changed.ui.get("files");
        checks.put("app.js", PublicationFixture.check(changed.uiBytes.get("app.js"))); changed.publish(second);
        mvc.perform(proxy(get(path("data/manifest.json"))).cookie(cookie)).andExpect(status().isServiceUnavailable());
    }
}
