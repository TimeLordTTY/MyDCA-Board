package com.timelordtty.dca.config;

import com.timelordtty.dca.security.JwtAuthenticationFilter;
import com.timelordtty.dca.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real production security chain; no database, mock authentication, or real identity. */
@WebMvcTest(controllers = IsolatedJwtFilterTest.IdentityProbe.class,
        properties = {"spring.profiles.active=isolated-auth-test",
                "jwt.secret=synthetic-only-20261014-key-with-at-least-256-bits",
                "jwt.expiration=60000"})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        IsolatedJwtFilterTest.IdentityProbe.class})
@ContextConfiguration(classes = {SecurityConfig.class, JwtAuthenticationFilter.class,
        JwtTokenProvider.class, IsolatedJwtFilterTest.IdentityProbe.class})
class IsolatedJwtFilterTest {
    private static final String KEY = "synthetic-only-20261014-key-with-at-least-256-bits";
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;

    @RestController
    static class IdentityProbe {
        @GetMapping("/isolated-test/identity")
        String identity(Authentication auth) { return auth.getName(); }
    }

    @Test
    void missingMalformedExpiredAndWrongSignatureAreRejected() throws Exception {
        mvc.perform(get("/isolated-test/identity")).andExpect(status().isUnauthorized());
        for (String token : new String[]{"malformed", expired(), wrongSignature()}) {
            mvc.perform(get("/isolated-test/identity").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void eachRequestUsesItsOwnFictionalIdentityAndNoSessionSurvivesTokenRemoval() throws Exception {
        for (String name : new String[]{"fictional-admin", "fictional-member", "fictional-other-owner"}) {
            mvc.perform(get("/isolated-test/identity").header("Authorization", "Bearer " + tokens.generateToken(name)))
                    .andExpect(status().isOk()).andExpect(content().string(name));
            mvc.perform(get("/isolated-test/identity")).andExpect(status().isUnauthorized());
        }
    }

    private String expired() {
        return Jwts.builder().subject("fictional-expired").expiration(new Date(0))
                .signWith(Keys.hmacShaKeyFor(KEY.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private String wrongSignature() {
        return Jwts.builder().subject("fictional-forged").expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor((KEY + "-wrong").getBytes(StandardCharsets.UTF_8))).compact();
    }
}
