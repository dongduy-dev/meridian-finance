package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.dto.PartnerEmployeeImportBatchDto;
import com.meridian.platform.partner.application.dto.PartnerEmployeeImportBatchDetailDto;
import com.meridian.platform.partner.application.dto.PartnerEmployeeImportResultDto;
import com.meridian.platform.partner.application.mapper.PartnerEmployeeMapper;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeRepository;
import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatchStatus;
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
    private final PartnerEmployeeRepository employeeRepository;
    private final PartnerEmployeeMapper employeeMapper;

    public QueryPartnerEmployeeImportBatchService(
            PartnerCompanyRepository partnerCompanyRepository,
            PartnerEmployeeImportBatchRepository importBatchRepository,
            PartnerEmployeeImportBatchMapper importBatchMapper,
            PartnerEmployeeRepository employeeRepository,
            PartnerEmployeeMapper employeeMapper
    ) {
        this.partnerCompanyRepository = partnerCompanyRepository;
        this.importBatchRepository = importBatchRepository;
        this.importBatchMapper = importBatchMapper;
        this.employeeRepository = employeeRepository;
        this.employeeMapper = employeeMapper;
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

    @Override
    @Transactional(readOnly = true)
    public PartnerEmployeeImportBatchDetailDto getImportBatch(UUID partnerCompanyId, UUID batchId) {
        Objects.requireNonNull(partnerCompanyId, "partnerCompanyId must not be null");
        Objects.requireNonNull(batchId, "batchId must not be null");
        partnerCompanyRepository.findById(partnerCompanyId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "PARTNER_COMPANY_NOT_FOUND", "Partner company was not found."
                ));
        var batch = importBatchRepository.findByPartnerCompanyIdAndId(partnerCompanyId, batchId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "PARTNER_EMPLOYEE_IMPORT_BATCH_NOT_FOUND", "Partner employee import batch was not found."
                ));
        var employees = batch.status() == PartnerEmployeeImportBatchStatus.COMPLETED
                ? employeeRepository.findByPartnerCompanyIdAndImportBatchId(partnerCompanyId, batchId)
                        .stream().map(employeeMapper::toDto).toList()
                : List.<com.meridian.platform.partner.application.dto.PartnerEmployeeDto>of();
        return new PartnerEmployeeImportBatchDetailDto(
                batch.id(), batch.partnerCompanyId(), batch.effectiveMonth(), batch.status().name(),
                batch.validRowCount(), batch.invalidRowCount(), employees,
                batch.rejections().stream().map(rejection -> new PartnerEmployeeImportResultDto.RowRejection(
                        rejection.rowIndex(), rejection.errorCode(), rejection.reason()
                )).toList()
        );
    }
}
