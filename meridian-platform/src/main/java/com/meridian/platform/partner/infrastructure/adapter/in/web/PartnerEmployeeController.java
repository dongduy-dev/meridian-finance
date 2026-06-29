package com.meridian.platform.partner.infrastructure.adapter.in.web;

import com.meridian.platform.partner.application.dto.PartnerEmployeeDto;
import com.meridian.platform.partner.application.port.in.QueryPartnerEmployeeUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/partner-companies/{partnerCompanyId}/employees")
@PreAuthorize("hasAuthority('partner:read')")
public class PartnerEmployeeController {

    private final QueryPartnerEmployeeUseCase queryPartnerEmployeeUseCase;

    public PartnerEmployeeController(QueryPartnerEmployeeUseCase queryPartnerEmployeeUseCase) {
        this.queryPartnerEmployeeUseCase = queryPartnerEmployeeUseCase;
    }

    @GetMapping
    public List<PartnerEmployeeDto> getPartnerEmployeesByCompanyId(
            @PathVariable UUID partnerCompanyId,
            @RequestParam(defaultValue = "false") boolean activeOnly
    ) {
        return queryPartnerEmployeeUseCase.getPartnerEmployeesByCompanyId(partnerCompanyId, activeOnly);
    }
}
