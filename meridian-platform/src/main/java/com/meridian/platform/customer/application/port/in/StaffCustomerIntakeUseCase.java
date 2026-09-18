package com.meridian.platform.customer.application.port.in;

import com.meridian.platform.customer.application.dto.AddCustomerBankAccountRequest;
import com.meridian.platform.customer.application.dto.CreateStaffAssistedCustomerRequest;
import com.meridian.platform.customer.application.dto.CustomerBankAccountDto;
import com.meridian.platform.customer.application.dto.CustomerDto;
import com.meridian.platform.customer.application.dto.StaffCustomerSearchRequest;
import com.meridian.platform.customer.application.dto.UpdateCustomerProfileRequest;

import java.util.List;
import java.util.UUID;

public interface StaffCustomerIntakeUseCase {

    CustomerDto search(StaffCustomerSearchRequest request);

    CustomerDto getCustomer(UUID customerId);

    CustomerDto createCustomer(CreateStaffAssistedCustomerRequest request);

    CustomerDto updateProfile(UUID customerId, UpdateCustomerProfileRequest request);

    List<CustomerBankAccountDto> getBankAccounts(UUID customerId);

    CustomerBankAccountDto addBankAccount(UUID customerId, AddCustomerBankAccountRequest request);

    CustomerBankAccountDto makePrimary(UUID customerId, UUID customerBankAccountId);

    CustomerBankAccountDto deactivate(UUID customerId, UUID customerBankAccountId);
}
