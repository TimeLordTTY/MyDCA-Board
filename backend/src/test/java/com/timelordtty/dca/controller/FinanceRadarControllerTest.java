package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.service.FamilyService;
import com.timelordtty.dca.service.FinanceRadarService;
import com.timelordtty.dca.service.UserService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinanceRadarControllerTest {
    private final FinanceRadarService radar = mock(FinanceRadarService.class);
    private final UserService users = mock(UserService.class);
    private final FamilyService families = mock(FamilyService.class);
    private final FinanceRadarController controller = new FinanceRadarController(radar, users, families);

    @Test void rejectsInvalidAndUnscopedFamilyRequests() {
        assertEquals(400, controller.getRadar("ALL").getStatusCode().value());
        verifyNoInteractions(users, radar);
        AuthResponse.UserInfo user = new AuthResponse.UserInfo(); user.setId(1L);
        when(users.getCurrentUser()).thenReturn(user);
        assertEquals(400, controller.getRadar("FAMILY").getStatusCode().value());
        verifyNoInteractions(families, radar);
    }

    @Test void personalAndFamilyUseAuthenticatedIdentityAndAdminGate() {
        AuthResponse.UserInfo user = new AuthResponse.UserInfo(); user.setId(1L); user.setFamilyId(7L);
        when(users.getCurrentUser()).thenReturn(user);
        controller.getRadar("PERSONAL");
        verify(radar).getRadar(1L, 7L, "PERSONAL");
        verifyNoInteractions(families);
        controller.getRadar("FAMILY");
        verify(families).assertAdmin(1L, 7L);
        verify(radar).getRadar(1L, 7L, "FAMILY");
    }

    @Test void deniedFamilyAdminDoesNotReadRadar() {
        AuthResponse.UserInfo user = new AuthResponse.UserInfo(); user.setId(1L); user.setFamilyId(7L);
        when(users.getCurrentUser()).thenReturn(user);
        doThrow(new SecurityException("denied")).when(families).assertAdmin(1L, 7L);
        assertThrows(SecurityException.class, () -> controller.getRadar("FAMILY"));
        verifyNoInteractions(radar);
    }
}
