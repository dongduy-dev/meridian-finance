package com.meridian.platform.customer.application.port.in;

import com.meridian.platform.customer.application.dto.CustomerBankAccountDto;

import java.util.List;

public interface QueryOwnCustomerBankAccountsUseCase {

    List<CustomerBankAccountDto> getOwnBankAccounts();
}
