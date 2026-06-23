package com.meridian.platform.partner.application.mapper;

import com.meridian.platform.partner.application.dto.PartnerEmployeeImportBatchDto;
import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatch;
import org.springframework.stereotype.Component;

@Component
public class PartnerEmployeeImportBatchMapper {

    public PartnerEmployeeImportBatchDto toDto(PartnerEmployeeImportBatch importBatch) {
        return new PartnerEmployeeImportBatchDto(
                importBatch.id(),
                importBatch.partnerCompanyId(),
                importBatch.effectiveMonth(),
                importBatch.status().name(),
                importBatch.validRowCount(),
                importBatch.invalidRowCount()
        );
    }
}