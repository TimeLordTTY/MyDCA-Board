package com.timelordtty.dca.config;

import com.timelordtty.dca.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security配置
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * JWT 认证过滤器，在请求进入 Controller 前解析 Bearer Token，并把通过校验的用户身份写入 Spring Security 上下文。
     */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 注入 JWT 认证过滤器，供安全过滤链在请求进入业务接口前完成身份解析。
     */
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * 创建 BCrypt 密码编码器，用于注册入库密码哈希和登录密码校验。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 定义后端安全过滤链：关闭 CSRF，使用无状态 Session，放行登录注册与健康检查，并在用户名密码过滤器前接入 JWT 过滤器。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v2/auth/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/accounts/recalculate-all-balances").permitAll()  // 临时公开：重新计算余额
                        .requestMatchers("/api/v2/ledger/recalculate-all-balance-history").permitAll()  // 临时公开：重算历史余额
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 未登录或 JWT 无效时统一返回 401，让 Android 客户端能够清理失效会话；已登录但无权限仍返回 403。
     */
    AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> response.sendError(HttpStatus.UNAUTHORIZED.value());
    }

    /**
     * 区分匿名访问与已登录越权，避免 Android 把失效会话误判为普通权限不足。
     */
    AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            boolean anonymous = authentication == null
                    || !authentication.isAuthenticated()
                    || authentication instanceof AnonymousAuthenticationToken;
            response.sendError(anonymous ? HttpStatus.UNAUTHORIZED.value() : HttpStatus.FORBIDDEN.value());
        };
    }
}

