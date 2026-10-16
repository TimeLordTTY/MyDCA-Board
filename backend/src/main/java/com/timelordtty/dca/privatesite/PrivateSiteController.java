package com.timelordtty.dca.privatesite;

import com.timelordtty.dca.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

import static com.timelordtty.dca.privatesite.PrivateSiteSessions.denied;

@RestController
@RequestMapping(PrivateSiteSecurity.BASE)
public class PrivateSiteController {
    static final String COOKIE = "__Host-PrivateSite";
    private final PrivateSiteSessions sessions;
    private final JwtTokenProvider tokens;
    private final PrivateSitePublication publication;

    public PrivateSiteController(PrivateSiteSessions sessions, JwtTokenProvider tokens, PrivateSitePublication publication) {
        this.sessions = sessions; this.tokens = tokens; this.publication = publication;
    }

    @PostMapping("/session/exchange")
    public Map<String, Object> exchange(HttpServletRequest request, HttpServletResponse response, Authentication auth) {
        // A same-origin account switch always revokes the previous private session, even if the new JWT fails ACL.
        sessions.revoke(cookie(request));
        clearCookie(response);
        String bearer = request.getHeader("Authorization");
        if (auth == null || bearer == null || !bearer.startsWith("Bearer ")
                || java.util.Collections.list(request.getHeaders("Authorization")).size() != 1
                || !tokens.validateToken(bearer.substring(7))) throw denied(HttpStatus.UNAUTHORIZED);
        Instant expiry;
        try {
            var date = tokens.getExpirationFromToken(bearer.substring(7));
            if (date == null || !auth.getName().equals(tokens.getUsernameFromToken(bearer.substring(7))))
                throw denied(HttpStatus.UNAUTHORIZED);
            expiry = date.toInstant();
        } catch (Exception ex) { throw denied(HttpStatus.UNAUTHORIZED); }
        var issued = sessions.issue(auth.getName(), expiry);
        response.setHeader("Set-Cookie", cookieHeader(issued.cookie(), -1));
        return state(issued.session());
    }

    @GetMapping("/session")
    public Map<String, Object> state(HttpServletRequest request) {
        return state(requireSession(request));
    }

    @PostMapping("/session/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        var session = requireSession(request);
        if (!PrivateSiteSessions.equalSecret(session.csrf(), request.getHeader("X-CSRF-Token")))
            throw denied(HttpStatus.FORBIDDEN);
        sessions.revoke(cookie(request));
        clearCookie(response);
        response.setStatus(204);
    }

    @RequestMapping(value = {"/explore", "/explore/**"}, method = {RequestMethod.GET, RequestMethod.HEAD})
    public void explore(HttpServletRequest request, HttpServletResponse response) {
        requireSession(request);
        var selected = publication.select(request.getRequestURI().substring((PrivateSiteSecurity.BASE + "/explore").length()));
        if (selected.redirect() != null) {
            response.setStatus(302);
            response.setHeader("Location", selected.redirect());
        } else {
            response.setHeader("X-Accel-Redirect", selected.internal());
        }
    }

    private PrivateSiteSessions.Session requireSession(HttpServletRequest request) {
        // Cookie never supplies a Spring Security identity, and mixed-account requests must re-exchange explicitly.
        if (request.getHeader("Authorization") != null) {
            sessions.revoke(cookie(request));
            throw denied(HttpStatus.UNAUTHORIZED);
        }
        return sessions.require(cookie(request));
    }

    private static Map<String, Object> state(PrivateSiteSessions.Session session) {
        return Map.of("authenticated", true, "csrfToken", session.csrf(), "expiresAt", session.expiresAt().toString());
    }

    static String cookie(HttpServletRequest request) {
        String value = null;
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) {
            if (COOKIE.equals(cookie.getName())) {
                if (value != null || !cookie.getValue().matches("[A-Za-z0-9_-]{43}")) throw denied(HttpStatus.UNAUTHORIZED);
                value = cookie.getValue();
            }
        }
        return value;
    }

    private static String cookieHeader(String value, long maxAge) {
        return ResponseCookie.from(COOKIE, value).secure(true).httpOnly(true).sameSite("Lax")
                .path("/").maxAge(maxAge).build().toString();
    }

    private static void clearCookie(HttpServletResponse response) {
        response.setHeader("Set-Cookie", cookieHeader("", 0));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public void rejected(ResponseStatusException ex, HttpServletResponse response) {
        if (ex.getStatusCode().value() == 401) clearCookie(response);
        response.setStatus(ex.getStatusCode().value());
    }
}
