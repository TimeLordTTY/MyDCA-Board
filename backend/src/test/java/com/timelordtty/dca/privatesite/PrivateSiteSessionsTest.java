package com.timelordtty.dca.privatesite;

import com.timelordtty.dca.mapper.UserMapper;
import com.timelordtty.dca.model.User;
import org.junit.jupiter.api.Test;
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
        var second = service.issue(u.getUsername(), clock.now.plusSeconds(9999));
        assertEquals(clock.now.plusSeconds(900), second.session().expiresAt());
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
        p.setSessionCapacity(1); p.setSessionTtlSeconds(3601); status(503, () -> service.require("unknown"));
        p.setSessionTtlSeconds(1); p.setOwnerUserId(null); status(503, () -> service.require("unknown"));
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
