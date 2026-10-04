package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.CustomerCollateralDto;
import com.meridian.platform.loan.application.port.in.QueryOwnCollateralUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CustomerCollateralControllerTest {

    @Test
    void returnsExactlyFiveSafeSubmittedFieldsAndDelegatesOnlyApplicationIdentity() throws Exception {
        UUID id = UUID.randomUUID();
        QueryOwnCollateralUseCase query = mock(QueryOwnCollateralUseCase.class);
        when(query.query(id)).thenReturn(new CustomerCollateralDto("MOTORBIKE", "Submitted motorbike",
                BigDecimal.valueOf(35_000_000), "Customer owned", "Normal used condition"));

        MockMvcBuilders.standaloneSetup(new CustomerCollateralController(query)).build()
                .perform(get("/api/v1/loan-applications/{id}/collateral", id))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"collateralType":"MOTORBIKE","description":"Submitted motorbike",
                         "estimatedValue":35000000,"ownershipStatus":"Customer owned",
                         "conditionNote":"Normal used condition"}
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
        verify(query).query(id);
        verifyNoMoreInteractions(query);
    }
}
