package com.meridian.platform.customer.infrastructure.adapter.in.web;

import com.meridian.platform.customer.application.dto.CustomerDto;
import com.meridian.platform.customer.application.dto.UpdateCustomerProfileRequest;
import com.meridian.platform.customer.application.port.in.QueryOwnCustomerUseCase;
import com.meridian.platform.customer.application.port.in.UpdateOwnCustomerProfileUseCase;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers/me")
public class CustomerProfileController {

    private final QueryOwnCustomerUseCase queryOwnCustomerUseCase;
    private final UpdateOwnCustomerProfileUseCase updateOwnCustomerProfileUseCase;

    public CustomerProfileController(
            QueryOwnCustomerUseCase queryOwnCustomerUseCase,
            UpdateOwnCustomerProfileUseCase updateOwnCustomerProfileUseCase
    ) {
        this.queryOwnCustomerUseCase = queryOwnCustomerUseCase;
        this.updateOwnCustomerProfileUseCase = updateOwnCustomerProfileUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('customer:profile:read:own')")
    public CustomerDto getOwnCustomer() {
        return queryOwnCustomerUseCase.getOwnCustomer();
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAuthority('customer:profile:write:own')")
    public CustomerDto updateOwnProfile(@Valid @RequestBody UpdateCustomerProfileRequest request) {
        return updateOwnCustomerProfileUseCase.updateOwnProfile(request);
    }
}
