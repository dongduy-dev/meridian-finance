package com.meridian.platform.partner.infrastructure.adapter.in.web;

import com.meridian.platform.partner.application.dto.PartnerEmployeeDto;
import com.meridian.platform.partner.application.mapper.PartnerEmployeeMapper;
import com.meridian.platform.partner.domain.port.in.QueryPartnerEmployeeUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
            @PathVariable UUID partnerCompanyId
    ) {
        return queryPartnerEmployeeUseCase.getPartnerEmployeesByCompanyId(partnerCompanyId)
                .stream()
                .map(PartnerEmployeeMapper::toDto)
                .toList();
    }
}