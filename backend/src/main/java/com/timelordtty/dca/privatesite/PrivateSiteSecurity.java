package com.timelordtty.dca.privatesite;

import com.timelordtty.dca.security.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

@Configuration
@EnableConfigurationProperties(PrivateSiteProperties.class)
public class PrivateSiteSecurity {
    static final String BASE = "/api/v2/private-sites";
    static final String ORIGIN = "https://www.timelordtty.cn";

    @Bean
    @Order(1)
    SecurityFilterChain privateSiteSecurityChain(HttpSecurity http, JwtAuthenticationFilter bearer,
                                                PrivateSiteProperties properties) throws Exception {
        // The boundary filter is created only here: never a bean/global Servlet Filter registration.
        http.securityMatcher(BASE, BASE + "/**")
                .csrf(csrf -> csrf.disable()) // Origin + non-simple request header; logout also checks session CSRF.
                .cors(cors -> cors.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(bearer, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new BoundaryFilter(properties), JwtAuthenticationFilter.class);
        return http.build();
    }

    static final class BoundaryFilter extends OncePerRequestFilter {
        private final PrivateSiteProperties properties;
        BoundaryFilter(PrivateSiteProperties properties) { this.properties = properties; }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader("Vary", "Cookie, Authorization, Origin");
            response.setHeader("X-Content-Type-Options", "nosniff");
            if (!properties.sessionsEnabled()) { response.setStatus(503); return; }
            if (!trusted(request)) { response.setStatus(403); return; }
            String uri = request.getRequestURI();
            if (uri.startsWith(BASE + "/session")) {
                boolean read = "GET".equals(request.getMethod());
                String origin = request.getHeader("Origin");
                boolean sameOrigin = ORIGIN.equals(origin) || (read && origin == null
                        && "same-origin".equals(request.getHeader("Sec-Fetch-Site"))
                        && request.getHeader("Referer") != null
                        && request.getHeader("Referer").startsWith(ORIGIN + "/"));
                if (!sameOrigin || !Collections.list(request.getHeaders("X-Private-Site-Request")).equals(java.util.List.of("1"))) {
                    response.setStatus(403); return;
                }
            }
            chain.doFilter(request, response);
        }

        private boolean trusted(HttpServletRequest request) {
            if (!Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(request.getRemoteAddr())
                    || !Collections.list(request.getHeaders("Host")).equals(java.util.List.of("www.timelordtty.cn"))
                    || !Collections.list(request.getHeaders("X-Forwarded-Proto")).equals(java.util.List.of("https"))) return false;
            String site = request.getHeader("Sec-Fetch-Site");
            if (site != null && !Set.of("same-origin", "none").contains(site)) return false;
            String origin = request.getHeader("Origin");
            if (origin != null && !Collections.list(request.getHeaders("Origin")).equals(java.util.List.of(ORIGIN))) return false;
            for (String name : new String[]{"X-User", "X-Authenticated-User", "X-Accel-Redirect", "Forwarded", "X-Forwarded-Host"}) {
                if (request.getHeader(name) != null) return false;
            }
            return true;
        }
    }
}
