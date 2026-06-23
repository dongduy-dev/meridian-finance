package com.meridian.platform.partner.infrastructure.adapter.in.web;

import com.meridian.platform.partner.application.dto.PartnerEmployeeDto;
import com.meridian.platform.partner.application.port.in.QueryPartnerEmployeeUseCase;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/partner-companies/{partnerCompanyId}/employees")
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
