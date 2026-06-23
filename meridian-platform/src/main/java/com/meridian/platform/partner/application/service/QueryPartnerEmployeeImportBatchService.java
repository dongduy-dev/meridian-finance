package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.dto.PartnerEmployeeImportBatchDto;
import com.meridian.platform.partner.application.mapper.PartnerEmployeeImportBatchMapper;
import com.meridian.platform.partner.application.port.in.QueryPartnerEmployeeImportBatchUseCase;
import com.meridian.platform.partner.application.port.out.PartnerCompanyRepository;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeImportBatchRepository;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class QueryPartnerEmployeeImportBatchService implements QueryPartnerEmployeeImportBatchUseCase {

    private final PartnerCompanyRepository partnerCompanyRepository;
    private final PartnerEmployeeImportBatchRepository importBatchRepository;
    private final PartnerEmployeeImportBatchMapper importBatchMapper;

    public QueryPartnerEmployeeImportBatchService(
            PartnerCompanyRepository partnerCompanyRepository,
            PartnerEmployeeImportBatchRepository importBatchRepository,
            PartnerEmployeeImportBatchMapper importBatchMapper
    ) {
        this.partnerCompanyRepository = partnerCompanyRepository;
        this.importBatchRepository = importBatchRepository;
        this.importBatchMapper = importBatchMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartnerEmployeeImportBatchDto> getImportBatchesByPartnerCompanyId(UUID partnerCompanyId) {
        Objects.requireNonNull(partnerCompanyId, "partnerCompanyId must not be null");

        partnerCompanyRepository.findById(partnerCompanyId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "PARTNER_COMPANY_NOT_FOUND",
                        "Partner company was not found."
                ));

        return importBatchRepository.findByPartnerCompanyId(partnerCompanyId)
                .stream()
                .map(importBatchMapper::toDto)
                .toList();
    }
}
