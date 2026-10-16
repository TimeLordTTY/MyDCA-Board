package com.timelordtty.dca.privatesite;

import com.timelordtty.dca.mapper.UserMapper;
import com.timelordtty.dca.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrivateSiteSessionsTest {
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    static PrivateSiteProperties properties() {
        var p = new PrivateSiteProperties(); p.setEnabled(true); p.setOwnerUserId(7001L); return p;
    }
    static User user(long id, String name, boolean active) {
        var u = new User(); u.setId(id); u.setUsername(name); u.setIsActive(active); return u;
    }
    static void status(int expected, org.junit.jupiter.api.function.Executable call) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, call).getStatusCode().value());
    }

    @Test void ttlIsBoundedByJwtAndReclaimedWithoutScheduler() {
        var p = properties(); p.setSessionCapacity(1);
        var users = mock(UserMapper.class); var u = user(7001, "fixture-owner", true);
        when(users.selectByUsername(u.getUsername())).thenReturn(u); when(users.selectById(u.getId())).thenReturn(u);
        var clock = new MutableClock(); var service = new PrivateSiteSessions(p, users, clock);
        var first = service.issue(u.getUsername(), clock.now.plusSeconds(3));
        assertEquals(clock.now.plusSeconds(3), first.session().expiresAt());
        assertEquals(43, first.cookie().length()); assertNotEquals(first.cookie(), first.session().csrf());
        status(429, () -> service.issue(u.getUsername(), clock.now.plusSeconds(9999)));
        clock.now = clock.now.plusSeconds(3);
        status(401, () -> service.require(first.cookie()));
        var second = service.issue(u.getUsername(), clock.now.plusSeconds(172800));
        assertEquals(clock.now.plusSeconds(86400), second.session().expiresAt());
        assertNotEquals(first.cookie(), second.cookie());
        service.revoke(second.cookie()); status(401, () -> service.require(second.cookie()));
    }

    @Test void restartDisabledConfigAndDisabledRenamedOrReassignedUsersFailClosed() {
        var p = properties(); var users = mock(UserMapper.class); var clock = new MutableClock();
        var owner = user(7001, "fixture-owner", true);
        when(users.selectByUsername(owner.getUsername())).thenReturn(owner); when(users.selectById(7001L)).thenReturn(owner);
        var service = new PrivateSiteSessions(p, users, clock);
        var issued = service.issue(owner.getUsername(), clock.now.plusSeconds(600));
        status(401, () -> new PrivateSiteSessions(p, users, clock).require(issued.cookie()));
        owner.setIsActive(false); status(401, () -> service.require(issued.cookie()));
        owner.setIsActive(true); status(401, () -> service.require(issued.cookie()));
        var renamed = service.issue(owner.getUsername(), clock.now.plusSeconds(600));
        owner.setUsername("renamed-fixture"); status(401, () -> service.require(renamed.cookie()));
        when(users.selectByUsername(owner.getUsername())).thenReturn(owner);
        var changedAcl = service.issue(owner.getUsername(), clock.now.plusSeconds(600));
        p.setOwnerUserId(7002L); status(403, () -> service.require(changedAcl.cookie()));
        p.setEnabled(false); status(503, () -> service.require(changedAcl.cookie()));
        p.setEnabled(true); p.setOwnerUserId(7001L); status(401, () -> service.require(changedAcl.cookie()));
    }

    @Test void unknownNonOwnerDisabledAndInvalidCapacityNeverIssue() {
        var p = properties(); var users = mock(UserMapper.class); var clock = new MutableClock();
        var service = new PrivateSiteSessions(p, users, clock);
        status(401, () -> service.issue("missing", clock.now.plusSeconds(60)));
        when(users.selectByUsername("other")).thenReturn(user(7002, "other", true));
        status(403, () -> service.issue("other", clock.now.plusSeconds(60)));
        when(users.selectByUsername("disabled")).thenReturn(user(7001, "disabled", false));
        status(401, () -> service.issue("disabled", clock.now.plusSeconds(60)));
        p.setSessionCapacity(1025); status(503, () -> service.issue("other", clock.now.plusSeconds(60)));
        p.setSessionCapacity(1); p.setSessionTtlSeconds(86401); status(503, () -> service.require("unknown"));
        p.setSessionTtlSeconds(1); p.setOwnerUserId(null); status(503, () -> service.require("unknown"));
    }

    @Test void defaultDayLimitSurvivesFifteenMinutesButReadsNeverRenewIt() {
        var p = properties(); assertEquals(86400, p.getSessionTtlSeconds());
        var users = mock(UserMapper.class); var owner = user(7001, "fixture-owner", true);
        when(users.selectByUsername(owner.getUsername())).thenReturn(owner);
        when(users.selectById(owner.getId())).thenReturn(owner);
        var clock = new MutableClock(); var start = clock.now;
        var service = new PrivateSiteSessions(p, users, clock);
        var issued = service.issue(owner.getUsername(), start.plusSeconds(172800));
        for (long elapsed : new long[] {900, 901, 960, 43200, 86399}) {
            clock.now = start.plusSeconds(elapsed);
            assertEquals(start.plusSeconds(86400), service.require(issued.cookie()).expiresAt());
        }
        clock.now = start.plusSeconds(86400);
        status(401, () -> service.require(issued.cookie()));
        status(401, () -> service.issue(owner.getUsername(), clock.now));
        status(401, () -> service.issue(owner.getUsername(), null));
        verify(users, never()).insert(any()); verify(users, never()).update(any());
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 900, 3600, 3601, 86399, 86400})
    void configuredTtlUsesEarlierOfLimitAndJwt(long ttl) {
        var p = properties(); p.setSessionTtlSeconds(ttl);
        var users = mock(UserMapper.class); var owner = user(7001, "fixture-owner", true);
        when(users.selectByUsername(owner.getUsername())).thenReturn(owner);
        when(users.selectById(owner.getId())).thenReturn(owner);
        var clock = new MutableClock(); var start = clock.now;
        var service = new PrivateSiteSessions(p, users, clock);
        var longJwt = service.issue(owner.getUsername(), start.plusSeconds(172800));
        var shortJwt = service.issue(owner.getUsername(), start.plusSeconds(60));
        assertEquals(start.plusSeconds(ttl), longJwt.session().expiresAt());
        assertEquals(start.plusSeconds(Math.min(ttl, 60)), shortJwt.session().expiresAt());
        clock.now = start.plusSeconds(Math.min(ttl, 60));
        status(401, () -> service.require(shortJwt.cookie()));
        clock.now = start.plusSeconds(ttl);
        status(401, () -> service.require(longJwt.cookie()));
    }

    @Test void advancingClockCannotMoveDayLimitPastVerifiedJwtDeadline() {
        var p = properties(); var users = mock(UserMapper.class);
        when(users.selectByUsername("fixture-owner")).thenReturn(user(7001, "fixture-owner", true));
        var clock = new MutableClock() {
            @Override public Instant instant() {
                var sampled = now; now = now.plusMillis(1); return sampled;
            }
        };
        var start = clock.now; var jwtExpiry = start.plusSeconds(86400).plusMillis(1);
        var issued = new PrivateSiteSessions(p, users, clock).issue("fixture-owner", jwtExpiry);
        assertFalse(issued.session().expiresAt().isAfter(jwtExpiry));
        assertEquals(start.plusSeconds(86400), issued.session().expiresAt());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, 86401, Long.MIN_VALUE, Long.MAX_VALUE})
    void invalidTtlFailsClosedBeforeExpiryArithmetic(long ttl) {
        var p = properties(); p.setSessionTtlSeconds(ttl);
        var users = mock(UserMapper.class); var clock = new MutableClock();
        var service = new PrivateSiteSessions(p, users, clock);
        assertFalse(p.sessionsEnabled());
        status(503, () -> service.issue("fixture-owner", clock.now.plusSeconds(172800)));
        status(503, () -> service.require("unknown"));
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"9223372036854775808", "-9223372036854775809", "18446744073709638016"})
    void overflowingConfigurationCannotWrapIntoAnAllowedTtl(String ttl) {
        var source = new MapConfigurationPropertySource(java.util.Map.of("private-sites.session-ttl-seconds", ttl));
        assertThrows(BindException.class, () -> new Binder(source)
                .bind("private-sites", Bindable.of(PrivateSiteProperties.class)));
    }

    @Test void concurrentIssueCannotExceedCapacity() throws Exception {
        var p = properties(); p.setSessionCapacity(3); var users = mock(UserMapper.class); var clock = new MutableClock();
        when(users.selectByUsername("fixture-owner")).thenReturn(user(7001, "fixture-owner", true));
        var service = new PrivateSiteSessions(p, users, clock);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            var jobs = new java.util.ArrayList<java.util.concurrent.Callable<Boolean>>();
            for (int i = 0; i < 24; i++) jobs.add(() -> {
                try { service.issue("fixture-owner", clock.now.plusSeconds(60)); return true; }
                catch (ResponseStatusException ex) { assertEquals(429, ex.getStatusCode().value()); return false; }
            });
            int issued = 0; for (var result : pool.invokeAll(jobs)) if (result.get()) issued++;
            assertEquals(3, issued);
        } finally { pool.shutdownNow(); }
    }
}
