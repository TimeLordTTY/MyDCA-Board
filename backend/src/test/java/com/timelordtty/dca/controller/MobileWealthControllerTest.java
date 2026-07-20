package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.mobile.MobilePageResponse;
import com.timelordtty.dca.dto.mobile.MobileTransactionDto;
import com.timelordtty.dca.dto.mobile.MobileWealthOverviewDto;
import com.timelordtty.dca.service.MobileWealthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MobileWealthControllerTest {
    private final MobileWealthService service = mock(MobileWealthService.class);
    private final MobileWealthController controller = new MobileWealthController(service);

    @Test
    void getOverviewDelegatesToReadOnlyService() {
        MobileWealthOverviewDto dto = new MobileWealthOverviewDto();
        dto.setAccountCount(2);
        when(service.getOverview()).thenReturn(dto);

        ResponseEntity<MobileWealthOverviewDto> response = controller.getOverview();

        assertEquals(2, response.getBody().getAccountCount());
        verify(service).getOverview();
    }

    @Test
    void getTransactionsDelegatesPagination() {
        MobilePageResponse<MobileTransactionDto> page = new MobilePageResponse<>();
        page.setPage(2);
        when(service.getTransactions(2, 10)).thenReturn(page);

        ResponseEntity<MobilePageResponse<MobileTransactionDto>> response = controller.getTransactions(2, 10);

        assertEquals(2, response.getBody().getPage());
        verify(service).getTransactions(2, 10);
    }
}
