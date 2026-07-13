package com.meridian.platform.customer.infrastructure.adapter.in.web;

import com.meridian.platform.customer.application.dto.CustomerDto;
import com.meridian.platform.customer.application.dto.UpdateCustomerProfileRequest;
import com.meridian.platform.customer.application.port.in.QueryOwnCustomerUseCase;
import com.meridian.platform.customer.application.port.in.UpdateOwnCustomerProfileUseCase;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerProfileControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new CustomerProfileController(new StubQueryUseCase(), new DuplicateIdentityUseCase()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void duplicateIdentityReferenceReturnsConflictWithoutSensitiveValue() throws Exception {
        mockMvc.perform(put("/api/v1/customers/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Customer Demo",
                                  "identityReference": "IDREF-MER-001",
                                  "phoneNumber": "0901234567",
                                  "residentialAddress": "1 Meridian Street",
                                  "employmentStatus": "SALARIED",
                                  "employerName": "Meridian Partner Co",
                                  "termsConsentAccepted": true,
                                  "dataProcessingConsentAccepted": true
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDENTITY_REFERENCE_ALREADY_IN_USE"))
                .andExpect(jsonPath("$.message").value("Identity reference is already associated with another customer."))
                .andExpect(jsonPath("$.message", not(containsString("IDREF-MER-001"))));
    }

    private static class StubQueryUseCase implements QueryOwnCustomerUseCase {

        @Override
        public CustomerDto getOwnCustomer() {
            throw new UnsupportedOperationException("not used");
        }
    }

    private static class DuplicateIdentityUseCase implements UpdateOwnCustomerProfileUseCase {

        @Override
        public CustomerDto updateOwnProfile(UpdateCustomerProfileRequest request) {
            throw new BusinessStateConflictException(
                    "IDENTITY_REFERENCE_ALREADY_IN_USE",
                    "Identity reference is already associated with another customer."
            );
        }
    }
}