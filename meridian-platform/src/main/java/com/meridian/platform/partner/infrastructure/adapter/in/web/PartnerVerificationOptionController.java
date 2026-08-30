package com.meridian.platform.partner.infrastructure.adapter.in.web;

import com.meridian.platform.partner.application.dto.PartnerVerificationOptionDto;
import com.meridian.platform.partner.application.port.in.QueryPartnerVerificationOptionsUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/partner-companies/verification-options")
public class PartnerVerificationOptionController {

    private final QueryPartnerVerificationOptionsUseCase queryOptions;

    public PartnerVerificationOptionController(QueryPartnerVerificationOptionsUseCase queryOptions) {
        this.queryOptions = queryOptions;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('partner:employee:verify:own')")
    public List<PartnerVerificationOptionDto> query() {
        return queryOptions.query();
    }
}
