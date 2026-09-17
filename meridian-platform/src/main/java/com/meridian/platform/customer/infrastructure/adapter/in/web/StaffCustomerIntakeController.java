package com.meridian.platform.customer.infrastructure.adapter.in.web;

import com.meridian.platform.customer.application.dto.AddCustomerBankAccountRequest;
import com.meridian.platform.customer.application.dto.CreateStaffAssistedCustomerRequest;
import com.meridian.platform.customer.application.dto.CustomerBankAccountDto;
import com.meridian.platform.customer.application.dto.CustomerDto;
import com.meridian.platform.customer.application.dto.StaffCustomerSearchRequest;
import com.meridian.platform.customer.application.dto.UpdateCustomerProfileRequest;
import com.meridian.platform.customer.application.port.in.StaffCustomerIntakeUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/customers")
public class StaffCustomerIntakeController {

    private final StaffCustomerIntakeUseCase useCase;

    public StaffCustomerIntakeController(StaffCustomerIntakeUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/search")
    @PreAuthorize("hasAuthority('customer:read')")
    public CustomerDto search(@Valid @RequestBody StaffCustomerSearchRequest request) {
        return useCase.search(request);
    }

    @GetMapping("/{customerId}")
    @PreAuthorize("hasAuthority('customer:read')")
    public CustomerDto get(@PathVariable UUID customerId) {
        return useCase.getCustomer(customerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('customer:intake:manage')")
    public CustomerDto create(@Valid @RequestBody CreateStaffAssistedCustomerRequest request) {
        return useCase.createCustomer(request);
    }

    @PutMapping("/{customerId}/profile")
    @PreAuthorize("hasAuthority('customer:intake:manage')")
    public CustomerDto updateProfile(
            @PathVariable UUID customerId,
            @Valid @RequestBody UpdateCustomerProfileRequest request
    ) {
        return useCase.updateProfile(customerId, request);
    }

    @GetMapping("/{customerId}/bank-accounts")
    @PreAuthorize("hasAuthority('customer:intake:manage')")
    public List<CustomerBankAccountDto> getBankAccounts(@PathVariable UUID customerId) {
        return useCase.getBankAccounts(customerId);
    }

    @PostMapping("/{customerId}/bank-accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('customer:intake:manage')")
    public CustomerBankAccountDto addBankAccount(
            @PathVariable UUID customerId,
            @Valid @RequestBody AddCustomerBankAccountRequest request
    ) {
        return useCase.addBankAccount(customerId, request);
    }

    @PostMapping("/{customerId}/bank-accounts/{bankAccountId}/make-primary")
    @PreAuthorize("hasAuthority('customer:intake:manage')")
    public CustomerBankAccountDto makePrimary(
            @PathVariable UUID customerId,
            @PathVariable UUID bankAccountId
    ) {
        return useCase.makePrimary(customerId, bankAccountId);
    }

    @PostMapping("/{customerId}/bank-accounts/{bankAccountId}/deactivate")
    @PreAuthorize("hasAuthority('customer:intake:manage')")
    public CustomerBankAccountDto deactivate(
            @PathVariable UUID customerId,
            @PathVariable UUID bankAccountId
    ) {
        return useCase.deactivate(customerId, bankAccountId);
    }
}
