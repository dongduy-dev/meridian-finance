package com.meridian.platform.customer.application.port.in;

import com.meridian.platform.customer.application.dto.AddCustomerBankAccountRequest;
import com.meridian.platform.customer.application.dto.CustomerBankAccountDto;

import java.util.UUID;

public interface ManageOwnCustomerBankAccountUseCase {

    CustomerBankAccountDto addBankAccount(AddCustomerBankAccountRequest request);

    CustomerBankAccountDto makePrimary(UUID customerBankAccountId);

    CustomerBankAccountDto deactivate(UUID customerBankAccountId);
}
