package com.meridian.platform.partner.infrastructure.adapter.in.web;

import com.meridian.platform.partner.application.dto.PartnerEmployeeImportBatchDto;
import com.meridian.platform.partner.application.dto.ImportPartnerEmployeesRequest;
import com.meridian.platform.partner.application.dto.PartnerEmployeeImportResultDto;
import com.meridian.platform.partner.application.port.in.ImportPartnerEmployeesUseCase;
import com.meridian.platform.partner.application.port.in.QueryPartnerEmployeeImportBatchUseCase;
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
@RequestMapping("/api/v1/partner-companies/{partnerCompanyId}/employee-import-batches")
public class PartnerEmployeeImportBatchController {

    private final QueryPartnerEmployeeImportBatchUseCase queryImportBatchUseCase;
    private final ImportPartnerEmployeesUseCase importPartnerEmployeesUseCase;

    public PartnerEmployeeImportBatchController(
            QueryPartnerEmployeeImportBatchUseCase queryImportBatchUseCase,
            ImportPartnerEmployeesUseCase importPartnerEmployeesUseCase
    ) {
        this.queryImportBatchUseCase = queryImportBatchUseCase;
        this.importPartnerEmployeesUseCase = importPartnerEmployeesUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('partner:read')")
    public List<PartnerEmployeeImportBatchDto> getImportBatchesByPartnerCompanyId(
            @PathVariable UUID partnerCompanyId
    ) {
        return queryImportBatchUseCase.getImportBatchesByPartnerCompanyId(partnerCompanyId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('partner:manage')")
    public PartnerEmployeeImportResultDto importEmployees(
            @PathVariable UUID partnerCompanyId,
            @Valid @RequestBody ImportPartnerEmployeesRequest request
    ) {
        return importPartnerEmployeesUseCase.importEmployees(partnerCompanyId, request);
    }
}
