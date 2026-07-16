package com.timelordtty.dca.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

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
}
