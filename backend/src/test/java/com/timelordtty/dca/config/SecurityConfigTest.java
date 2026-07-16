package com.timelordtty.dca.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityConfigTest {

    @Test
    void unauthorizedEntryPointReturns401ForMissingOrInvalidSession() throws Exception {
        SecurityConfig config = new SecurityConfig(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        config.unauthorizedEntryPoint().commence(
                new MockHttpServletRequest(),
                response,
                new AuthenticationCredentialsNotFoundException("test")
        );

        assertEquals(401, response.getStatus());
    }

    @Test
    void accessDeniedHandlerReturns401ForAnonymousRequest() throws Exception {
        SecurityConfig config = new SecurityConfig(null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();

        config.accessDeniedHandler().handle(
                new MockHttpServletRequest(),
                response,
                new org.springframework.security.access.AccessDeniedException("test")
        );

        assertEquals(401, response.getStatus());
    }

    @Test
    void accessDeniedHandlerKeeps403ForAuthenticatedUser() throws Exception {
        SecurityConfig config = new SecurityConfig(null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("user", "credentials", "ROLE_USER")
        );

        try {
            config.accessDeniedHandler().handle(
                    new MockHttpServletRequest(),
                    response,
                    new org.springframework.security.access.AccessDeniedException("test")
            );
            assertEquals(403, response.getStatus());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
