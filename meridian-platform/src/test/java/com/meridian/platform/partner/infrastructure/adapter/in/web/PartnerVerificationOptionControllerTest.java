package com.meridian.platform.partner.infrastructure.adapter.in.web;

import com.meridian.platform.partner.application.dto.PartnerVerificationOptionDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PartnerVerificationOptionControllerTest {

    @Test
    void exposesOnlyCustomerSafeSelectorFields() throws Exception {
        UUID id = UUID.randomUUID();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PartnerVerificationOptionController(
                () -> List.of(new PartnerVerificationOptionDto(id, "MERIDIAN-DEMO", "Meridian Demo"))
        )).build();

        mvc.perform(get("/api/v1/partner-companies/verification-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].partnerCompanyId").value(id.toString()))
                .andExpect(jsonPath("$[0].companyCode").value("MERIDIAN-DEMO"))
                .andExpect(jsonPath("$[0].name").value("Meridian Demo"))
                .andExpect(jsonPath("$[0].salaryAdvancePolicyLimit").doesNotExist())
                .andExpect(jsonPath("$[0].employees").doesNotExist());
    }
}
