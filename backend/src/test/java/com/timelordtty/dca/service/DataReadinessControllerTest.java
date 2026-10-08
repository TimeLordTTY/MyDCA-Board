package com.timelordtty.dca.service;

import com.timelordtty.dca.controller.DataReadinessController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DataReadinessControllerTest {
    @Test void sanitizesAllErrorResponsesAndOnlyExposesGet() throws Exception {
        var service=mock(DataReadinessService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new DataReadinessController(service)).build();
        when(service.diagnose("FAMILY","2026-10")).thenThrow(new AccessDeniedException("password secret"));
        mvc.perform(get("/api/v2/data-readiness").param("scope","FAMILY").param("month","2026-10"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("无权访问该诊断作用域"));
        when(service.diagnose("PERSONAL","2026-10")).thenThrow(new IllegalStateException("jdbc secret"));
        mvc.perform(get("/api/v2/data-readiness").param("month","2026-10"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("诊断暂不可用，请稍后重试"));
        mvc.perform(post("/api/v2/data-readiness").param("month","2026-10"))
                .andExpect(status().isMethodNotAllowed());
    }
}
