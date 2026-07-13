package com.meridian.platform.customer.infrastructure.adapter.in.web;

import com.meridian.platform.customer.application.dto.AddCustomerBankAccountRequest;
import com.meridian.platform.customer.application.dto.CustomerBankAccountDto;
import com.meridian.platform.customer.application.port.in.ManageOwnCustomerBankAccountUseCase;
import com.meridian.platform.customer.application.port.in.QueryOwnCustomerBankAccountsUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers/me/bank-accounts")
public class CustomerBankAccountController {

    private final QueryOwnCustomerBankAccountsUseCase queryOwnCustomerBankAccountsUseCase;
    private final ManageOwnCustomerBankAccountUseCase manageOwnCustomerBankAccountUseCase;

    public CustomerBankAccountController(
            QueryOwnCustomerBankAccountsUseCase queryOwnCustomerBankAccountsUseCase,
            ManageOwnCustomerBankAccountUseCase manageOwnCustomerBankAccountUseCase
    ) {
        this.queryOwnCustomerBankAccountsUseCase = queryOwnCustomerBankAccountsUseCase;
        this.manageOwnCustomerBankAccountUseCase = manageOwnCustomerBankAccountUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('customer:bank-account:read:own')")
    public List<CustomerBankAccountDto> getOwnBankAccounts() {
        return queryOwnCustomerBankAccountsUseCase.getOwnBankAccounts();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('customer:bank-account:write:own')")
    public CustomerBankAccountDto addBankAccount(@Valid @RequestBody AddCustomerBankAccountRequest request) {
        return manageOwnCustomerBankAccountUseCase.addBankAccount(request);
    }

    @PostMapping("/{customerBankAccountId}/make-primary")
    @PreAuthorize("hasAuthority('customer:bank-account:write:own')")
    public CustomerBankAccountDto makePrimary(@PathVariable UUID customerBankAccountId) {
        return manageOwnCustomerBankAccountUseCase.makePrimary(customerBankAccountId);
    }

    @PostMapping("/{customerBankAccountId}/deactivate")
    @PreAuthorize("hasAuthority('customer:bank-account:write:own')")
    public CustomerBankAccountDto deactivate(@PathVariable UUID customerBankAccountId) {
        return manageOwnCustomerBankAccountUseCase.deactivate(customerBankAccountId);
    }
}
