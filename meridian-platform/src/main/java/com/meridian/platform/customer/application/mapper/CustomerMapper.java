package com.meridian.platform.customer.application.mapper;

import com.meridian.platform.customer.application.dto.CustomerBankAccountDto;
import com.meridian.platform.customer.application.dto.CustomerDto;
import com.meridian.platform.customer.application.dto.CustomerProfileDto;
import com.meridian.platform.customer.domain.model.Customer;
import com.meridian.platform.customer.domain.model.CustomerBankAccount;
import com.meridian.platform.customer.domain.model.CustomerProfile;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerDto toCustomerDto(Customer customer) {
        return new CustomerDto(
                customer.id(),
                customer.customerNumber(),
                customer.status().name(),
                customer.verificationStatus().name(),
                customer.profileCompletionStatus().name(),
                customer.hasPrimaryActiveBankAccount(),
                toProfileDto(customer.profile())
        );
    }

    public CustomerBankAccountDto toBankAccountDto(CustomerBankAccount account) {
        return new CustomerBankAccountDto(
                account.id(),
                account.bankCode(),
                account.bankNameSnapshot(),
                account.accountHolderName(),
                "****" + account.accountNumber().lastFour(),
                account.accountNumber().lastFour(),
                account.status().name(),
                account.primaryAccount(),
                account.createdAt(),
                account.updatedAt(),
                account.deactivatedAt()
        );
    }

    private CustomerProfileDto toProfileDto(CustomerProfile profile) {
        if (profile == null) {
            return null;
        }
        return new CustomerProfileDto(
                profile.fullName(),
                profile.phoneNumber(),
                profile.residentialAddress(),
                profile.employmentStatus(),
                profile.employerName(),
                profile.termsConsentAccepted(),
                profile.dataProcessingConsentAccepted()
        );
    }
}