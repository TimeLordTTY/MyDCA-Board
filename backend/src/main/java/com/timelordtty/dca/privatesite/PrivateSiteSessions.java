package com.timelordtty.dca.privatesite;

import com.timelordtty.dca.mapper.UserMapper;
import com.timelordtty.dca.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/** Bounded, process-local sessions. No JWT/password storage, scheduler, persistence or credential logging. */
@Service
public class PrivateSiteSessions {
    private final PrivateSiteProperties properties;
    private final UserMapper users;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new HashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public PrivateSiteSessions(PrivateSiteProperties properties, UserMapper users) {
        this(properties, users, Clock.systemUTC());
    }

    PrivateSiteSessions(PrivateSiteProperties properties, UserMapper users, Clock clock) {
        this.properties = properties;
        this.users = users;
        this.clock = clock;
    }

    public synchronized Issued issue(String username, Instant jwtExpiry) {
        cleanup();
        User user = users.selectByUsername(username);
        requireOwner(user);
        if (jwtExpiry == null || !jwtExpiry.isAfter(clock.instant())) throw denied(HttpStatus.UNAUTHORIZED);
        if (sessions.size() >= properties.getSessionCapacity()) throw denied(HttpStatus.TOO_MANY_REQUESTS);
        Instant expiry = jwtExpiry.isBefore(clock.instant().plusSeconds(properties.getSessionTtlSeconds()))
                ? jwtExpiry : clock.instant().plusSeconds(properties.getSessionTtlSeconds());
        String cookie = randomValue();
        Session session = new Session(user.getId(), username, randomValue(), expiry);
        sessions.put(hash(cookie), session);
        return new Issued(cookie, session);
    }

    public synchronized Session require(String cookie) {
        cleanup();
        Session session = cookie == null ? null : sessions.get(hash(cookie));
        if (session == null) throw denied(HttpStatus.UNAUTHORIZED);
        User user = users.selectById(session.userId());
        try {
            requireOwner(user);
            if (!session.username().equals(user.getUsername())) throw denied(HttpStatus.UNAUTHORIZED);
        } catch (ResponseStatusException ex) {
            sessions.remove(hash(cookie));
            throw ex;
        }
        return session;
    }

    private void requireOwner(User user) {
        if (user == null || !Boolean.TRUE.equals(user.getIsActive())) throw denied(HttpStatus.UNAUTHORIZED);
        if (!properties.getOwnerUserId().equals(user.getId())) throw denied(HttpStatus.FORBIDDEN);
    }

    public synchronized void revoke(String cookie) {
        if (cookie != null) sessions.remove(hash(cookie));
        cleanup();
    }

    private void cleanup() {
        if (!properties.sessionsEnabled()) {
            sessions.clear();
            throw denied(HttpStatus.SERVICE_UNAVAILABLE);
        }
        sessions.values().removeIf(session -> !session.expiresAt().isAfter(clock.instant()));
    }

    static ResponseStatusException denied(HttpStatus status) {
        return new ResponseStatusException(status);
    }

    static boolean equalSecret(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private String randomValue() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // Deliberately no generated toString: accidental diagnostics must not expose CSRF/cookie material.
    public static final class Issued {
        private final String cookie;
        private final Session session;
        Issued(String cookie, Session session) { this.cookie = cookie; this.session = session; }
        String cookie() { return cookie; }
        Session session() { return session; }
    }

    public static final class Session {
        private final Long userId;
        private final String username;
        private final String csrf;
        private final Instant expiresAt;
        Session(Long userId, String username, String csrf, Instant expiresAt) {
            this.userId = userId; this.username = username; this.csrf = csrf; this.expiresAt = expiresAt;
        }
        Long userId() { return userId; }
        String username() { return username; }
        String csrf() { return csrf; }
        Instant expiresAt() { return expiresAt; }
    }
}
