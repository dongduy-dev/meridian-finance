package com.meridian.platform.identity.infrastructure.adapter.in.web;

import com.meridian.platform.identity.application.dto.CustomerDigitalAccessDto;
import com.meridian.platform.identity.application.dto.EnableCustomerDigitalAccessRequest;
import com.meridian.platform.identity.application.port.in.ManageCustomerDigitalAccessUseCase;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/customers/{customerId}/digital-access")
public class StaffCustomerDigitalAccessController {
    private final ManageCustomerDigitalAccessUseCase useCase;

    public StaffCustomerDigitalAccessController(ManageCustomerDigitalAccessUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('customer:intake:manage')")
    public CustomerDigitalAccessDto status(@PathVariable UUID customerId) {
        return useCase.status(customerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('customer:intake:manage')")
    public CustomerDigitalAccessDto enable(@PathVariable UUID customerId,
                                           @Valid @RequestBody EnableCustomerDigitalAccessRequest request) {
        return useCase.enable(customerId, request);
    }
}
