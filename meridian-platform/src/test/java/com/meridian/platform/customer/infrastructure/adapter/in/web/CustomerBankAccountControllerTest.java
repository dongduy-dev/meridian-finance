package com.meridian.platform.customer.infrastructure.adapter.in.web;

import com.meridian.platform.customer.application.dto.AddCustomerBankAccountRequest;
import com.meridian.platform.customer.application.dto.CustomerBankAccountDto;
import com.meridian.platform.customer.application.port.in.ManageOwnCustomerBankAccountUseCase;
import com.meridian.platform.customer.application.port.in.QueryOwnCustomerBankAccountsUseCase;
import com.meridian.platform.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerBankAccountControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new CustomerBankAccountController(new StubQueryUseCase(), new ValidatingManageUseCase()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shortNormalizedAccountNumberReturnsValidationFailure() throws Exception {
        mockMvc.perform(post("/api/v1/customers/me/bank-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bankCode": "VCB",
                                  "bankNameSnapshot": "Vietcombank",
                                  "accountHolderName": "Customer Demo",
                                  "accountNumber": "12-34"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    private static class StubQueryUseCase implements QueryOwnCustomerBankAccountsUseCase {

        @Override
        public List<CustomerBankAccountDto> getOwnBankAccounts() {
            return List.of();
        }
    }

    private static class ValidatingManageUseCase implements ManageOwnCustomerBankAccountUseCase {

        @Override
        public CustomerBankAccountDto addBankAccount(AddCustomerBankAccountRequest request) {
            String normalized = request.accountNumber().trim().replaceAll("[\\s-]+", "");
            if (normalized.length() < 6) {
                throw new IllegalArgumentException("accountNumber must contain at least 6 characters after normalization");
            }
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public CustomerBankAccountDto makePrimary(UUID customerBankAccountId) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public CustomerBankAccountDto deactivate(UUID customerBankAccountId) {
            throw new UnsupportedOperationException("not used");
        }
    }
}